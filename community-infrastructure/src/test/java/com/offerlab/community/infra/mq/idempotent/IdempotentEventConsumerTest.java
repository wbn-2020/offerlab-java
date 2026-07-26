package com.offerlab.community.infra.mq.idempotent;

import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.mq.EventEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotentEventConsumerTest {

    @Test
    void duplicateDeliveryIsSkippedForTheSameConsumer() {
        RecordingInboxMapper mapper = new RecordingInboxMapper(1, 0);
        IdempotentEventConsumer consumer = new IdempotentEventConsumer(mapper, new IncrementingIdGenerator());
        AtomicInteger executions = new AtomicInteger();
        EventEnvelope<Object> envelope = EventEnvelope.builder()
                .messageId("message-1")
                .idempotencyKey("business-1")
                .eventType("POST_UPDATED")
                .payload(new Object())
                .build();

        assertTrue(consumer.consume(envelope, "search-post-index", executions::incrementAndGet));
        assertFalse(consumer.consume(envelope, "search-post-index", executions::incrementAndGet));

        assertEquals(1, executions.get());
        assertEquals("search-post-index", mapper.lastConsumerName);
        assertEquals("business-1", mapper.lastIdempotencyKey);
    }

    @Test
    void failedHandlerPropagatesSoTheInboxTransactionCanRollBack() {
        RecordingInboxMapper mapper = new RecordingInboxMapper(1);
        IdempotentEventConsumer consumer = new IdempotentEventConsumer(mapper, new IncrementingIdGenerator());
        EventEnvelope<Object> envelope = EventEnvelope.builder()
                .messageId("message-2")
                .payload(new Object())
                .build();

        assertThrows(IllegalStateException.class,
                () -> consumer.consume(envelope, "question-post-sync",
                        () -> {
                            throw new IllegalStateException("simulated failure");
                        }));

        assertEquals(1, mapper.insertCount);
    }

    @Test
    void duplicateDeliveryDoesNotInvokeTheHandler() {
        RecordingInboxMapper mapper = new RecordingInboxMapper(
                new DuplicateKeyException("duplicate consumer/key"));
        IdempotentEventConsumer consumer = new IdempotentEventConsumer(mapper, new IncrementingIdGenerator());
        AtomicInteger executions = new AtomicInteger();

        assertFalse(consumer.consume(
                "message-3", "LIKE", "notification-events", executions::incrementAndGet));

        assertEquals(0, executions.get());
    }

    @Test
    void consumeMethodsRemainTransactional() throws Exception {
        assertNotNull(IdempotentEventConsumer.class
                .getMethod("consume", EventEnvelope.class, String.class, Runnable.class)
                .getAnnotation(Transactional.class));
        assertNotNull(IdempotentEventConsumer.class
                .getMethod("consume", String.class, String.class, String.class, Runnable.class)
                .getAnnotation(Transactional.class));
    }

    private static final class RecordingInboxMapper implements EventConsumerInboxMapper {
        private final Queue<Object> results = new ArrayDeque<>();
        private int insertCount;
        private String lastConsumerName;
        private String lastIdempotencyKey;

        private RecordingInboxMapper(Object... results) {
            this.results.addAll(java.util.List.of(results));
        }

        @Override
        public int insertIfAbsent(Long id, String consumerName, String idempotencyKey, String eventType) {
            insertCount++;
            lastConsumerName = consumerName;
            lastIdempotencyKey = idempotencyKey;
            Object result = results.remove();
            if (result instanceof RuntimeException failure) {
                throw failure;
            }
            return (Integer) result;
        }

        @Override
        public int deleteBefore(LocalDateTime before, int limit) {
            return 0;
        }
    }

    private static final class IncrementingIdGenerator extends SnowflakeIdGenerator {
        private long value = 100L;

        @Override
        public synchronized long nextId() {
            return ++value;
        }
    }
}
