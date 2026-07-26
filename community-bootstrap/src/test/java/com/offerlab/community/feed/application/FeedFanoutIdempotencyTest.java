package com.offerlab.community.feed.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.mq.EventEnvelope;
import com.offerlab.community.infra.mq.idempotent.IdempotentEventConsumer;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.post.domain.model.Post;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedFanoutIdempotencyTest {

    @Mock
    private FeedFanoutService fanoutService;
    @Mock
    private IdempotentEventConsumer idempotentConsumer;
    @Mock
    private Acknowledgment acknowledgment;

    @Test
    void duplicatePostPublishedEventIsAcknowledgedWithoutFanoutWork() {
        PostPublishedFeedConsumer consumer = new PostPublishedFeedConsumer(
                fanoutService, new ObjectMapper(), idempotentConsumer);
        EventEnvelope<PostPublishedEvent> envelope = envelope();
        when(idempotentConsumer.consume(
                eq("post.published:88:123456"),
                eq("POST_PUBLISHED"),
                eq("feed-fanout"),
                any(Runnable.class))).thenReturn(false);

        consumer.onMessage(envelope, acknowledgment);

        verify(acknowledgment).acknowledge();
        verify(fanoutService, never()).fanoutPostPublished(any(), any());
    }

    @Test
    void inboxOrFanoutFailureIsRethrownWithoutAcknowledging() {
        PostPublishedFeedConsumer consumer = new PostPublishedFeedConsumer(
                fanoutService, new ObjectMapper(), idempotentConsumer);
        EventEnvelope<PostPublishedEvent> envelope = envelope();
        when(idempotentConsumer.consume(
                eq("post.published:88:123456"),
                eq("POST_PUBLISHED"),
                eq("feed-fanout"),
                any(Runnable.class))).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> consumer.onMessage(envelope, acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void republishingTheSamePostUsesANewInboxIdentity() {
        PostPublishedEvent first = envelope().getPayload();
        PostPublishedEvent second = PostPublishedEvent.builder()
                .postId(first.getPostId())
                .timestamp(first.getTimestamp() + 1)
                .build();

        assertNotEquals(
                FeedFanoutService.idempotencyKey(first),
                FeedFanoutService.idempotencyKey(second));
    }

    private static EventEnvelope<PostPublishedEvent> envelope() {
        return EventEnvelope.<PostPublishedEvent>builder()
                .messageId("message-88")
                .eventType("POST_PUBLISHED")
                .payload(PostPublishedEvent.builder()
                        .postId(88L)
                        .authorId(7L)
                        .visibility(Post.VIS_PUBLIC)
                        .postStatus(Post.STATUS_PUBLISHED)
                        .timestamp(123456L)
                        .build())
                .build();
    }
}
