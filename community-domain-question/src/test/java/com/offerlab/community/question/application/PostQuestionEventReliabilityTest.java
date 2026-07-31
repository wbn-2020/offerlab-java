package com.offerlab.community.question.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.mq.EventEnvelope;
import com.offerlab.community.infra.mq.idempotent.EventConsumerInboxMapper;
import com.offerlab.community.infra.mq.idempotent.IdempotentEventConsumer;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostQuestionEventReliabilityTest {

    @Test
    void localAfterCommitPathIsDisabledWhenKafkaOwnsDelivery() {
        AtomicInteger extractionCount = new AtomicInteger();
        PostQuestionEventListener listener = new PostQuestionEventListener(
                questionFacade(extractionCount), postFacade());
        listener.setKafkaEnabled(true);

        listener.onPostPublished(PostPublishedEvent.builder().postId(42L).build());

        assertEquals(0, extractionCount.get());
    }

    @Test
    void localAfterCommitPathRunsWhenQuestionConsumerIsDisabled() {
        AtomicInteger extractionCount = new AtomicInteger();
        PostQuestionEventListener listener = new PostQuestionEventListener(
                questionFacade(extractionCount), postFacade());
        listener.setKafkaEnabled(true);
        listener.setKafkaConsumerEnabled(false);

        listener.onPostPublished(PostPublishedEvent.builder().postId(42L).build());

        assertEquals(1, extractionCount.get());
    }

    @Test
    void localBeforeCommitPathDoesNotSwallowTaskPersistenceFailure() {
        QuestionFacade failingFacade = (QuestionFacade) Proxy.newProxyInstance(
                QuestionFacade.class.getClassLoader(),
                new Class<?>[]{QuestionFacade.class},
                (proxy, method, args) -> {
                    if ("extractPostQuestions".equals(method.getName())) {
                        throw new IllegalStateException("task table unavailable");
                    }
                    return defaultValue(method.getReturnType());
                });
        PostQuestionEventListener listener = new PostQuestionEventListener(failingFacade, postFacade());
        listener.setKafkaEnabled(false);

        assertThrows(
                IllegalStateException.class,
                () -> listener.onPostPublished(PostPublishedEvent.builder().postId(42L).build()));
    }

    @Test
    void duplicateKafkaDeliveryCreatesOnlyOneExtractionTask() {
        AtomicInteger extractionCount = new AtomicInteger();
        PostQuestionEventListener listener = new PostQuestionEventListener(
                questionFacade(extractionCount), postFacade());
        PostQuestionEventConsumer consumer = new PostQuestionEventConsumer(
                listener,
                new ObjectMapper(),
                idempotentConsumer());
        AtomicInteger ackCount = new AtomicInteger();
        Acknowledgment ack = ackCount::incrementAndGet;
        EventEnvelope<PostPublishedEvent> envelope = EventEnvelope.<PostPublishedEvent>builder()
                .messageId("message-42")
                .idempotencyKey("message-42")
                .eventType("POST_PUBLISHED")
                .payload(PostPublishedEvent.builder().postId(42L).build())
                .build();

        consumer.onMessage(envelope, ack);
        consumer.onMessage(envelope, ack);

        assertEquals(1, extractionCount.get());
        assertEquals(2, ackCount.get());
    }

    @Test
    void nullKafkaEnvelopeIsAcknowledgedAndSkipped() {
        AtomicInteger extractionCount = new AtomicInteger();
        PostQuestionEventConsumer consumer = new PostQuestionEventConsumer(
                new PostQuestionEventListener(questionFacade(extractionCount), postFacade()),
                new ObjectMapper(),
                idempotentConsumer());
        AtomicInteger ackCount = new AtomicInteger();

        consumer.onMessage(null, ackCount::incrementAndGet);

        assertEquals(0, extractionCount.get());
        assertEquals(1, ackCount.get());
    }

    private static QuestionFacade questionFacade(AtomicInteger extractionCount) {
        return (QuestionFacade) Proxy.newProxyInstance(
                QuestionFacade.class.getClassLoader(),
                new Class<?>[]{QuestionFacade.class},
                (proxy, method, args) -> {
                    if ("extractPostQuestions".equals(method.getName())) {
                        extractionCount.incrementAndGet();
                        return 100L;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static PostFacade postFacade() {
        return (PostFacade) Proxy.newProxyInstance(
                PostFacade.class.getClassLoader(),
                new Class<?>[]{PostFacade.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == int.class) {
            return 0;
        }
        return 0;
    }

    private static IdempotentEventConsumer idempotentConsumer() {
        return new IdempotentEventConsumer(
                new RecordingInboxMapper(1, 0),
                new IncrementingIdGenerator());
    }

    private static final class RecordingInboxMapper implements EventConsumerInboxMapper {
        private final Queue<Integer> insertResults = new ArrayDeque<>();

        private RecordingInboxMapper(Integer... insertResults) {
            this.insertResults.addAll(java.util.List.of(insertResults));
        }

        @Override
        public int insertIfAbsent(Long id, String consumerName, String idempotencyKey, String eventType) {
            return insertResults.remove();
        }

        @Override
        public int deleteBefore(LocalDateTime before, int limit) {
            return 0;
        }
    }

    private static final class IncrementingIdGenerator extends SnowflakeIdGenerator {
        private long value;

        @Override
        public synchronized long nextId() {
            return ++value;
        }
    }
}
