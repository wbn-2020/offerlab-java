package com.offerlab.community.infra.mq.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.mq.EventEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxSchedulerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void renewsEachClaimImmediatelyBeforeSending() throws Exception {
        List<String> operations = new ArrayList<>();
        List<OutboxMessage> messages = List.of(message(1L), message(2L), message(3L));
        RecordingMapper mapper = new RecordingMapper(messages, null, operations);
        RecordingKafkaTemplate kafkaTemplate = new RecordingKafkaTemplate(operations);

        new OutboxScheduler(mapper.proxy(), kafkaTemplate, objectMapper).flush();

        assertEquals(List.of(
                "claim:100",
                "load:100",
                "renew:1", "send:1", "sent:1",
                "renew:2", "send:2", "sent:2",
                "renew:3", "send:3", "sent:3"
        ), operations);
        assertEquals(3, mapper.renewedUntil.size());
        assertTrue(mapper.renewedUntil.stream().allMatch(deadline -> deadline.isAfter(LocalDateTime.now().plusSeconds(50))));
    }

    @Test
    void skipsStaleMessageWhenItsLeaseCannotBeRenewed() throws Exception {
        List<String> operations = new ArrayList<>();
        List<OutboxMessage> messages = List.of(message(1L), message(2L));
        RecordingMapper mapper = new RecordingMapper(messages, 1L, operations);
        RecordingKafkaTemplate kafkaTemplate = new RecordingKafkaTemplate(operations);

        new OutboxScheduler(mapper.proxy(), kafkaTemplate, objectMapper).flush();

        assertEquals(List.of(
                "claim:100",
                "load:100",
                "renew:1",
                "renew:2", "send:2", "sent:2"
        ), operations);
    }

    @Test
    void cleansTerminalRowsInBoundedRetentionBatches() throws Exception {
        List<String> operations = new ArrayList<>();
        RecordingMapper mapper = new RecordingMapper(List.of(), null, operations);
        OutboxScheduler scheduler = new OutboxScheduler(mapper.proxy(), new RecordingKafkaTemplate(operations), objectMapper);

        OutboxScheduler.class.getMethod("cleanupTerminalMessages").invoke(scheduler);

        assertEquals(List.of("cleanup:1:1000", "cleanup:2:1000"), operations);
    }

    private OutboxMessage message(Long id) throws Exception {
        EventEnvelope<Long> envelope = EventEnvelope.<Long>builder()
                .messageId(String.valueOf(id))
                .eventType("TEST")
                .payload(id)
                .build();
        return OutboxMessage.builder()
                .id(id)
                .aggregateId(id)
                .topic("test.events")
                .payload(objectMapper.writeValueAsString(envelope))
                .retryCount(0)
                .build();
    }

    private static final class RecordingMapper implements InvocationHandler {

        private final List<OutboxMessage> messages;
        private final Long lostId;
        private final List<String> operations;
        private final List<LocalDateTime> renewedUntil = new ArrayList<>();

        private RecordingMapper(List<OutboxMessage> messages, Long lostId, List<String> operations) {
            this.messages = messages;
            this.lostId = lostId;
            this.operations = operations;
        }

        private OutboxMessageMapper proxy() {
            return (OutboxMessageMapper) Proxy.newProxyInstance(
                    OutboxMessageMapper.class.getClassLoader(),
                    new Class<?>[]{OutboxMessageMapper.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            return switch (method.getName()) {
                case "claimPending" -> {
                    operations.add("claim:" + args[2]);
                    yield messages.size();
                }
                case "findClaimed" -> {
                    operations.add("load:" + args[1]);
                    yield messages;
                }
                case "renewClaim" -> {
                    Long id = (Long) args[0];
                    operations.add("renew:" + id);
                    renewedUntil.add((LocalDateTime) args[2]);
                    yield id.equals(lostId) ? 0 : 1;
                }
                case "markSent" -> {
                    operations.add("sent:" + args[0]);
                    yield 1;
                }
                case "deleteTerminalBefore" -> {
                    operations.add("cleanup:" + args[0] + ":" + args[2]);
                    yield 0;
                }
                default -> defaultValue(method.getReturnType());
            };
        }
    }

    private static final class RecordingKafkaTemplate extends KafkaTemplate<String, EventEnvelope<?>> {

        private final List<String> operations;

        private RecordingKafkaTemplate(List<String> operations) {
            super(producerFactory());
            this.operations = operations;
        }

        @Override
        public CompletableFuture<SendResult<String, EventEnvelope<?>>> send(Message<?> message) {
            EventEnvelope<?> envelope = (EventEnvelope<?>) message.getPayload();
            operations.add("send:" + envelope.getMessageId());
            return CompletableFuture.completedFuture(null);
        }

        @SuppressWarnings("unchecked")
        private static ProducerFactory<String, EventEnvelope<?>> producerFactory() {
            return (ProducerFactory<String, EventEnvelope<?>>) Proxy.newProxyInstance(
                    ProducerFactory.class.getClassLoader(),
                    new Class<?>[]{ProducerFactory.class},
                    (proxy, method, args) -> defaultValue(method.getReturnType())
            );
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return 0;
    }
}
