package com.offerlab.community.feed.application;

import com.offerlab.community.post.api.event.PostPublishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Legacy local Spring event fanout.
 * Disabled by default after Kafka fanout is introduced. If it is enabled for
 * fallback, FeedFanoutService still uses the same business idempotent key as
 * the Kafka consumer to avoid duplicate inbox writes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "offerlab.feed", name = "local-event-fanout-enabled", havingValue = "true")
public class FeedFanoutListener {

    private final FeedFanoutService fanoutService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostPublished(PostPublishedEvent event) {
        try {
            fanoutService.fanoutPostPublished(event, "spring-local-event");
        } catch (Exception e) {
            log.error("local feed fanout failed: {}", event, e);
        }
    }
}
