package com.offerlab.community.notification.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationWebSocketHandlerTest {

    private final NotificationPacketCodec codec = new NotificationPacketCodec(new ObjectMapper());
    private NotificationWebSocketHandler handler;

    @AfterEach
    void stopTimeoutExecutor() {
        if (handler != null) {
            handler.shutdown();
        }
    }

    @Test
    void authenticatesOnlyWithFirstBinaryAuthRequestAndRegistersSession() {
        NotificationSessionRegistry registry = new NotificationSessionRegistry();
        handler = handler(registry);
        TestWebSocketSession session = new TestWebSocketSession("session-1");

        handler.afterConnectionEstablished(session.session());
        handler.handleBinaryMessage(session.session(), codec.encode(NotificationPacketCommand.AUTH_REQ, Map.of("token", "valid")));

        assertEquals(NotificationPacketCodec.MAX_FRAME_BYTES, session.binaryMessageSizeLimit());
        assertEquals(1, registry.sessionCount(42L));
        assertEquals(1, session.sentMessages().size());
        WebSocketMessage<?> response = session.sentMessages().get(0);
        assertTrue(response instanceof BinaryMessage);
        NotificationPacket packet = codec.decode((BinaryMessage) response);
        assertEquals(NotificationPacketCommand.AUTH_RESP, packet.command());
        assertTrue(packet.body().path("authenticated").booleanValue());

        handler.handleBinaryMessage(session.session(), codec.encode(NotificationPacketCommand.PING, Map.of()));

        assertEquals(2, session.sentMessages().size());
        assertEquals(NotificationPacketCommand.PONG,
                codec.decode((BinaryMessage) session.sentMessages().get(1)).command());
    }

    @Test
    void rejectsProtocolViolationsAndInvalidTokensWithoutRegistration() {
        NotificationSessionRegistry registry = new NotificationSessionRegistry();
        handler = handler(registry);
        TestWebSocketSession protocolViolation = new TestWebSocketSession("session-2");
        handler.afterConnectionEstablished(protocolViolation.session());

        handler.handleBinaryMessage(protocolViolation.session(), codec.encode(NotificationPacketCommand.PING, Map.of()));

        assertCloseCode(protocolViolation.closeStatus(), 4002);
        assertEquals(0, registry.sessionCount(42L));

        TestWebSocketSession invalidToken = new TestWebSocketSession("session-3");
        handler.afterConnectionEstablished(invalidToken.session());
        handler.handleBinaryMessage(invalidToken.session(),
                codec.encode(NotificationPacketCommand.AUTH_REQ, Map.of("token", "invalid")));

        assertCloseCode(invalidToken.closeStatus(), 4003);
        assertEquals(0, registry.sessionCount(42L));
        assertEquals(NotificationPacketCommand.AUTH_RESP,
                codec.decode((BinaryMessage) invalidToken.sentMessages().get(0)).command());
    }

    @Test
    void rejectsRepeatedAuthenticationAndSessionLimit() {
        NotificationSessionRegistry registry = new NotificationSessionRegistry();
        handler = handler(registry);
        TestWebSocketSession first = new TestWebSocketSession("session-4");
        TestWebSocketSession second = new TestWebSocketSession("session-5");

        handler.afterConnectionEstablished(first.session());
        handler.handleBinaryMessage(first.session(), codec.encode(NotificationPacketCommand.AUTH_REQ, Map.of("token", "valid")));
        handler.afterConnectionEstablished(second.session());
        handler.handleBinaryMessage(second.session(), codec.encode(NotificationPacketCommand.AUTH_REQ, Map.of("token", "valid")));

        assertNotNull(second.closeStatus());
        assertCloseCode(second.closeStatus(), 4004);

        handler.handleBinaryMessage(first.session(), codec.encode(NotificationPacketCommand.AUTH_REQ, Map.of("token", "valid")));

        assertCloseCode(first.closeStatus(), 4002);
    }

    @Test
    void closesRegisteredSessionsWhenTheApplicationShutsDown() {
        NotificationSessionRegistry registry = new NotificationSessionRegistry();
        handler = handler(registry);
        TestWebSocketSession session = new TestWebSocketSession("session-6");

        handler.afterConnectionEstablished(session.session());
        handler.handleBinaryMessage(session.session(), codec.encode(NotificationPacketCommand.AUTH_REQ, Map.of("token", "valid")));
        handler.shutdown();

        assertCloseCode(session.closeStatus(), CloseStatus.GOING_AWAY.getCode());
        assertEquals(0, registry.sessionCount(42L));
    }

    private NotificationWebSocketHandler handler(NotificationSessionRegistry registry) {
        JwtService jwtService = new JwtService(null) {
            @Override
            public Long parseUid(String token) {
                return "valid".equals(token) ? 42L : null;
            }
        };
        return new NotificationWebSocketHandler(jwtService, codec, registry, 10, 30, 1);
    }

    private void assertCloseCode(CloseStatus closeStatus, int expectedCode) {
        assertNotNull(closeStatus);
        assertEquals(expectedCode, closeStatus.getCode());
    }
}
