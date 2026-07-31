package com.offerlab.community.notification.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.utils.LogMask;
import com.offerlab.community.notification.application.NotificationRealtimePublisher;
import com.offerlab.community.notification.infrastructure.persistence.mapper.NotificationMessageMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationMessagePO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "offerlab.realtime", name = "websocket-enabled", havingValue = "true")
public class DefaultNotificationRealtimePublisher implements NotificationRealtimePublisher {

    static final String REDIS_CHANNEL = "offerlab:notification:realtime:v1";
    static final CloseStatus OUTBOUND_OVERFLOW_CLOSE_STATUS = new CloseStatus(4005, "Outbound queue overflow");
    private static final Set<String> SAFE_CONTENT_FIELDS = Set.of(
            "action", "targetType", "targetId", "postId", "postTitle", "commentId", "suggestionId", "decision", "userId",
            "requestId", "sourceType", "reportId", "userStatus", "reportStatus", "status",
            "resultText", "userResultText", "targetPath", "jumpPath", "href", "topicId",
            "topicSlug", "topicName", "topics", "placementType", "placementKey", "source",
            "dedupKey", "message", "title", "eventId", "eventType", "contentId", "contentTitle",
            "placementId", "placementLabel", "sectionKey", "reason", "reasonText", "entrance",
            "needId", "targetNeedId"
    );

    private final NotificationMessageMapper mapper;
    private final NotificationSessionRegistry sessionRegistry;
    private final NotificationPacketCodec packetCodec;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final String instanceId;
    private final boolean redisPubSubEnabled;

    public DefaultNotificationRealtimePublisher(NotificationMessageMapper mapper,
                                                NotificationSessionRegistry sessionRegistry,
                                                NotificationPacketCodec packetCodec,
                                                RedisTemplate<String, Object> redisTemplate,
                                                ObjectMapper objectMapper,
                                                @Value("${offerlab.realtime.instance-id:${random.uuid}}") String instanceId,
                                                @Value("${offerlab.redis.pubsub-enabled:true}") boolean redisPubSubEnabled) {
        this.mapper = mapper;
        this.sessionRegistry = sessionRegistry;
        this.packetCodec = packetCodec;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.instanceId = instanceId;
        this.redisPubSubEnabled = redisPubSubEnabled;
    }

    @Override
    public void publishNotification(Long receiverUid, Long notificationId, Map<String, Long> unread) {
        publish(new NotificationRealtimeEvent(instanceId, receiverUid, notificationId, normalizeUnread(unread)));
    }

    @Override
    public void publishUnreadCount(Long receiverUid, Map<String, Long> unread) {
        publish(new NotificationRealtimeEvent(instanceId, receiverUid, null, normalizeUnread(unread)));
    }

    void receiveRemote(NotificationRealtimeEvent event) {
        if (event == null || !event.isValid() || instanceId.equals(event.instanceId())) {
            return;
        }
        deliver(event);
    }

    private void publish(NotificationRealtimeEvent event) {
        if (!event.isValid()) {
            return;
        }
        deliver(event);
        publishToRedis(event);
    }

    private void deliver(NotificationRealtimeEvent event) {
        List<WebSocketSession> sessions = sessionRegistry.sessionsFor(event.receiverUid());
        if (sessions.isEmpty()) {
            return;
        }

        if (event.notificationId() != null) {
            NotificationMessagePO message = loadNotification(event);
            if (message != null) {
                sendToSessions(sessions, NotificationPacketCommand.NOTIF_PUSH, toNotificationPayload(message));
            }
        }
        sendToSessions(sessions, NotificationPacketCommand.UNREAD_COUNT, event.unread());
    }

    private NotificationMessagePO loadNotification(NotificationRealtimeEvent event) {
        try {
            NotificationMessagePO message = mapper.selectById(event.notificationId());
            if (message == null
                    || !event.receiverUid().equals(message.getReceiverUid())
                    || Integer.valueOf(1).equals(message.getIsDeleted())) {
                return null;
            }
            return message;
        } catch (RuntimeException e) {
            log.warn("realtime notification lookup failed: uid={} notificationId={} reason={}",
                    LogMask.id(event.receiverUid()), LogMask.id(event.notificationId()), LogMask.message(e));
            return null;
        }
    }

    private Map<String, Object> toNotificationPayload(NotificationMessagePO message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", message.getId());
        payload.put("type", typeName(message.getNotifType()));
        payload.put("content", parseContent(message.getContentJson()));
        payload.put("isRead", Integer.valueOf(1).equals(message.getIsRead()));
        if (message.getCreateTime() != null) {
            payload.put("createTime", message.getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli());
        }
        return payload;
    }

