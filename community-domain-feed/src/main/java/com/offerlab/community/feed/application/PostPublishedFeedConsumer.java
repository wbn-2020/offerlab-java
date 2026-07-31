package com.offerlab.community.feed.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.mq.EventEnvelope;
import com.offerlab.community.infra.mq.idempotent.IdempotentEventConsumer;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "offerlab.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PostPublishedFeedConsumer {

    private static final String CONSUMER_NAME = "feed-fanout";

    private final FeedFanoutService fanoutService;
    private final ObjectMapper objectMapper;
    private final IdempotentEventConsumer idempotentConsumer;

    @KafkaListener(
            topics = "post.published",
            groupId = "${offerlab.feed.kafka-consumer-group:offerlab-feed-fanout}",
            containerFactory = "kafkaListenerContainerFactory",
            autoStartup = "${offerlab.feed.kafka-consumer-enabled:true}"
    )
    public void onMessage(EventEnvelope<?> envelope, Acknowledgment ack) {
        if (envelope == null) {
            log.warn("post.published feed message skipped: empty envelope");
            ack.acknowledge();
            return;
        }

        try {
            PostPublishedEvent event = objectMapper.convertValue(envelope.getPayload(), PostPublishedEvent.class);
            boolean processed = idempotentConsumer.consume(
                    FeedFanoutService.idempotencyKey(event),
                    envelope.getEventType(),
                    CONSUMER_NAME,
                    () -> fanoutService.fanoutPostPublished(event, "kafka:" + envelope.getMessageId()));
            ack.acknowledge();
            log.info("post.published feed message acked: messageId={} eventType={} processed={}",
                    envelope.getMessageId(), envelope.getEventType(), processed);
        } catch (Exception e) {
            log.error("post.published feed message failed, will retry: messageId={} eventType={}",
                    envelope.getMessageId(), envelope.getEventType(), e);
            throw e;
        }
    }
}
