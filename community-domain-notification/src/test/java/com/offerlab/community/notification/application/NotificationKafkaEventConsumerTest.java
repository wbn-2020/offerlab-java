package com.offerlab.community.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.mq.EventEnvelope;
import com.offerlab.community.infra.mq.idempotent.EventConsumerInboxMapper;
import com.offerlab.community.infra.mq.idempotent.IdempotentEventConsumer;
import com.offerlab.community.interaction.api.event.PostLikedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationKafkaEventConsumerTest {

    @Test
    void localDurableListenerIsDisabledWhenKafkaOwnsDelivery() {
        RecordingListener listener = new RecordingListener();
        listener.setKafkaEnabled(true);

        listener.onPostLiked(event());

        assertEquals(0, listener.postLikedCount.get());
    }

    @Test
    void localDurableListenerRunsWhenNotificationConsumerIsDisabled() {
        RecordingListener listener = new RecordingListener();
        listener.setKafkaEnabled(true);
        listener.setKafkaConsumerEnabled(false);

        listener.onPostLiked(event());

        assertEquals(1, listener.postLikedCount.get());
    }

    @Test
    void duplicateKafkaDeliveryIsAcknowledgedWithoutRepeatingNotification() {
        RecordingListener listener = new RecordingListener();
        NotificationKafkaEventConsumer consumer = new NotificationKafkaEventConsumer(
                listener,
                new ObjectMapper(),
                idempotentConsumer());
        AtomicInteger ackCount = new AtomicInteger();
        Acknowledgment ack = ackCount::incrementAndGet;
        EventEnvelope<PostLikedEvent> envelope = EventEnvelope.<PostLikedEvent>builder()
                .messageId("message-like-1")
                .idempotencyKey("message-like-1")
                .eventType("LIKE")
                .payload(event())
                .build();

        consumer.onMessage(envelope, ack);
        consumer.onMessage(envelope, ack);

        assertEquals(1, listener.postLikedCount.get());
        assertEquals(2, ackCount.get());
    }

    @Test
    void nullKafkaEnvelopeIsAcknowledgedAndSkipped() {
        RecordingListener listener = new RecordingListener();
        NotificationKafkaEventConsumer consumer = new NotificationKafkaEventConsumer(
                listener,
                new ObjectMapper(),
                idempotentConsumer());
        AtomicInteger ackCount = new AtomicInteger();

        consumer.onMessage(null, ackCount::incrementAndGet);

        assertEquals(0, listener.postLikedCount.get());
        assertEquals(1, ackCount.get());
    }

    private static PostLikedEvent event() {
        return PostLikedEvent.builder()
                .uid(11L)
                .postId(22L)
                .postAuthorId(33L)
                .timestamp(44L)
                .build();
    }

    private static final class RecordingListener extends NotificationEventListener {
        private final AtomicInteger postLikedCount = new AtomicInteger();

        private RecordingListener() {
            super(null, null, null, null, null, null);
        }

        @Override
        public void handlePostLikedSynchronously(PostLikedEvent event) {
            postLikedCount.incrementAndGet();
        }
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
