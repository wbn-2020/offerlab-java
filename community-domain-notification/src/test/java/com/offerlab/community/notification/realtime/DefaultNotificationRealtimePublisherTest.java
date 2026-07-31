package com.offerlab.community.notification.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.notification.infrastructure.persistence.mapper.NotificationMessageMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationMessagePO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.socket.BinaryMessage;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultNotificationRealtimePublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationPacketCodec packetCodec = new NotificationPacketCodec(objectMapper);

    @Test
    void deliversNotificationAndCanonicalUnreadCountToLocalSessions() {
        TestWebSocketSession session = new TestWebSocketSession("local-user");
        NotificationSessionRegistry registry = new NotificationSessionRegistry();
        registry.register(42L, session.session(), 5);
        DefaultNotificationRealtimePublisher publisher = publisher(registry, false, "node-a");

        publisher.publishNotification(42L, 101L, Map.of("total", 1L, "like", 1L));

        assertEquals(2, session.sentMessages().size());
        NotificationPacket notification = packetCodec.decode((BinaryMessage) session.sentMessages().get(0));
        NotificationPacket unread = packetCodec.decode((BinaryMessage) session.sentMessages().get(1));
        assertEquals(NotificationPacketCommand.NOTIF_PUSH, notification.command());
        assertEquals(101L, notification.body().path("id").asLong());
        assertEquals("like", notification.body().path("content").path("action").asText());
        assertFalse(notification.body().path("content").has("internalNote"));
        assertEquals(NotificationPacketCommand.UNREAD_COUNT, unread.command());
        assertEquals(1L, unread.body().path("total").asLong());
    }

    @Test
    void ignoresOwnRedisEventAndKeepsRemoteRedisFailureOutOfTheWritePath() {
        TestWebSocketSession session = new TestWebSocketSession("remote-user");
        NotificationSessionRegistry registry = new NotificationSessionRegistry();
        registry.register(42L, session.session(), 5);
        DefaultNotificationRealtimePublisher publisher = publisher(registry, true, "node-a");

        publisher.receiveRemote(new NotificationRealtimeEvent("node-a", 42L, 101L, Map.of("total", 1L)));
        assertTrue(session.sentMessages().isEmpty(), "the source instance must not receive its own fan-out event");

        publisher.publishUnreadCount(42L, Map.of("total", 0L));
        assertEquals(1, session.sentMessages().size(), "Redis publish failures must not prevent local delivery");
        assertEquals(NotificationPacketCommand.UNREAD_COUNT,
                packetCodec.decode((BinaryMessage) session.sentMessages().get(0)).command());
    }

    @Test
    void deserializesRemoteRedisEventsForLocalDelivery() throws Exception {
        TestWebSocketSession session = new TestWebSocketSession("listener-user");
        NotificationSessionRegistry registry = new NotificationSessionRegistry();
        registry.register(42L, session.session(), 5);
        DefaultNotificationRealtimePublisher publisher = publisher(registry, false, "node-a");
        NotificationRealtimeRedisListener listener = new NotificationRealtimeRedisListener(objectMapper, publisher);
        String payload = objectMapper.writeValueAsString(
                new NotificationRealtimeEvent("node-b", 42L, 101L, Map.of("total", 1L, "like", 1L)));

        listener.onMessage(new DefaultMessage(
                DefaultNotificationRealtimePublisher.REDIS_CHANNEL.getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8)), null);

        assertEquals(2, session.sentMessages().size());
        assertEquals(NotificationPacketCommand.NOTIF_PUSH,
                packetCodec.decode((BinaryMessage) session.sentMessages().get(0)).command());
        assertEquals(NotificationPacketCommand.UNREAD_COUNT,
                packetCodec.decode((BinaryMessage) session.sentMessages().get(1)).command());
    }

    private DefaultNotificationRealtimePublisher publisher(NotificationSessionRegistry registry,
                                                            boolean redisEnabled,
                                                            String instanceId) {
        NotificationMessageMapper mapper = (NotificationMessageMapper) Proxy.newProxyInstance(
                NotificationMessageMapper.class.getClassLoader(),
                new Class<?>[]{NotificationMessageMapper.class},
                (proxy, method, args) -> "selectById".equals(method.getName()) ? notification() : null
        );
        RedisTemplate<String, Object> redis = new RedisTemplate<>() {
            @Override
            public Long convertAndSend(String channel, Object message) {
                throw new IllegalStateException("redis unavailable");
            }
        };
        return new DefaultNotificationRealtimePublisher(
                mapper, registry, packetCodec, redis, objectMapper, instanceId, redisEnabled);
    }

    private NotificationMessagePO notification() {
        NotificationMessagePO message = new NotificationMessagePO();
        message.setId(101L);
        message.setReceiverUid(42L);
        message.setNotifType(1);
        message.setContentJson("{\"action\":\"like\",\"postId\":99,\"internalNote\":\"must not reach WebSocket\"}");
        message.setIsRead(0);
        message.setIsDeleted(0);
        message.setCreateTime(LocalDateTime.of(2026, 7, 31, 10, 0));
        return message;
    }
}
