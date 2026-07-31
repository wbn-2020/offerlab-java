package com.offerlab.community.notification.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.utils.LogMask;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.notification.infrastructure.persistence.mapper.SubscriptionUpdateDigestMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.SubscriptionUpdateDigestPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionUpdateDigestService {

    private static final int REQUIRED_COLUMN_COUNT = 14;
    private static final Set<String> SOURCE_TYPES = Set.of("TOPIC", "DISCUSSION", "NEED");
    private static final Set<String> RESOURCE_TYPES =
            Set.of("POST", "TOPIC", "NEED", "COLLECTION", "SERIES");

    private final SubscriptionUpdateDigestMapper digestMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private volatile Boolean readyCache;

    /**
     * Records a digest-only delivery fact. Duplicate writes are intentionally absorbed by the
     * database unique key so event replay and local/Kafka overlap remain safe.
     */
    public SubscriptionUpdateDigestRecordResult record(SubscriptionUpdateDigestCommand command) {
        SubscriptionUpdateDigestPO row = toRow(command);
        if (!isReady()) {
            log.warn("subscription update digest table unavailable: receiverUid={} sourceType={} sourceId={} eventKey={}",
                    LogMask.id(row.getReceiverUid()), row.getSourceType(), LogMask.id(row.getSourceId()),
                    LogMask.key(row.getEventKey()));
            return SubscriptionUpdateDigestRecordResult.TABLE_UNAVAILABLE;
        }
        int inserted = digestMapper.insertIgnore(row);
        return inserted > 0
                ? SubscriptionUpdateDigestRecordResult.CREATED
                : SubscriptionUpdateDigestRecordResult.DUPLICATE;
    }

    public boolean isReady() {
        if (Boolean.TRUE.equals(readyCache)) {
            return true;
        }
        try {
            boolean ready = digestMapper.tableExists() > 0
                    && digestMapper.requiredColumnCount() >= REQUIRED_COLUMN_COUNT;
            if (ready) {
                readyCache = true;
            }
            return ready;
        } catch (RuntimeException e) {
            log.warn("subscription update digest table readiness check failed", e);
            return false;
        }
    }

    private SubscriptionUpdateDigestPO toRow(SubscriptionUpdateDigestCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("subscription update digest command is required");
        }
        SubscriptionUpdateDigestPO row = new SubscriptionUpdateDigestPO();
        row.setId(idGenerator.nextId());
        row.setReceiverUid(requirePositive(command.receiverUid(), "receiverUid"));
        row.setSourceType(normalizeEnum(command.sourceType(), "sourceType", SOURCE_TYPES));
        row.setSourceId(requirePositive(command.sourceId(), "sourceId"));
        row.setResourceType(normalizeEnum(command.resourceType(), "resourceType", RESOURCE_TYPES));
        row.setResourceId(requirePositive(command.resourceId(), "resourceId"));
        row.setEventType(normalizeEventType(command.eventType()));
        row.setEventKey(requireText(command.eventKey(), "eventKey", 160));
        row.setActorUid(optionalPositive(command.actorUid(), "actorUid"));
        row.setPayloadJson(toPayloadJson(command.safePayload()));
        row.setOccurredAt(toOccurredAt(command.occurredAt()));
        row.setIsDeleted(0);
        return row;
    }

    private String toPayloadJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(sanitizePayload(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("safePayload cannot be serialized", e);
        }
    }

    private static Map<String, Object> sanitizePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        copyText(payload, safe, "action", 64);
        copyText(payload, safe, "summary", 300);
        copySafePath(payload, safe, "targetPath");
        copyPositiveLong(payload, safe, "postId");
        copyPositiveLong(payload, safe, "commentId");
        copyPositiveLong(payload, safe, "topicId");
        copyPositiveLong(payload, safe, "needId");
        return Map.copyOf(safe);
    }

    private static void copyText(Map<String, Object> source,
                                 Map<String, Object> target,
                                 String key,
                                 int maxLength) {
        Object value = source.get(key);
        if (!(value instanceof String text) || !StringUtils.hasText(text)) {
            return;
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() > maxLength) {
            normalized = normalized.substring(0, maxLength);
        }
        target.put(key, normalized);
    }

    private static void copySafePath(Map<String, Object> source,
                                     Map<String, Object> target,
                                     String key) {
        Object value = source.get(key);
        if (value instanceof String path && isSafePath(path.trim())) {
            target.put(key, path.trim());
        }
    }

    private static void copyPositiveLong(Map<String, Object> source,
                                         Map<String, Object> target,
                                         String key) {
        Long value = positiveLong(source.get(key));
        if (value != null) {
            target.put(key, value);
        }
    }

    private static String normalizeEnum(String value, String field, Set<String> allowed) {
        String normalized = requireText(value, field, 24).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(field + " is unsupported");
        }
        return normalized;
    }

    private static String normalizeEventType(String value) {
        String normalized = requireText(value, "eventType", 64).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("eventType is invalid");
        }
        return normalized;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static Long optionalPositive(Long value, String field) {
        if (value == null) {
            return null;
        }
        return requirePositive(value, field);
    }

    private static LocalDateTime toOccurredAt(Instant occurredAt) {
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
        return LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC);
    }

    private static Long positiveLong(Object value) {
        if (value instanceof Number number) {
            long parsed = number.longValue();
            return parsed > 0 ? parsed : null;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                long parsed = Long.parseLong(text.trim());
                return parsed > 0 ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean isSafePath(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return path.startsWith("/")
                && !path.startsWith("//")
                && !path.startsWith("/api/")
                && !path.contains("\\")
                && !path.matches(".*\\s+.*")
                && !lower.contains("://")
                && !lower.startsWith("/javascript:")
                && !lower.startsWith("/data:")
                && path.length() <= 300;
    }
}
