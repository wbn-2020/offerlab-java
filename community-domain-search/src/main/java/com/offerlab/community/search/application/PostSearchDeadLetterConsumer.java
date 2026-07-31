package com.offerlab.community.search.application;

import com.offerlab.community.infra.mq.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Converts exhausted post-search Kafka deliveries into the durable search retry queue.
 *
 * <p>The retry queue is deduplicated by post id, so a redelivered DLT record cannot create an
 * unbounded number of recovery tasks. The DLT offset is acknowledged only after persistence
 * succeeds.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "offerlab.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PostSearchDeadLetterConsumer {

    private final SearchIndexRetryService retryService;

    @KafkaListener(
            topics = {"post.published.DLT", "post.updated.DLT", "post.deleted.DLT"},
            groupId = "${offerlab.search.kafka-dlt-consumer-group:offerlab-search-post-index-dlt}",
            containerFactory = "postSearchDeadLetterKafkaListenerContainerFactory",
            autoStartup = "${offerlab.search.kafka-dlt-consumer-enabled:true}"
    )
    public void onMessage(EventEnvelope<?> envelope, Acknowledgment ack) {
        if (envelope == null) {
            log.warn("post search DLT record skipped: empty envelope");
            ack.acknowledge();
            return;
        }
        Long postId = requirePostId(envelope);
        String eventType = normalize(envelope.getEventType());
        IllegalStateException cause = new IllegalStateException(
                "Kafka post-search event exhausted retries: messageId=" + envelope.getMessageId()
                        + ", eventType=" + eventType);
        switch (eventType) {
            case "POST_PUBLISHED", "POST_UPDATED" -> retryService.enqueueIndexRequired(postId, cause);
            case "POST_DELETED" -> retryService.enqueueDeleteRequired(postId, cause);
            default -> throw new IllegalArgumentException("unsupported post search DLT event type: " + eventType);
        }
        ack.acknowledge();
        log.warn("post search DLT persisted for recovery: messageId={} eventType={} postId={}",
                envelope.getMessageId(), eventType, postId);
    }

    private static Long requirePostId(EventEnvelope<?> envelope) {
        String sourceId = envelope.getSourceId();
        try {
            long postId = Long.parseLong(sourceId == null ? "" : sourceId.trim());
            if (postId > 0) {
                return postId;
            }
        } catch (NumberFormatException ignored) {
            // Fail below with an actionable message.
        }
        throw new IllegalArgumentException("post search DLT event requires a positive sourceId");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
