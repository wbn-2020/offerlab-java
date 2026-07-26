package com.offerlab.community.infra.mq.producer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 事件类型 → Topic 映射解析器
 * 根据事件类型推导 Kafka Topic 和 aggregateId
 */
@Slf4j
@Component
public class EventTopicResolver {

    /**
     * 事件类型映射结果
     */
    public static class TopicMapping {
        public final String topic;
        public final Long aggregateId;
        public final String eventType;
        public final boolean registered;
        public final String sourceType;
        public final String sourceId;
        public final Long actorUid;

        public TopicMapping(String topic, Long aggregateId, String eventType) {
            this(topic, aggregateId, eventType, true, topic.split("\\.")[0],
                    aggregateId == null ? null : String.valueOf(aggregateId), null);
        }

        public TopicMapping(String topic,
                            Long aggregateId,
                            String eventType,
                            boolean registered,
                            String sourceType,
                            String sourceId,
                            Long actorUid) {
            this.topic = topic;
            this.aggregateId = aggregateId;
            this.eventType = eventType;
            this.registered = registered;
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.actorUid = actorUid;
        }
    }

    /**
     * 根据事件对象推导 Topic 和 aggregateId
     * @param event 事件对象
     * @return TopicMapping
     */
    public TopicMapping resolve(Object event) {
        TopicMapping mapping = resolveMapping(event);
        Long actorUid = mapping.actorUid == null ? readActorUid(event) : mapping.actorUid;
        if (mapping.actorUid != null || actorUid == null) {
            return mapping;
        }
        return new TopicMapping(
                mapping.topic,
                mapping.aggregateId,
                mapping.eventType,
                mapping.registered,
                mapping.sourceType,
                mapping.sourceId,
                actorUid);
    }

