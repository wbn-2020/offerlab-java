package com.offerlab.community.search.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.mq.EventEnvelope;
import com.offerlab.community.infra.mq.idempotent.IdempotentEventConsumer;
import com.offerlab.community.post.api.event.PostDeletedEvent;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.post.api.event.PostUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "offerlab.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PostSearchEventConsumer {

    private static final String CONSUMER_NAME = "search-post-index";

    private final PostSearchEventListener listener;
    private final ObjectMapper objectMapper;
    private final IdempotentEventConsumer idempotentConsumer;

    @KafkaListener(
            topics = {"post.published", "post.updated", "post.deleted"},
            groupId = "${offerlab.search.kafka-consumer-group:offerlab-search-post-index}",
            containerFactory = "kafkaListenerContainerFactory",
            autoStartup = "${offerlab.search.kafka-consumer-enabled:true}"
    )
    public void onMessage(EventEnvelope<?> envelope, Acknowledgment ack) {
        if (envelope == null) {
            log.warn("post search event skipped: empty envelope");
            ack.acknowledge();
            return;
        }
        try {
            boolean processed = idempotentConsumer.consume(
                    envelope, CONSUMER_NAME, () -> dispatch(envelope));
            ack.acknowledge();
            log.info("post search event acked: messageId={} eventType={} processed={}",
                    envelope == null ? null : envelope.getMessageId(),
                    envelope == null ? null : envelope.getEventType(),
                    processed);
        } catch (RuntimeException e) {
            log.error("post search event failed, will retry: messageId={} eventType={}",
                    envelope == null ? null : envelope.getMessageId(),
                    envelope == null ? null : envelope.getEventType(),
                    e);
            throw e;
        }
    }

    private void dispatch(EventEnvelope<?> envelope) {
        String eventType = normalize(envelope.getEventType());
        switch (eventType) {
            case "POST_PUBLISHED" -> listener.handlePostPublishedSynchronously(
                    convert(envelope, PostPublishedEvent.class));
            case "POST_UPDATED" -> listener.handlePostUpdatedSynchronously(
                    convert(envelope, PostUpdatedEvent.class));
            case "POST_DELETED" -> listener.handlePostDeletedSynchronously(
                    convert(envelope, PostDeletedEvent.class));
            default -> throw new IllegalArgumentException(
                    "unsupported post search event type: " + eventType);
        }
    }

    private <T> T convert(EventEnvelope<?> envelope, Class<T> eventClass) {
        T event = objectMapper.convertValue(envelope.getPayload(), eventClass);
        if (event == null) {
            throw new IllegalArgumentException(
                    "post search event payload is required: " + envelope.getEventType());
        }
        return event;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