    private void sendToSessions(List<WebSocketSession> sessions,
                                NotificationPacketCommand command,
                                Object body) {
        try {
            packetCodec.encode(command, body);
        } catch (IllegalArgumentException e) {
            log.warn("realtime packet omitted because it exceeds the frame limit: command={} reason={}",
                    command, LogMask.message(e));
            return;
        }
        for (WebSocketSession session : sessions) {
            try {
                if (!session.isOpen()) {
                    sessionRegistry.unregister(session);
                    continue;
                }
                session.sendMessage(packetCodec.encode(command, body));
            } catch (IOException | RuntimeException e) {
                sessionRegistry.unregister(session);
                closeOutboundOverflow(session);
                log.debug("realtime notification delivery failed: sessionId={} reason={}",
                        session.getId(), LogMask.message(e));
            }
        }
    }

    private void publishToRedis(NotificationRealtimeEvent event) {
        if (!redisPubSubEnabled) {
            return;
        }
        try {
            redisTemplate.convertAndSend(REDIS_CHANNEL, objectMapper.writeValueAsString(event));
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("realtime redis fan-out degraded: uid={} notificationId={} reason={}",
                    LogMask.id(event.receiverUid()), LogMask.id(event.notificationId()), LogMask.message(e));
        }
    }

    private void closeOutboundOverflow(WebSocketSession session) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.close(OUTBOUND_OVERFLOW_CLOSE_STATUS);
        } catch (IOException ignored) {
            // The transport may already have closed the session.
        }
    }

    private Map<String, Long> normalizeUnread(Map<String, Long> unread) {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("total", 0L);
        result.put("like", 0L);
        result.put("comment", 0L);
        result.put("favorite", 0L);
        result.put("follower", 0L);
        result.put("mention", 0L);
        result.put("system", 0L);
        if (unread != null) {
            unread.forEach((key, value) -> {
                if (result.containsKey(key) && value != null && value >= 0) {
                    result.put(key, value);
                }
            });
        }
        return Map.copyOf(result);
    }

    private Map<String, Object> parseContent(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return Map.of();
        }
        try {
            return sanitizeContent(objectMapper.readValue(contentJson, new TypeReference<Map<String, Object>>() { }));
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private Map<String, Object> sanitizeContent(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> content = new LinkedHashMap<>();
        raw.forEach((field, value) -> {
            if (!SAFE_CONTENT_FIELDS.contains(field)) {
                return;
            }
            if ("targetPath".equals(field) || "jumpPath".equals(field) || "href".equals(field)) {
                String path = safePath(value);
                if (path != null) {
                    content.put(field, path);
                }
                return;
            }
            if ("topics".equals(field)) {
                List<Map<String, Object>> topics = sanitizeTopics(value);
                if (!topics.isEmpty()) {
                    content.put(field, topics);
                }
                return;
            }
            Object safeValue = safeScalar(value);
            if (safeValue != null) {
                content.put(field, safeValue);
            }
        });
        return content;
    }

    private List<Map<String, Object>> sanitizeTopics(Object value) {
        if (!(value instanceof List<?> topics)) {
            return List.of();
        }
        return topics.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::sanitizeTopic)
                .filter(topic -> !topic.isEmpty())
                .toList();
    }

    private Map<String, Object> sanitizeTopic(Map<?, ?> raw) {
        Map<String, Object> topic = new LinkedHashMap<>();
        putSafeScalar(topic, "topicId", raw.get("topicId"));
        putSafeScalar(topic, "topicSlug", raw.get("topicSlug"));
        putSafeScalar(topic, "topicName", raw.get("topicName"));
        return topic;
    }

    private void putSafeScalar(Map<String, Object> target, String field, Object value) {
        Object safeValue = safeScalar(value);
        if (safeValue != null) {
            target.put(field, safeValue);
        }
    }

    private Object safeScalar(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
            }
        }
        return null;
    }

    private String safePath(Object value) {
        if (!(value instanceof String text)) {
            return null;
        }
        String path = text.trim();
        String lower = path.toLowerCase(Locale.ROOT);
        if (path.isEmpty()
                || !path.startsWith("/")
                || path.startsWith("//")
                || path.startsWith("/api/")
                || path.contains("\\")
                || path.matches(".*\\s+.*")
                || lower.startsWith("/javascript:")
                || lower.startsWith("/data:")
                || lower.contains("://")) {
            return null;
        }
        return path.length() > 300 ? null : path;
    }

    private String typeName(Integer type) {
        if (type == null) {
            return "system";
        }
        return switch (type) {
            case 1 -> "like";
            case 2 -> "comment";
            case 3 -> "favorite";
            case 4 -> "follower";
            case 6 -> "mention";
            default -> "system";
        };
    }
}