    private TopicMapping resolveMapping(Object event) {
        String className = event.getClass().getSimpleName();
        if ("PostPublishedEvent".equals(className)) {
            return new TopicMapping("post.published", readLong(event, "getPostId"), "POST_PUBLISHED");
        }

        if ("PostUpdatedEvent".equals(className)) {
            return new TopicMapping("post.updated", readLong(event, "getPostId"), "POST_UPDATED");
        }

        if ("PostDeletedEvent".equals(className)) {
            return new TopicMapping("post.deleted", readLong(event, "getPostId"), "POST_DELETED");
        }

        if ("PostLikedEvent".equals(className)) {
            return new TopicMapping("interaction.like", readLong(event, "getPostId"), "LIKE");
        }

        if ("PostFavoritedEvent".equals(className)) {
            return new TopicMapping("interaction.favorite", readLong(event, "getPostId"), "FAVORITE");
        }

        if ("CommentCreatedEvent".equals(className)) {
            return new TopicMapping("interaction.comment", readLong(event, "getPostId"), "COMMENT_CREATED");
        }

        if ("CommentLikedEvent".equals(className)) {
            return new TopicMapping("interaction.comment.like", readLong(event, "getCommentId"), "COMMENT_LIKED");
        }

        if ("CommentQualitySignalChangedEvent".equals(className)) {
            return new TopicMapping(
                    "interaction.comment.quality-signal-changed",
                    readLong(event, "getCommentId"),
                    "COMMENT_QUALITY_SIGNAL_CHANGED");
        }

        if ("CommentHelpfulThresholdReachedEvent".equals(className)) {
            return new TopicMapping(
                    "interaction.comment.helpful-threshold",
                    readLong(event, "getCommentId"),
                    "COMMENT_HELPFUL_THRESHOLD_REACHED");
        }

        if ("CommentUnavailableEvent".equals(className)) {
            return new TopicMapping(
                    "interaction.comment.unavailable",
                    readLong(event, "getCommentId"),
                    "COMMENT_UNAVAILABLE");
        }

        if ("PostUsefulFeedbackChangedEvent".equals(className)) {
            return new TopicMapping(
                    "interaction.post.useful-feedback",
                    readLong(event, "getPostId"),
                    "POST_USEFUL_FEEDBACK_CHANGED");
        }

        if ("PostOutcomeChangedEvent".equals(className)) {
            Long outcomeId = readLong(event, "getOutcomeId");
            return new TopicMapping(
                    "interaction.post.outcome-changed",
                    outcomeId,
                    "POST_OUTCOME_CHANGED",
                    true,
                    "POST_OUTCOME",
                    String.valueOf(outcomeId),
                    readNullableLong(event, "getUid"));
        }

        if ("ContentSuggestionSubmittedEvent".equals(className)) {
            return new TopicMapping(
                    "interaction.content-suggestion.submitted",
                    readLong(event, "getPostId"),
                    "CONTENT_SUGGESTION_SUBMITTED");
        }

        if ("ContentSuggestionDecidedEvent".equals(className)) {
            return new TopicMapping(
                    "interaction.content-suggestion.decided",
                    readLong(event, "getPostId"),
                    "CONTENT_SUGGESTION_DECIDED");
        }

        if ("QuestionStateChangedEvent".equals(className)) {
            return new TopicMapping(
                    "interaction.question.state-changed",
                    readLong(event, "getPostId"),
                    "QUESTION_STATE_CHANGED");
        }

        if ("PostFreshnessChangedEvent".equals(className)) {
            return new TopicMapping(
                    "interaction.post.freshness-changed",
                    readLong(event, "getPostId"),
                    "POST_FRESHNESS_CHANGED");
        }

        if ("AnswerAcceptedEvent".equals(className)) {
            return new TopicMapping(
                    "interaction.answer.accepted",
                    readLong(event, "getPostId"),
                    "ANSWER_ACCEPTED");
        }

        if ("AnswerAcceptanceInvalidatedEvent".equals(className)) {
            return new TopicMapping(
                    "interaction.answer.acceptance-invalidated",
                    readLong(event, "getPostId"),
                    "ANSWER_ACCEPTANCE_INVALIDATED");
        }

        if ("PostReportReviewedEvent".equals(className)) {
            return new TopicMapping(
                    "post.report.reviewed",
                    readLong(event, "getPostId"),
                    "POST_REPORT_REVIEWED");
        }

        if ("CommentReportReviewedEvent".equals(className)) {
            return new TopicMapping(
                    "interaction.comment.report.reviewed",
                    readLong(event, "getCommentId"),
                    "COMMENT_REPORT_REVIEWED");
        }

        if ("CollaborationContributionAcceptedEvent".equals(className)) {
            return new TopicMapping(
                    "collaboration.contribution.accepted",
                    readLong(event, "getSourceId"),
                    "COLLABORATION_CONTRIBUTION_ACCEPTED");
        }

        if ("CollaborationNeedStateChangedEvent".equals(className)) {
            return new TopicMapping(
                    "collaboration.need.state-changed",
                    readLong(event, "getNeedId"),
                    "COLLABORATION_NEED_STATE_CHANGED");
        }

        if ("UserFollowedEvent".equals(className)) {
            return new TopicMapping("user.followed", readLong(event, "getFollowerId"), "USER_FOLLOWED");
        }

        // 未识别的事件类型，用类名小写作为 topic
        if ("UserRegisteredEvent".equals(className)) {
            return new TopicMapping("user.registered", readLong(event, "getUid"), "USER_REGISTERED");
        }

        if ("OperationCurationSelectedEvent".equals(className)) {
            return new TopicMapping("operation.curation.selected", readLong(event, "getContentId"), "OPERATION_CURATION_SELECTED");
        }

        String topic = className.replaceAll("([A-Z])", "_$1").toLowerCase().replaceFirst("^_", "");
        log.warn("Unknown event type: {}, using topic: {}", className, topic);
        return new TopicMapping(topic, 0L, className, false, className, null, readActorUid(event));
    }

    private Long readActorUid(Object event) {
        for (String methodName : new String[]{"getActorUid", "getOperatorUid", "getUserId", "getUid", "getFollowerId"}) {
            Long value = readNullableLong(event, methodName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long readLong(Object event, String methodName) {
        Long value = readNullableLong(event, methodName);
        return value == null ? 0L : value;
    }

    private Long readNullableLong(Object event, String methodName) {
        try {
            Object value = event.getClass().getMethod(methodName).invoke(event);
            return value instanceof Number number ? number.longValue() : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
