package com.offerlab.community.incentive.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.mq.EventEnvelope;
import com.offerlab.community.interaction.api.event.CommentReportReviewedEvent;
import com.offerlab.community.interaction.api.event.CommentUnavailableEvent;
import com.offerlab.community.post.api.event.OperationCurationSelectedEvent;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.post.api.event.PostDeletedEvent;
import com.offerlab.community.post.api.event.PostReportReviewedEvent;
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
public class TrustedContributionRewardConsumer {
    private final TrustedContributionRewardListener rewardListener;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {
                    "post.published",
                    "post.deleted",
                    "interaction.answer.accepted",
                    "interaction.answer.acceptance-invalidated",
                    "interaction.content-suggestion.decided",
                    "interaction.post.freshness-changed",
                     "interaction.comment.helpful-threshold",
                     "interaction.comment.unavailable",
                    "operation.curation.selected",
                    "post.report.reviewed",
                    "interaction.comment.report.reviewed",
                    "collaboration.contribution.accepted"
            },
            groupId = "${offerlab.incentive.kafka-consumer-group:offerlab-incentive-reward-inbox}",
            containerFactory = "kafkaListenerContainerFactory",
            autoStartup = "${offerlab.incentive.kafka-consumer-enabled:true}"
    )
    public void onMessage(EventEnvelope<?> envelope, Acknowledgment ack) {
        if (envelope == null || envelope.getMessageId() == null || envelope.getMessageId().isBlank()) {
            throw new IllegalArgumentException("reward event envelope and messageId are required");
        }
        String eventType = envelope.getEventType() == null
                ? ""
                : envelope.getEventType().trim().toUpperCase(Locale.ROOT);
        try {
            switch (eventType) {
                case "POST_PUBLISHED" -> rewardListener.onPostPublished(
                        convert(envelope, PostPublishedEvent.class));
                case "POST_DELETED" -> rewardListener.onPostDeletedStrict(
                        convert(envelope, PostDeletedEvent.class));
                case "ANSWER_ACCEPTED" -> rewardListener.onAnswerAccepted(
                        requirePayload(envelope));
                case "ANSWER_ACCEPTANCE_INVALIDATED" ->
                        rewardListener.onAnswerAcceptanceInvalidatedStrict(requirePayload(envelope));
                case "CONTENT_SUGGESTION_DECIDED" -> rewardListener.onSuggestionDecided(
                        requirePayload(envelope));
                case "POST_FRESHNESS_CHANGED" -> rewardListener.onFreshnessUpdated(
                        requirePayload(envelope));
                 case "COMMENT_HELPFUL_THRESHOLD_REACHED" -> rewardListener.onCommentHelpfulThreshold(
                         requirePayload(envelope));
                 case "COMMENT_UNAVAILABLE" -> rewardListener.onCommentUnavailableStrict(
                         convert(envelope, CommentUnavailableEvent.class));
                case "OPERATION_CURATION_SELECTED" -> rewardListener.onOperationSelected(
                        convert(envelope, OperationCurationSelectedEvent.class));
                case "POST_REPORT_REVIEWED" -> rewardListener.onPostReportReviewedStrict(
                        convert(envelope, PostReportReviewedEvent.class));
                case "COMMENT_REPORT_REVIEWED" -> rewardListener.onCommentReportReviewed(
                        convert(envelope, CommentReportReviewedEvent.class));
                case "COLLABORATION_CONTRIBUTION_ACCEPTED" -> rewardListener.onCollaborationAccepted(
                        requirePayload(envelope));
                default -> throw new IllegalArgumentException("unsupported incentive reward event type: " + eventType);
            }
            ack.acknowledge();
            log.info("incentive reward event acked: messageId={} eventType={}",
                    envelope.getMessageId(), eventType);
        } catch (RuntimeException e) {
            log.error("incentive reward event failed, will retry: messageId={} eventType={}",
                    envelope.getMessageId(), eventType, e);
            throw e;
        }
    }

    private <T> T convert(EventEnvelope<?> envelope, Class<T> eventClass) {
        T event = objectMapper.convertValue(envelope.getPayload(), eventClass);
        if (event == null) {
            throw new IllegalArgumentException("reward event payload is required: " + envelope.getEventType());
        }
        return event;
    }

    private Object requirePayload(EventEnvelope<?> envelope) {
        if (envelope.getPayload() == null) {
            throw new IllegalArgumentException("reward event payload is required: " + envelope.getEventType());
        }
        return envelope.getPayload();
    }
}
