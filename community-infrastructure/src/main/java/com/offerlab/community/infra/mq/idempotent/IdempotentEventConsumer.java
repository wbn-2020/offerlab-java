package com.offerlab.community.infra.mq.idempotent;

import com.offerlab.community.infra.mq.EventEnvelope;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes an event handler in the same database transaction as its durable inbox insert.
 */
@Component
@RequiredArgsConstructor
public class IdempotentEventConsumer {

    private static final int MAX_CONSUMER_NAME_LENGTH = 64;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 160;
    private static final int MAX_EVENT_TYPE_LENGTH = 64;

    private final EventConsumerInboxMapper inboxMapper;
    private final SnowflakeIdGenerator idGenerator;

    @Transactional
    public boolean consume(EventEnvelope<?> envelope, String consumerName, Runnable handler) {
        if (envelope == null) {
            throw new IllegalArgumentException("event envelope is required");
        }
        return consume(idempotencyKey(envelope), envelope.getEventType(), consumerName, handler);
    }

    @Transactional
    public boolean consume(String idempotencyKey, String eventType, String consumerName, Runnable handler) {
        if (consumerName == null || consumerName.isBlank()) {
            throw new IllegalArgumentException("consumer name is required");
        }
        if (handler == null) {
            throw new IllegalArgumentException("event handler is required");
        }

        String normalizedConsumer = boundedRequired(
                consumerName, MAX_CONSUMER_NAME_LENGTH, "consumer name");
        String normalizedKey = boundedRequired(
                idempotencyKey, MAX_IDEMPOTENCY_KEY_LENGTH, "event idempotency key");
        String normalizedEventType = boundedOptional(eventType, MAX_EVENT_TYPE_LENGTH, "event type");
        int inserted;
        try {
            inserted = inboxMapper.insertIfAbsent(
                    idGenerator.nextId(), normalizedConsumer, normalizedKey, normalizedEventType);
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
        if (inserted == 0) {
            return false;
        }
        if (inserted != 1) {
            throw new IllegalStateException("unexpected event inbox insert count: " + inserted);
        }

        handler.run();
        return true;
    }

    private String idempotencyKey(EventEnvelope<?> envelope) {
        String key = clean(envelope.getIdempotencyKey());
        if (!key.isBlank()) {
            return key;
        }
        String messageId = clean(envelope.getMessageId());
        if (messageId.isBlank()) {
            throw new IllegalArgumentException("event idempotencyKey or messageId is required");
        }
        return messageId;
    }

    private String boundedRequired(String value, int maxLength, String label) {
        String normalized = clean(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return bounded(normalized, maxLength, label);
    }

    private String boundedOptional(String value, int maxLength, String label) {
        String normalized = clean(value);
        return normalized.isBlank() ? null : bounded(normalized, maxLength, label);
    }

    private String bounded(String value, int maxLength, String label) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(label + " exceeds " + maxLength + " characters");
        }
        return value;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
