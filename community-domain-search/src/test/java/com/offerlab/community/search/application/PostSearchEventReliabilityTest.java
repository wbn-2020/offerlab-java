package com.offerlab.community.search.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.mq.EventEnvelope;
import com.offerlab.community.infra.mq.idempotent.EventConsumerInboxMapper;
import com.offerlab.community.infra.mq.idempotent.IdempotentEventConsumer;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostSearchEventReliabilityTest {

    @Test
    void localAfterCommitPathIsDisabledWhenKafkaOwnsDelivery() {
        PostSearchIndexer indexer = mock(PostSearchIndexer.class);
        SearchIndexRetryService retryService = mock(SearchIndexRetryService.class);
        PostSearchEventListener listener = new PostSearchEventListener(indexer, retryService);
        listener.setKafkaEnabled(true);

        listener.onPostPublished(PostPublishedEvent.builder().postId(42L).build());

        verify(indexer, never()).indexPost(42L);
        verify(retryService, never()).enqueueIndex(42L, null);
    }

    @Test
    void localAfterCommitPathRunsWhenSearchConsumerIsDisabled() {
        PostSearchIndexer indexer = mock(PostSearchIndexer.class);
        when(indexer.indexPost(42L)).thenReturn(true);
        PostSearchEventListener listener = new PostSearchEventListener(
                indexer, mock(SearchIndexRetryService.class));
        listener.setKafkaEnabled(true);
        listener.setKafkaConsumerEnabled(false);

        listener.onPostPublished(PostPublishedEvent.builder().postId(42L).build());

        verify(indexer).indexPost(42L);
    }

    @Test
    void duplicateKafkaDeliveryIsAcknowledgedWithoutReindexing() {
        PostSearchIndexer indexer = mock(PostSearchIndexer.class);
        when(indexer.indexPost(42L)).thenReturn(true);
        PostSearchEventListener listener = new PostSearchEventListener(
                indexer, mock(SearchIndexRetryService.class));
        PostSearchEventConsumer consumer = new PostSearchEventConsumer(
                listener,
                new ObjectMapper(),
                idempotentConsumer());
        Acknowledgment ack = mock(Acknowledgment.class);
        EventEnvelope<PostPublishedEvent> envelope = EventEnvelope.<PostPublishedEvent>builder()
                .messageId("message-42")
                .idempotencyKey("message-42")
                .eventType("POST_PUBLISHED")
                .payload(PostPublishedEvent.builder().postId(42L).build())
                .build();

        consumer.onMessage(envelope, ack);
        consumer.onMessage(envelope, ack);

        verify(indexer, times(1)).indexPost(42L);
        verify(ack, times(2)).acknowledge();
    }

    @Test
    void nullKafkaEnvelopeIsAcknowledgedAndSkipped() {
        PostSearchIndexer indexer = mock(PostSearchIndexer.class);
        PostSearchEventConsumer consumer = new PostSearchEventConsumer(
                new PostSearchEventListener(indexer, mock(SearchIndexRetryService.class)),
                new ObjectMapper(),
                idempotentConsumer());
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.onMessage(null, ack);

        verify(indexer, never()).indexPost(anyLong());
        verify(ack).acknowledge();
    }

    private static IdempotentEventConsumer idempotentConsumer() {
        EventConsumerInboxMapper mapper = mock(EventConsumerInboxMapper.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(1L, 2L);
        when(mapper.insertIfAbsent(anyLong(), any(), any(), any())).thenReturn(1, 0);
        return new IdempotentEventConsumer(mapper, idGenerator);
    }
}
