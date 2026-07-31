package com.offerlab.community.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.mq.EventEnvelope;
import com.offerlab.community.infra.mq.idempotent.IdempotentEventConsumer;
import com.offerlab.community.interaction.api.event.CommentCreatedEvent;
import com.offerlab.community.interaction.api.event.CommentLikedEvent;
import com.offerlab.community.interaction.api.event.CommentQualitySignalChangedEvent;
import com.offerlab.community.interaction.api.event.CommentReportReviewedEvent;
import com.offerlab.community.interaction.api.event.PostFavoritedEvent;
import com.offerlab.community.interaction.api.event.PostLikedEvent;
import com.offerlab.community.post.api.event.OperationCurationSelectedEvent;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.post.api.event.PostReportReviewedEvent;
import com.offerlab.community.user.api.event.UserFollowedEvent;
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
public class NotificationKafkaEventConsumer {

    private static final String CONSUMER_NAME = "notification-core";

    private final NotificationEventListener listener;
    private final ObjectMapper objectMapper;
    private final IdempotentEventConsumer idempotentConsumer;

    @KafkaListener(
            topics = {
                    "post.published",
                    "interaction.like",
                    "interaction.comment.like",
                    "interaction.comment",
                    "interaction.comment.quality-signal-changed",
                    "user.followed",
                    "interaction.favorite",
                    "operation.curation.selected",
                    "post.report.reviewed",
                    "interaction.comment.report.reviewed"
            },
            groupId = "${offerlab.notification.core-kafka-consumer-group:offerlab-notification-core}",
            containerFactory = "kafkaListenerContainerFactory",
            autoStartup = "${offerlab.notification.kafka-consumer-enabled:true}"
    )
    public void onMessage(EventEnvelope<?> envelope, Acknowledgment ack) {
        if (envelope == null) {
            log.warn("notification event skipped: empty envelope");
            ack.acknowledge();
            return;
        }
        try {
            boolean processed = idempotentConsumer.consume(
                    envelope, CONSUMER_NAME, () -> dispatch(envelope));
            ack.acknowledge();
            log.info("notification event acked: messageId={} eventType={} processed={}",
                    envelope == null ? null : envelope.getMessageId(),
                    envelope == null ? null : envelope.getEventType(),
                    processed);
        } catch (RuntimeException e) {
            log.error("notification event failed, will retry: messageId={} eventType={}",
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
            case "LIKE" -> listener.handlePostLikedSynchronously(
                    convert(envelope, PostLikedEvent.class));
            case "COMMENT_LIKED" -> listener.handleCommentLikedSynchronously(
                    convert(envelope, CommentLikedEvent.class));
            case "COMMENT_CREATED" -> listener.handleCommentCreatedSynchronously(
                    convert(envelope, CommentCreatedEvent.class));
            case "COMMENT_QUALITY_SIGNAL_CHANGED" ->
                    listener.handleCommentQualitySignalChangedSynchronously(
                            convert(envelope, CommentQualitySignalChangedEvent.class));
            case "USER_FOLLOWED" -> listener.handleUserFollowedSynchronously(
                    convert(envelope, UserFollowedEvent.class));
            case "FAVORITE" -> listener.handlePostFavoritedSynchronously(
                    convert(envelope, PostFavoritedEvent.class));
            case "OPERATION_CURATION_SELECTED" ->
                    listener.handleOperationCurationSelectedSynchronously(
                            convert(envelope, OperationCurationSelectedEvent.class));
            case "POST_REPORT_REVIEWED" -> listener.handlePostReportReviewedSynchronously(
                    convert(envelope, PostReportReviewedEvent.class));
            case "COMMENT_REPORT_REVIEWED" ->
                    listener.handleCommentReportReviewedSynchronously(
                            convert(envelope, CommentReportReviewedEvent.class));
            default -> throw new IllegalArgumentException(
                    "unsupported notification event type: " + eventType);
        }
    }

    private <T> T convert(EventEnvelope<?> envelope, Class<T> eventClass) {
        T event = objectMapper.convertValue(envelope.getPayload(), eventClass);
        if (event == null) {
            throw new IllegalArgumentException(
                    "notification event payload is required: " + envelope.getEventType());
        }
        return event;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
