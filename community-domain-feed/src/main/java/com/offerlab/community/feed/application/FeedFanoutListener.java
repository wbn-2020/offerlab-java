package com.offerlab.community.feed.application;

import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.infra.mq.idempotent.IdempotentEventConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Optional local Spring event feed fanout.
 * Kafka-disabled deployments use the bounded database following-feed fallback
 * by default. This listener remains available as an explicit Redis fanout mode.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "offerlab.feed", name = "local-event-fanout-enabled", havingValue = "true")
public class FeedFanoutListener {

    private static final String CONSUMER_NAME = "feed-fanout";

    private final FeedFanoutService fanoutService;
    private final IdempotentEventConsumer idempotentConsumer;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostPublished(PostPublishedEvent event) {
        try {
            idempotentConsumer.consume(
                    FeedFanoutService.idempotencyKey(event),
                    "POST_PUBLISHED",
                    CONSUMER_NAME,
                    () -> fanoutService.fanoutPostPublished(event, "spring-local-event"));
        } catch (Exception e) {
            log.error("local feed fanout failed: {}", event, e);
        }
    }
}
