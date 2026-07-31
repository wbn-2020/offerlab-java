package com.offerlab.community.notification.realtime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationSessionRegistry {

    private final Map<Long, ConcurrentHashMap<String, WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, Long> userBySession = new ConcurrentHashMap<>();
    private final int sendTimeLimitMillis;
    private final int outboundBufferBytes;

    public NotificationSessionRegistry() {
        this(10_000, NotificationPacketCodec.MAX_FRAME_BYTES * 4);
    }

    @Autowired
    public NotificationSessionRegistry(
            @Value("${offerlab.realtime.send-time-limit-millis:10000}") int sendTimeLimitMillis,
            @Value("${offerlab.realtime.outbound-buffer-bytes:65536}") int outboundBufferBytes) {
        this.sendTimeLimitMillis = Math.max(1_000, sendTimeLimitMillis);
        this.outboundBufferBytes = Math.max(NotificationPacketCodec.MAX_FRAME_BYTES, outboundBufferBytes);
    }

    public synchronized boolean register(Long uid, WebSocketSession session, int maxSessionsPerUser) {
        if (uid == null || uid <= 0 || session == null || !session.isOpen() || maxSessionsPerUser < 1) {
            return false;
        }

        String sessionId = session.getId();
        Long registeredUid = userBySession.get(sessionId);
        if (registeredUid != null) {
            return registeredUid.equals(uid);
        }

        ConcurrentHashMap<String, WebSocketSession> sessions = sessionsByUser.computeIfAbsent(
                uid, ignored -> new ConcurrentHashMap<>());
        removeClosedSessions(uid, sessions);
        if (sessions.size() >= maxSessionsPerUser) {
            return false;
        }

        sessions.put(sessionId, decorate(session));
        userBySession.put(sessionId, uid);
        return true;
    }

    public synchronized void unregister(WebSocketSession session) {
        if (session == null) {
            return;
        }
        String sessionId = session.getId();
        Long uid = userBySession.remove(sessionId);
        if (uid == null) {
            return;
        }
        ConcurrentHashMap<String, WebSocketSession> sessions = sessionsByUser.get(uid);
        if (sessions == null) {
            return;
        }
        sessions.remove(sessionId);
        if (sessions.isEmpty()) {
            sessionsByUser.remove(uid, sessions);
        }
    }

    public synchronized List<WebSocketSession> sessionsFor(Long uid) {
        if (uid == null || uid <= 0) {
            return List.of();
        }
        ConcurrentHashMap<String, WebSocketSession> sessions = sessionsByUser.get(uid);
        if (sessions == null) {
            return List.of();
        }
        removeClosedSessions(uid, sessions);
        if (sessions.isEmpty()) {
            sessionsByUser.remove(uid, sessions);
            return List.of();
        }
        return List.copyOf(new ArrayList<>(sessions.values()));
    }

    public synchronized int sessionCount(Long uid) {
        return sessionsFor(uid).size();
    }

    public synchronized WebSocketSession managedSession(WebSocketSession session) {
        if (session == null) {
            return null;
        }
        Long uid = userBySession.get(session.getId());
        if (uid == null) {
            return session;
        }
        ConcurrentHashMap<String, WebSocketSession> sessions = sessionsByUser.get(uid);
        if (sessions == null) {
            return session;
        }
        return sessions.getOrDefault(session.getId(), session);
    }

    public synchronized List<WebSocketSession> drainSessions() {
        List<WebSocketSession> sessions = sessionsByUser.values().stream()
                .flatMap(bySession -> bySession.values().stream())
                .toList();
        sessionsByUser.clear();
        userBySession.clear();
        return sessions;
    }

    private WebSocketSession decorate(WebSocketSession session) {
        if (session instanceof ConcurrentWebSocketSessionDecorator) {
            return session;
        }
        return new ConcurrentWebSocketSessionDecorator(session, sendTimeLimitMillis, outboundBufferBytes);
    }

    private void removeClosedSessions(Long uid, ConcurrentHashMap<String, WebSocketSession> sessions) {
        sessions.entrySet().removeIf(entry -> {
            WebSocketSession session = entry.getValue();
            boolean closed = session == null || !session.isOpen();
            if (closed) {
                userBySession.remove(entry.getKey(), uid);
            }
            return closed;
        });
    }
}
