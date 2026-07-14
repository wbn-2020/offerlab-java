package com.offerlab.community.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.mq.EventEnvelope;
import com.offerlab.community.interaction.api.event.AnswerAcceptedEvent;
import com.offerlab.community.interaction.api.event.ContentSuggestionDecidedEvent;
import com.offerlab.community.interaction.api.event.ContentSuggestionSubmittedEvent;
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
public class TrustedContentNotificationConsumer {

    private static final String ANSWER_ACCEPTED = "ANSWER_ACCEPTED";
    private static final String CONTENT_SUGGESTION_SUBMITTED = "CONTENT_SUGGESTION_SUBMITTED";
    private static final String CONTENT_SUGGESTION_DECIDED = "CONTENT_SUGGESTION_DECIDED";

    private final NotificationEventListener notificationEventListener;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {
                    "interaction.answer.accepted",
                    "interaction.content-suggestion.submitted",
                    "interaction.content-suggestion.decided"
            },
            groupId = "${offerlab.notification.kafka-consumer-group:offerlab-notification-trusted-content}",
            containerFactory = "kafkaListenerContainerFactory",
            autoStartup = "${offerlab.notification.kafka-consumer-enabled:true}"
    )
    public void onMessage(EventEnvelope<?> envelope, Acknowledgment ack) {
        if (envelope == null) {
            log.warn("trusted-content notification message skipped: empty envelope");
            ack.acknowledge();
            return;
        }

        try {
            String eventType = envelope.getEventType() == null
                    ? ""
                    : envelope.getEventType().trim().toUpperCase();
            switch (eventType) {
                case ANSWER_ACCEPTED -> {
                    AnswerAcceptedEvent event = objectMapper.convertValue(
                            envelope.getPayload(), AnswerAcceptedEvent.class);
                    requireComplete(event);
                    notificationEventListener.handleAnswerAcceptedSynchronously(event);
                }
                case CONTENT_SUGGESTION_SUBMITTED -> {
                    ContentSuggestionSubmittedEvent event = objectMapper.convertValue(
                            envelope.getPayload(), ContentSuggestionSubmittedEvent.class);
                    requireComplete(event);
                    notificationEventListener.handleContentSuggestionSubmittedSynchronously(event);
                }
                case CONTENT_SUGGESTION_DECIDED -> {
                    ContentSuggestionDecidedEvent event = objectMapper.convertValue(
                            envelope.getPayload(), ContentSuggestionDecidedEvent.class);
                    requireComplete(event);
                    notificationEventListener.handleContentSuggestionDecidedSynchronously(event);
                }
                default -> throw new IllegalArgumentException(
                        "unsupported trusted-content notification event type: " + eventType);
            }
            ack.acknowledge();
            log.info("trusted-content notification message acked: messageId={} eventType={}",
                    envelope.getMessageId(), envelope.getEventType());
        } catch (RuntimeException e) {
            log.error("trusted-content notification message failed, will retry: messageId={} eventType={}",
                    envelope.getMessageId(), envelope.getEventType(), e);
            throw e;
        }
    }

    private void requireComplete(AnswerAcceptedEvent event) {
        if (event == null
                || !isPositive(event.getPostId())
                || !isPositive(event.getCommentId())
                || !isPositive(event.getPostAuthorUid())
                || !isPositive(event.getCommentAuthorUid())
                || !isPositive(event.getAcceptanceId())) {
            throw incompletePayload(ANSWER_ACCEPTED);
        }
    }

    private void requireComplete(ContentSuggestionSubmittedEvent event) {
        if (event == null
                || !isPositive(event.getPostId())
                || !isPositive(event.getSuggestionId())
                || !isPositive(event.getPostAuthorUid())) {
            throw incompletePayload(CONTENT_SUGGESTION_SUBMITTED);
        }
    }

    private void requireComplete(ContentSuggestionDecidedEvent event) {
        if (event == null
                || !isPositive(event.getPostId())
                || !isPositive(event.getSuggestionId())
                || !isPositive(event.getSubmitterUid())
                || event.getDecision() == null
                || event.getDecision().isBlank()) {
            throw incompletePayload(CONTENT_SUGGESTION_DECIDED);
        }
    }

    private boolean isPositive(Long value) {
        return value != null && value > 0;
    }

    private IllegalArgumentException incompletePayload(String eventType) {
        return new IllegalArgumentException(
                "incomplete trusted-content notification payload for event type: " + eventType);
    }
}
