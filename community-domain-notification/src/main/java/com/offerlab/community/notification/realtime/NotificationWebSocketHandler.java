package com.offerlab.community.notification.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.offerlab.community.infra.security.JwtService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Component
public class NotificationWebSocketHandler extends BinaryWebSocketHandler {

    static final CloseStatus AUTH_TIMEOUT_CLOSE_STATUS = new CloseStatus(4001, "Authentication timeout");
    static final CloseStatus INVALID_PACKET_CLOSE_STATUS = new CloseStatus(4002, "Invalid packet");
    static final CloseStatus AUTH_REJECTED_CLOSE_STATUS = new CloseStatus(4003, "Authentication rejected");
    static final CloseStatus SESSION_LIMIT_CLOSE_STATUS = new CloseStatus(4004, "Session limit reached");

    private static final int MAX_TOKEN_LENGTH = 4096;

    private final JwtService jwtService;
    private final NotificationPacketCodec packetCodec;
    private final NotificationSessionRegistry sessionRegistry;
    private final int authTimeoutSeconds;
    private final int heartbeatSeconds;
    private final int maxSessionsPerUser;
    private final ScheduledExecutorService authTimeoutExecutor;
    private final Map<String, ConnectionState> connectionStates = new ConcurrentHashMap<>();

    public NotificationWebSocketHandler(JwtService jwtService,
                                        NotificationPacketCodec packetCodec,
                                        NotificationSessionRegistry sessionRegistry,
                                        @Value("${offerlab.realtime.auth-timeout-seconds:10}") int authTimeoutSeconds,
                                        @Value("${offerlab.realtime.heartbeat-seconds:30}") int heartbeatSeconds,
                                        @Value("${offerlab.realtime.max-sessions-per-user:5}") int maxSessionsPerUser) {
        this.jwtService = jwtService;
        this.packetCodec = packetCodec;
        this.sessionRegistry = sessionRegistry;
        this.authTimeoutSeconds = Math.max(1, authTimeoutSeconds);
        this.heartbeatSeconds = Math.max(1, heartbeatSeconds);
        this.maxSessionsPerUser = Math.max(1, maxSessionsPerUser);
        this.authTimeoutExecutor = Executors.newSingleThreadScheduledExecutor(new AuthTimeoutThreadFactory());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.setBinaryMessageSizeLimit(NotificationPacketCodec.MAX_FRAME_BYTES);
        ConnectionState state = new ConnectionState();
        ConnectionState existing = connectionStates.putIfAbsent(session.getId(), state);
        if (existing != null) {
            closeAndCleanup(session, INVALID_PACKET_CLOSE_STATUS);
            return;
        }
        state.authTimeout = authTimeoutExecutor.schedule(
                () -> closeIfUnauthenticated(session, state),
                authTimeoutSeconds,
                TimeUnit.SECONDS);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        closeAndCleanup(session, INVALID_PACKET_CLOSE_STATUS);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ConnectionState state = connectionStates.get(session.getId());
        if (state == null) {
            closeAndCleanup(session, INVALID_PACKET_CLOSE_STATUS);
            return;
        }

        NotificationPacket packet;
        try {
            packet = packetCodec.decode(message);
        } catch (NotificationProtocolException e) {
            closeAndCleanup(session, INVALID_PACKET_CLOSE_STATUS);
            return;
        }

        synchronized (state) {
            if (!state.authenticated) {
                handleAuthentication(session, state, packet);
                return;
            }
            handleAuthenticatedPacket(session, state, packet);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        cleanup(session);
        if (session.isOpen()) {
            close(session, CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        cleanup(session);
    }

    @PreDestroy
    void shutdown() {
        authTimeoutExecutor.shutdownNow();
        connectionStates.values().forEach(state -> {
            cancelAuthTimeout(state);
            state.token = null;
            state.uid = null;
        });
        connectionStates.clear();
        sessionRegistry.drainSessions().forEach(session -> close(session, CloseStatus.GOING_AWAY));
    }

    private void handleAuthentication(WebSocketSession session, ConnectionState state, NotificationPacket packet) {
        if (packet.command() != NotificationPacketCommand.AUTH_REQ || !packet.body().isObject()) {
            closeAndCleanup(session, INVALID_PACKET_CLOSE_STATUS);
            return;
        }

        JsonNode tokenNode = packet.body().get("token");
        if (tokenNode == null || !tokenNode.isTextual()
                || tokenNode.textValue().isBlank() || tokenNode.textValue().length() > MAX_TOKEN_LENGTH) {
            rejectAuthentication(session);
            return;
        }

        String token = tokenNode.textValue();
        Long uid = jwtService.parseUid(token);
        if (uid == null || uid <= 0) {
            rejectAuthentication(session);
            return;
        }
        if (!sessionRegistry.register(uid, session, maxSessionsPerUser)) {
            closeAndCleanup(session, SESSION_LIMIT_CLOSE_STATUS);
            return;
        }

        state.authenticated = true;
        state.uid = uid;
        state.token = token;
        cancelAuthTimeout(state);
        try {
            send(session, NotificationPacketCommand.AUTH_RESP, Map.of(
                    "authenticated", true,
                    "serverTime", Instant.now().toEpochMilli(),
                    "heartbeatSeconds", heartbeatSeconds));
        } catch (IOException e) {
            closeAndCleanup(session, CloseStatus.SERVER_ERROR);
        }
    }

    private void handleAuthenticatedPacket(WebSocketSession session,
                                           ConnectionState state,
                                           NotificationPacket packet) {
        if (packet.command() != NotificationPacketCommand.PING || !packet.body().isObject()) {
            closeAndCleanup(session, INVALID_PACKET_CLOSE_STATUS);
            return;
        }
        Long validatedUid = jwtService.parseUid(state.token);
        if (!Objects.equals(state.uid, validatedUid)) {
            rejectAuthentication(session);
            return;
        }
        try {
            send(session, NotificationPacketCommand.PONG, Map.of("serverTime", Instant.now().toEpochMilli()));
        } catch (IOException e) {
            closeAndCleanup(session, CloseStatus.SERVER_ERROR);
        }
    }

    private void rejectAuthentication(WebSocketSession session) {
        try {
            send(session, NotificationPacketCommand.AUTH_RESP, Map.of(
                    "authenticated", false,
                    "serverTime", Instant.now().toEpochMilli(),
                    "heartbeatSeconds", heartbeatSeconds));
        } catch (IOException ignored) {
            // The close status remains the authoritative authentication result.
        }
        closeAndCleanup(session, AUTH_REJECTED_CLOSE_STATUS);
    }

    private void closeIfUnauthenticated(WebSocketSession session, ConnectionState state) {
        synchronized (state) {
            if (!state.authenticated && connectionStates.get(session.getId()) == state) {
                closeAndCleanup(session, AUTH_TIMEOUT_CLOSE_STATUS);
            }
        }
    }

    private void send(WebSocketSession session, NotificationPacketCommand command, Object body) throws IOException {
        WebSocketSession outboundSession = sessionRegistry.managedSession(session);
        if (outboundSession == null || !outboundSession.isOpen()) {
            throw new IOException("websocket session is closed");
        }
        outboundSession.sendMessage(packetCodec.encode(command, body));
    }

    private void closeAndCleanup(WebSocketSession session, CloseStatus closeStatus) {
        cleanup(session);
        if (session.isOpen()) {
            close(session, closeStatus);
        }
    }

    private void cleanup(WebSocketSession session) {
        ConnectionState state = connectionStates.remove(session.getId());
        if (state != null) {
            cancelAuthTimeout(state);
            state.token = null;
            state.uid = null;
        }
        sessionRegistry.unregister(session);
    }

    private void close(WebSocketSession session, CloseStatus closeStatus) {
        try {
            session.close(closeStatus);
        } catch (IOException ignored) {
            // Transport errors are handled by normal WebSocket close cleanup.
        }
    }

    private void cancelAuthTimeout(ConnectionState state) {
        ScheduledFuture<?> timeout = state.authTimeout;
        if (timeout != null) {
            timeout.cancel(false);
            state.authTimeout = null;
        }
    }

    private static final class ConnectionState {
        private boolean authenticated;
        private Long uid;
        private String token;
        private ScheduledFuture<?> authTimeout;
    }

    private static final class AuthTimeoutThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "notification-ws-auth-timeout");
            thread.setDaemon(true);
            return thread;
        }
    }
}
