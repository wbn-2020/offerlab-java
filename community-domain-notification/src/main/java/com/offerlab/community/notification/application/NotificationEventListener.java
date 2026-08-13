package com.offerlab.community.notification.application;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.utils.LogMask;
import com.offerlab.community.interaction.api.DiscussionFollowFacade;
import com.offerlab.community.interaction.api.event.AnswerAcceptedEvent;
import com.offerlab.community.interaction.api.event.CommentCreatedEvent;
import com.offerlab.community.interaction.api.event.CommentLikedEvent;
import com.offerlab.community.interaction.api.event.CommentReportReviewedEvent;
import com.offerlab.community.interaction.api.event.CommentQualitySignalChangedEvent;
import com.offerlab.community.interaction.api.event.ContentSuggestionDecidedEvent;
import com.offerlab.community.interaction.api.event.ContentSuggestionSubmittedEvent;
import com.offerlab.community.interaction.api.event.ContactRequestCreatedEvent;
import com.offerlab.community.interaction.api.event.ContactRequestHandledEvent;
import com.offerlab.community.interaction.api.event.ContactRequestReportReviewedEvent;
import com.offerlab.community.interaction.api.event.PostFavoritedEvent;
import com.offerlab.community.interaction.api.event.PostLikedEvent;
import com.offerlab.community.notification.api.NotificationFacade;
import com.offerlab.community.post.api.event.OperationCurationSelectedEvent;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.post.api.event.PostReportReviewedEvent;
import com.offerlab.community.post.collaboration.api.CollaborationNeedFollowFacade;
import com.offerlab.community.post.collaboration.api.CollaborationNeedStateChangedEvent;
import com.offerlab.community.user.api.event.UserFollowedEvent;
import com.offerlab.community.user.api.UserFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final int TARGET_POST = 1;
    private static final int TARGET_COMMENT = 2;
    private static final int TARGET_USER = 3;
    private static final int TYPE_LIKE = 1;
    private static final int TYPE_COMMENT = 2;
    private static final int TYPE_FAVORITE = 3;
    private static final int TYPE_FOLLOWER = 4;
    private static final int TYPE_SYSTEM = 5;
    private static final int TYPE_MENTION = 6;
    private static final int POST_VIS_PUBLIC = 1;
    private static final int POST_STATUS_PUBLISHED = 1;
    private static final int DISCUSSION_FOLLOW_NOTIFICATION_BATCH_SIZE = 500;
    private static final int COLLABORATION_NEED_FOLLOWER_BATCH_SIZE = 100;
    private static final int COLLABORATION_NEED_SAFE_NOTE_LENGTH = 500;
    private static final String ACTION_DISCUSSION_FOLLOW_COMMENT = "discussion_follow_comment";
    private static final String ACTION_DISCUSSION_FOLLOW_FEATURED_REPLY = "discussion_follow_featured_reply";
    private static final String ACTION_DISCUSSION_FOLLOW_AUTHOR_PINNED = "discussion_follow_author_pinned";
    private static final String ACTION_DISCUSSION_FOLLOW_AUTHOR_REPLY = "discussion_follow_author_reply";
    private static final String ACTION_ANSWER_ACCEPTED = "answerAccepted";
    private static final String ACTION_CONTENT_SUGGESTION_SUBMITTED = "contentSuggestionSubmitted";
    private static final String ACTION_CONTENT_SUGGESTION_DECIDED = "contentSuggestionDecided";
    private static final String ACTION_COLLABORATION_NEED_STATE_CHANGED = "collaboration_need_state_changed";
    private static final String COLLABORATION_NEED_DEDUP_PREFIX = "collaboration_need_event:";
    private static final String ACTION_REPORT_RECEIPT = "report_receipt";
    private static final String ACTION_CONTACT_REQUEST_RECEIVED = "contact_request_received";
    private static final String ACTION_CONTACT_REQUEST_ACCEPTED = "contact_request_accepted";
    private static final String ACTION_CONTACT_REQUEST_REJECTED = "contact_request_rejected";
    private static final String CONTACT_REQUEST_INBOX_PATH = "/me/contact-requests?tab=inbox";
    private static final String CONTACT_REQUEST_OUTBOX_PATH = "/me/contact-requests?tab=outbox";
    private static final String CONTACT_REQUEST_STATUS_ACCEPTED = "ACCEPTED";
    private static final String CONTACT_REQUEST_STATUS_REJECTED = "REJECTED";
    private static final String CONTACT_REQUEST_STATUS_REPORTED = "REPORTED";
    private static final String SOURCE_POST_REPORT = "POST_REPORT";
    private static final String SOURCE_COMMENT_REPORT = "COMMENT_REPORT";
    private static final String SOURCE_CONTACT_REQUEST_REPORT = "CONTACT_REQUEST_REPORT";
    private static final String REPORT_USER_STATUS_PROCESSING = "PROCESSING";
    private static final String REPORT_USER_STATUS_ACTION_TAKEN = "ACTION_TAKEN";
    private static final String REPORT_USER_STATUS_NOT_ACCEPTED = "NOT_ACCEPTED";
    private static final String REPORT_USER_STATUS_CLOSED = "CLOSED";
    private static final Set<String> COLLABORATION_NEED_EVENT_TYPES = Set.of(
            "CLAIMED", "SUBMITTED", "REJECTED", "WITHDRAWN",
            "ACCEPTED", "COMPLETED", "CLOSED", "MERGED", "RELEASED");
    private static final Set<String> COLLABORATION_NEED_FOLLOWER_EVENT_TYPES = Set.of(
            "ACCEPTED", "COMPLETED", "MERGED");
    private static final Set<String> COLLABORATION_NEED_CLAIMANT_REQUIRED_EVENT_TYPES = Set.of(
            "CLAIMED", "SUBMITTED", "REJECTED", "WITHDRAWN", "ACCEPTED", "RELEASED");
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\p{L}\\p{N}_\\-\\u4e00-\\u9fa5]{2,32})");

    private final NotificationFacade notificationFacade;
    private final UserFacade userFacade;
    private final DiscussionFollowFacade discussionFollowFacade;
    private final CollaborationNeedFollowFacade collaborationNeedFollowFacade;
    private final NotificationRetryService retryService;
    private final SubscriptionUpdateDeliveryService subscriptionUpdateDeliveryService;

    @Value("${offerlab.kafka.enabled:true}")
    private boolean kafkaEnabled;

    @Value("${offerlab.notification.kafka-consumer-enabled:true}")
    private boolean kafkaConsumerEnabled = true;

    @Async("notificationAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostPublished(PostPublishedEvent event) {
        if (!shouldHandleDurableLocalEvent()) {
            return;
        }
        handlePostPublishedSynchronously(event);
    }

    public void handlePostPublishedSynchronously(PostPublishedEvent event) {
        if (!isPublicPublished(event)) {
            log.warn("skip post publish notifications for non-public post: postId={} visibility={} status={}",
                    event == null ? null : event.getPostId(),
                    event == null ? null : event.getVisibility(),
                    event == null ? null : event.getPostStatus());
            return;
        }
        notifyMentions(event.getAuthorId(), event.getPostId(), null,
                textOf(event.getTitle(), event.getContent()), Set.of(event.getAuthorId()));
        notifyTopicFollowers(event);
    }

    @Async("notificationAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostLiked(PostLikedEvent event) {
        if (!shouldHandleDurableLocalEvent()) {
            return;
        }
        handlePostLikedSynchronously(event);
    }

    public void handlePostLikedSynchronously(PostLikedEvent event) {
        runQuietly(() -> notificationFacade.notifyLike(
                event.getPostAuthorId(), event.getUid(), TARGET_POST, event.getPostId()),
                "post like", event.getPostAuthorId(), event.getUid(), TYPE_LIKE, TARGET_POST, event.getPostId(),
                Map.of("action", "like", "targetType", TARGET_POST, "targetId", event.getPostId()));
    }

    @Async("notificationAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentLiked(CommentLikedEvent event) {
        if (!shouldHandleDurableLocalEvent()) {
            return;
        }
        handleCommentLikedSynchronously(event);
    }

    public void handleCommentLikedSynchronously(CommentLikedEvent event) {
        runQuietly(() -> notificationFacade.notifyCommentLike(
                event.getCommentAuthorId(), event.getUid(), event.getPostId(), event.getCommentId()),
                "comment like", event.getCommentAuthorId(), event.getUid(), TYPE_LIKE, TARGET_COMMENT, event.getCommentId(),
                Map.of("action", "like", "targetType", TARGET_COMMENT, "targetId", event.getCommentId(),
                        "postId", event.getPostId(), "commentId", event.getCommentId()));
    }

    @Async("notificationAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(CommentCreatedEvent event) {
        if (!shouldHandleDurableLocalEvent()) {
            return;
        }
        handleCommentCreatedSynchronously(event);
    }

    public void handleCommentCreatedSynchronously(CommentCreatedEvent event) {
        runQuietly(() -> notificationFacade.notifyComment(
                event.getPostAuthorId(), event.getUid(), event.getPostId(), event.getCommentId()),
                "post comment", event.getPostAuthorId(), event.getUid(), TYPE_COMMENT, TARGET_COMMENT, event.getCommentId(),
                Map.of("action", "comment", "postId", event.getPostId(), "commentId", event.getCommentId()));
        Long replyToUid = event.getReplyToUid();
        if (replyToUid != null && !replyToUid.equals(event.getPostAuthorId())) {
            runQuietly(() -> notificationFacade.notifyComment(
                    replyToUid, event.getUid(), event.getPostId(), event.getCommentId()),
                    "reply comment", replyToUid, event.getUid(), TYPE_COMMENT, TARGET_COMMENT, event.getCommentId(),
                    Map.of("action", "comment", "postId", event.getPostId(), "commentId", event.getCommentId()));
        }
        Set<Long> excluded = new HashSet<>();
        excluded.add(event.getUid());
        excluded.add(event.getPostAuthorId());
        if (replyToUid != null) {
            excluded.add(replyToUid);
        }
        excluded.addAll(notifyMentions(event.getUid(), event.getPostId(), event.getCommentId(), event.getContent(), excluded));
        notifyDiscussionFollowers(event, excluded);
    }

    @Async("notificationAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentQualitySignalChanged(CommentQualitySignalChangedEvent event) {
        if (!shouldHandleDurableLocalEvent()) {
            return;
        }
        handleCommentQualitySignalChangedSynchronously(event);
    }

    public void handleCommentQualitySignalChangedSynchronously(CommentQualitySignalChangedEvent event) {
        String action = actionForQualitySignal(event);
        if (action == null) {
            return;
        }
        Set<Long> excluded = new HashSet<>();
        addExcludedUid(excluded, event.getOperatorUid());
        addExcludedUid(excluded, event.getCommentAuthorUid());
        addExcludedUid(excluded, event.getPostAuthorUid());
        notifyDiscussionFollowers(event, action, excluded);
    }

    @Async("notificationAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnswerAccepted(AnswerAcceptedEvent event) {
        if (!shouldHandleDurableLocalEvent()) {
            return;
        }
        if (!isValidAnswerAccepted(event)) {
            return;
        }
        Map<String, Object> content = answerAcceptedContent(event);
        runQuietly(() -> handleAnswerAcceptedSynchronously(event, content),
                "answer accepted", event.getCommentAuthorUid(), event.getPostAuthorUid(),
                TYPE_COMMENT, TARGET_COMMENT, event.getCommentId(), content);
    }

    public void handleAnswerAcceptedSynchronously(AnswerAcceptedEvent event) {
        if (!isValidAnswerAccepted(event)) {
            return;
        }
        handleAnswerAcceptedSynchronously(event, answerAcceptedContent(event));
    }

    private void handleAnswerAcceptedSynchronously(
            AnswerAcceptedEvent event, Map<String, Object> content) {
        notificationFacade.notifyAnswerAccepted(
                event.getCommentAuthorUid(), event.getPostAuthorUid(),
                event.getPostId(), event.getCommentId(), content);
    }

    @Async("notificationAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContentSuggestionSubmitted(ContentSuggestionSubmittedEvent event) {
        if (!shouldHandleDurableLocalEvent()) {
            return;
        }
        if (!isValidContentSuggestionSubmitted(event)) {
            return;
        }
        Map<String, Object> content = contentSuggestionSubmittedContent(event);
        runQuietly(() -> handleContentSuggestionSubmittedSynchronously(event, content),
                "content suggestion submitted", event.getPostAuthorUid(), 0L,
                TYPE_SYSTEM, TARGET_POST, event.getPostId(), content);
    }

    public void handleContentSuggestionSubmittedSynchronously(ContentSuggestionSubmittedEvent event) {
        if (!isValidContentSuggestionSubmitted(event)) {
            return;
        }
        handleContentSuggestionSubmittedSynchronously(
                event, contentSuggestionSubmittedContent(event));
    }

    private void handleContentSuggestionSubmittedSynchronously(
            ContentSuggestionSubmittedEvent event, Map<String, Object> content) {
        notificationFacade.notifySystem(
                event.getPostAuthorUid(), (long) TARGET_POST, event.getPostId(), content);
    }

    @Async("notificationAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContentSuggestionDecided(ContentSuggestionDecidedEvent event) {
        if (!shouldHandleDurableLocalEvent()) {
            return;
        }
        if (!isValidContentSuggestionDecided(event)) {
            return;
        }
        Map<String, Object> content = contentSuggestionDecidedContent(event);
        runQuietly(() -> handleContentSuggestionDecidedSynchronously(event, content),
                "content suggestion decided", event.getSubmitterUid(), 0L,
                TYPE_SYSTEM, TARGET_POST, event.getPostId(), content);
    }

    public void handleContentSuggestionDecidedSynchronously(ContentSuggestionDecidedEvent event) {
        if (!isValidContentSuggestionDecided(event)) {
            return;
        }
        handleContentSuggestionDecidedSynchronously(
                event, contentSuggestionDecidedContent(event));
    }

    private void handleContentSuggestionDecidedSynchronously(
            ContentSuggestionDecidedEvent event, Map<String, Object> content) {
        notificationFacade.notifySystem(
                event.getSubmitterUid(), (long) TARGET_POST, event.getPostId(), content);
    }

    @Async("notificationAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCollaborationNeedStateChanged(CollaborationNeedStateChangedEvent event) {
        if (!shouldHandleDurableLocalEvent()) {
            return;
        }
        try {
            handleCollaborationNeedStateChangedSynchronously(event);
        } catch (RuntimeException e) {
            log.warn("collaboration need state notification failed: eventId={} needId={}",
                    event == null ? null : event.getEventId(),
                    event == null ? null : event.getNeedId(),
                    e);
        }
    }

    public void handleCollaborationNeedStateChangedSynchronously(
            CollaborationNeedStateChangedEvent event) {
        String eventType = requireCompleteCollaborationNeedStateChangedEvent(event);
        Set<Long> directRecipients = collaborationNeedDirectRecipients(event, eventType);
        Map<String, Object> directContent = collaborationNeedStateContent(event, eventType, true);
        for (Long receiverUid : directRecipients) {
            notifyCollaborationNeedStateReceiver(receiverUid, event, eventType, directContent);
        }
        if (!COLLABORATION_NEED_FOLLOWER_EVENT_TYPES.contains(eventType)) {
            return;
        }
        Set<Long> excluded = new HashSet<>(directRecipients);
        addExcludedUid(excluded, event.getActorUid());
        notifyCollaborationNeedFollowers(event, eventType, excluded);
    }

    @Async("notificationAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserFollowed(UserFollowedEvent event) {
        if (!shouldHandleDurableLocalEvent()) {
            return;
        }
        handleUserFollowedSynchronously(event);
    }

    public void handleUserFollowedSynchronously(UserFollowedEvent event) {
        runQuietly(() -> notificationFacade.notifyFollower(
                event.getFolloweeId(), event.getFollowerId()),
                "user follow", event.getFolloweeId(), event.getFollowerId(), TYPE_FOLLOWER, TARGET_USER, event.getFollowerId(),
                Map.of("action", "follow", "userId", event.getFollowerId()));
    }

    @Async("notificationAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostFavorited(PostFavoritedEvent event) {
        if (!shouldHandleDurableLocalEvent()) {
            return;
        }
        handlePostFavoritedSynchronously(event);
    }

    public void handlePostFavoritedSynchronously(PostFavoritedEvent event) {
        runQuietly(() -> notificationFacade.notifyFavorite(
                event.getPostAuthorId(), event.getUid(), event.getPostId()),
                "post favorite", event.getPostAuthorId(), event.getUid(), TYPE_FAVORITE, TARGET_POST, event.getPostId(),
                Map.of("action", "favorite", "postId", event.getPostId()));
    }

    @Async("notificationAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOperationCurationSelected(OperationCurationSelectedEvent event) {
        if (!shouldHandleDurableLocalEvent()) {
            return;
        }
        handleOperationCurationSelectedSynchronously(event);
    }

    public void handleOperationCurationSelectedSynchronously(OperationCurationSelectedEvent event) {
        String skippedReason = operationCurationSkippedReason(event);
        if (skippedReason != null) {
            log.debug("operation curation notification skipped: reason={} authorUid={} contentId={}",
                    skippedReason, event == null ? null : event.getAuthorUid(), event == null ? null : event.getContentId());
            return;
        }
        Map<String, Object> content = operationCurationSelectedContent(event);
        runQuietly(() -> notificationFacade.notifySystem(event.getAuthorUid(), (long) TARGET_POST, event.getContentId(), content),
                "operation curation selected", event.getAuthorUid(), 0L, TYPE_SYSTEM, TARGET_POST, event.getContentId(), content);
    }

    @Async("notificationAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostReportReviewed(PostReportReviewedEvent event) {
        if (!shouldHandleDurableLocalEvent()) {
            return;
        }
        handlePostReportReviewedSynchronously(event);
    }

    public void handlePostReportReviewedSynchronously(PostReportReviewedEvent event) {
        if (event == null || event.getReporterUid() == null || event.getReportId() == null) {
            return;
        }
        Map<String, Object> content = reportReceiptContent(SOURCE_POST_REPORT, event.getReportId(),
                event.getUserStatus(), event.getTargetPath());
        runQuietly(() -> notificationFacade.notifyReportReceipt(event.getReporterUid(), SOURCE_POST_REPORT,
                        event.getReportId(), event.getUserStatus(), event.getTargetPath()),
                "post report receipt", event.getReporterUid(), 0L, TYPE_SYSTEM, null, event.getReportId(), content);
    }

    @Async("notificationAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentReportReviewed(CommentReportReviewedEvent event) {
        if (!shouldHandleDurableLocalEvent()) {
            return;
        }
        handleCommentReportReviewedSynchronously(event);
    }

    public void handleCommentReportReviewedSynchronously(CommentReportReviewedEvent event) {
        if (event == null || event.getReporterUid() == null || event.getReportId() == null) {
            return;
        }
        Map<String, Object> content = reportReceiptContent(SOURCE_COMMENT_REPORT, event.getReportId(),
                event.getUserStatus(), event.getTargetPath());
        runQuietly(() -> notificationFacade.notifyReportReceipt(event.getReporterUid(), SOURCE_COMMENT_REPORT,
                        event.getReportId(), event.getUserStatus(), event.getTargetPath()),
                "comment report receipt", event.getReporterUid(), 0L, TYPE_SYSTEM, null, event.getReportId(), content);
    }

    @Async("notificationAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContactRequestCreated(ContactRequestCreatedEvent event) {
        if (event == null || event.getRequestId() == null
                || event.getRequesterUid() == null || event.getReceiverUid() == null) {
            return;
        }
        Map<String, Object> content = contactRequestContent(
                ACTION_CONTACT_REQUEST_RECEIVED, event.getRequestId(), CONTACT_REQUEST_INBOX_PATH);
        runQuietly(() -> notificationFacade.notifyContactRequestReceived(
                        event.getReceiverUid(), event.getRequesterUid(), event.getRequestId()),
                "contact request received", event.getReceiverUid(), event.getRequesterUid(),
                TYPE_SYSTEM, null, event.getRequestId(), content);
    }

    @Async("notificationAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContactRequestHandled(ContactRequestHandledEvent event) {
        if (event == null || event.getRequestId() == null
                || event.getRequesterUid() == null || event.getReceiverUid() == null) {
            return;
        }
        String status = event.getRequestStatus();
        if (CONTACT_REQUEST_STATUS_ACCEPTED.equals(status)) {
            Map<String, Object> content = contactRequestContent(
                    ACTION_CONTACT_REQUEST_ACCEPTED, event.getRequestId(), CONTACT_REQUEST_OUTBOX_PATH);
            runQuietly(() -> notificationFacade.notifyContactRequestAccepted(
                            event.getRequesterUid(), event.getReceiverUid(), event.getRequestId()),
                    "contact request accepted", event.getRequesterUid(), event.getReceiverUid(),
                    TYPE_SYSTEM, null, event.getRequestId(), content);
        } else if (CONTACT_REQUEST_STATUS_REJECTED.equals(status)) {
            Map<String, Object> content = contactRequestContent(
                    ACTION_CONTACT_REQUEST_REJECTED, event.getRequestId(), CONTACT_REQUEST_OUTBOX_PATH);
            runQuietly(() -> notificationFacade.notifyContactRequestRejected(
                            event.getRequesterUid(), event.getReceiverUid(), event.getRequestId()),
                    "contact request rejected", event.getRequesterUid(), event.getReceiverUid(),
                    TYPE_SYSTEM, null, event.getRequestId(), content);
        } else if (CONTACT_REQUEST_STATUS_REPORTED.equals(status)) {
            Long reportId = event.getReportId() == null ? event.getRequestId() : event.getReportId();
            Map<String, Object> content = contactRequestReportReceiptContent(reportId);
            runQuietly(() -> notificationFacade.notifySystem(event.getReceiverUid(), null, reportId, content),
                    "contact request report receipt", event.getReceiverUid(), 0L,
                    TYPE_SYSTEM, null, reportId, content);
        }
    }

    @Async("notificationAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContactRequestReportReviewed(ContactRequestReportReviewedEvent event) {
        if (event == null || event.getReportId() == null || event.getReporterUid() == null) {
            return;
        }
        String targetPath = event.getTargetPath() == null ? CONTACT_REQUEST_INBOX_PATH : event.getTargetPath();
        Map<String, Object> content = reportReceiptContent(SOURCE_CONTACT_REQUEST_REPORT, event.getReportId(),
                event.getUserStatus(), targetPath);
        runQuietly(() -> notificationFacade.notifyReportReceipt(event.getReporterUid(), SOURCE_CONTACT_REQUEST_REPORT,
                        event.getReportId(), event.getUserStatus(), targetPath),
                "contact request report reviewed", event.getReporterUid(), 0L,
                 TYPE_SYSTEM, null, event.getReportId(), content);
    }

    void setKafkaEnabled(boolean kafkaEnabled) {
        this.kafkaEnabled = kafkaEnabled;
    }

    void setKafkaConsumerEnabled(boolean kafkaConsumerEnabled) {
        this.kafkaConsumerEnabled = kafkaConsumerEnabled;
    }

    private boolean shouldHandleDurableLocalEvent() {
        return !kafkaEnabled || !kafkaConsumerEnabled;
    }

    private boolean runQuietly(Runnable runnable, String scene, Long receiverUid, Long senderUid,
                               Integer notifType, Integer targetType, Long targetId, Map<String, Object> content) {
        try {
            runnable.run();
            return true;
        } catch (Exception e) {
            String dedupKey = NotificationDedupKey.of(
                    receiverUid, senderUid, notifType, targetType, targetId, content);
            log.warn("create notification failed, scene={} dedupKey={}: {}",
                    scene, LogMask.key(dedupKey), e.getMessage());
            if (!retryService.enqueue(
                    scene, receiverUid, senderUid, notifType, targetType, targetId, content, e)) {
                throw new IllegalStateException(
                        "notification write failed and retry task was not persisted", e);
            }
            return false;
        }
    }

    private Map<String, Object> answerAcceptedContent(AnswerAcceptedEvent event) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("action", ACTION_ANSWER_ACCEPTED);
        content.put("postId", event.getPostId());
        content.put("commentId", event.getCommentId());
        content.put("acceptanceId", event.getAcceptanceId());
        content.put("targetPath", trustedContentTargetPath(
                event.getPostId(), ACTION_ANSWER_ACCEPTED, "#comment-" + event.getCommentId()));
        content.put("dedupKey", ACTION_ANSWER_ACCEPTED + ":" + event.getPostId() + ":"
                + event.getCommentId() + ":" + event.getAcceptanceId());
        return content;
    }

    private Map<String, Object> contentSuggestionSubmittedContent(ContentSuggestionSubmittedEvent event) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("action", ACTION_CONTENT_SUGGESTION_SUBMITTED);
        content.put("postId", event.getPostId());
        content.put("suggestionId", event.getSuggestionId());
        content.put("targetPath", trustedContentTargetPath(
                event.getPostId(), ACTION_CONTENT_SUGGESTION_SUBMITTED,
                "#content-suggestion-" + event.getSuggestionId()));
        content.put("dedupKey", ACTION_CONTENT_SUGGESTION_SUBMITTED + ":" + event.getSuggestionId());
        return content;
    }

    private Map<String, Object> contentSuggestionDecidedContent(ContentSuggestionDecidedEvent event) {
        String decision = String.valueOf(event.getDecision());
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("action", ACTION_CONTENT_SUGGESTION_DECIDED);
        content.put("postId", event.getPostId());
        content.put("suggestionId", event.getSuggestionId());
        content.put("decision", decision);
        content.put("targetPath", trustedContentTargetPath(
                event.getPostId(), ACTION_CONTENT_SUGGESTION_DECIDED,
                "#content-suggestion-" + event.getSuggestionId()));
        content.put("dedupKey", ACTION_CONTENT_SUGGESTION_DECIDED + ":" + event.getSuggestionId() + ":" + decision);
        return content;
    }

    private String trustedContentTargetPath(Long postId, String action, String fragment) {
        return "/post/" + postId + "?notification=" + action + fragment;
    }

    private boolean isPositive(Long value) {
        return value != null && value > 0;
    }

    private boolean isValidAnswerAccepted(AnswerAcceptedEvent event) {
        return event != null
                && isPositive(event.getPostId())
                && isPositive(event.getCommentId())
                && isPositive(event.getPostAuthorUid())
                && isPositive(event.getCommentAuthorUid())
                && isPositive(event.getAcceptanceId());
    }

    private boolean isValidContentSuggestionSubmitted(ContentSuggestionSubmittedEvent event) {
        return event != null
                && isPositive(event.getPostId())
                && isPositive(event.getSuggestionId())
                && isPositive(event.getPostAuthorUid());
    }

    private boolean isValidContentSuggestionDecided(ContentSuggestionDecidedEvent event) {
        return event != null
                && isPositive(event.getPostId())
                && isPositive(event.getSuggestionId())
                && isPositive(event.getSubmitterUid())
                && event.getDecision() != null
                && !String.valueOf(event.getDecision()).isBlank();
    }

    private String requireCompleteCollaborationNeedStateChangedEvent(
            CollaborationNeedStateChangedEvent event) {
        if (event == null) {
            throw incompleteCollaborationNeedEvent("event");
        }
        if (!isPositive(event.getEventId())) {
            throw incompleteCollaborationNeedEvent("eventId");
        }
        if (!isPositive(event.getNeedId())) {
            throw incompleteCollaborationNeedEvent("needId");
        }
        if (!isPositive(event.getActorUid())) {
            throw incompleteCollaborationNeedEvent("actorUid");
        }
        if (!isPositive(event.getCreatorUid())) {
            throw incompleteCollaborationNeedEvent("creatorUid");
        }
        if (event.getDomain() == null || event.getDomain() < 1 || event.getDomain() > 5) {
            throw incompleteCollaborationNeedEvent("domain");
        }
        if (!isPositive(event.getOccurredAt())) {
            throw incompleteCollaborationNeedEvent("occurredAt");
        }
        if (isBlank(event.getDedupKey())) {
            throw incompleteCollaborationNeedEvent("dedupKey");
        }
        if (isBlank(event.getFromStatus())) {
            throw incompleteCollaborationNeedEvent("fromStatus");
        }
        if (isBlank(event.getToStatus())) {
            throw incompleteCollaborationNeedEvent("toStatus");
        }
        String eventType = normalizeCollaborationNeedValue(event.getEventType());
        if (!COLLABORATION_NEED_EVENT_TYPES.contains(eventType)) {
            throw incompleteCollaborationNeedEvent("eventType");
        }
        if (COLLABORATION_NEED_CLAIMANT_REQUIRED_EVENT_TYPES.contains(eventType)
                && !isPositive(event.getClaimantUid())) {
            throw incompleteCollaborationNeedEvent("claimantUid");
        }
        if ("MERGED".equals(eventType) && !isPositive(event.getTargetNeedId())) {
            throw incompleteCollaborationNeedEvent("targetNeedId");
        }
        if (event.getTargetId() != null && !isPositive(event.getTargetId())) {
            throw incompleteCollaborationNeedEvent("targetId");
        }
        return eventType;
    }

    private Set<Long> collaborationNeedDirectRecipients(
            CollaborationNeedStateChangedEvent event, String eventType) {
        Set<Long> recipients = new LinkedHashSet<>();
        switch (eventType) {
            case "CLAIMED", "SUBMITTED", "WITHDRAWN", "RELEASED" ->
                    addCollaborationNeedRecipient(recipients, event.getCreatorUid(), event.getActorUid());
            case "REJECTED", "ACCEPTED", "COMPLETED", "CLOSED" -> {
                addCollaborationNeedRecipient(recipients, event.getClaimantUid(), event.getActorUid());
                addCollaborationNeedRecipient(recipients, event.getCreatorUid(), event.getActorUid());
            }
            case "MERGED" -> {
                addCollaborationNeedRecipient(recipients, event.getCreatorUid(), event.getActorUid());
                addCollaborationNeedRecipient(recipients, event.getClaimantUid(), event.getActorUid());
            }
            default -> {
                // Validated before dispatch.
            }
        }
        return recipients;
    }

    private void addCollaborationNeedRecipient(Set<Long> recipients, Long uid, Long actorUid) {
        if (recipients != null && isPositive(uid) && !uid.equals(actorUid)) {
            recipients.add(uid);
        }
    }

    private void notifyCollaborationNeedFollowers(
            CollaborationNeedStateChangedEvent event, String eventType, Set<Long> excluded) {
        long cursor = 0L;
        Set<Long> visitedCursors = new HashSet<>();
        visitedCursors.add(cursor);
        Set<Long> seenReceivers = new HashSet<>(excluded);
        Map<String, Object> publicContent = collaborationNeedStateContent(event, eventType, false);
        while (true) {
            PageResult<Long> page = collaborationNeedFollowFacade.listActiveFollowerUids(
                    event.getNeedId(), cursor, COLLABORATION_NEED_FOLLOWER_BATCH_SIZE);
            List<SubscriptionUpdateDeliveryCommand> commands = new ArrayList<>();
            for (Long receiverUid : safeItems(page)) {
                if (!isPositive(receiverUid) || !seenReceivers.add(receiverUid)) {
                    continue;
                }
                commands.add(collaborationNeedFollowerCommand(
                        receiverUid, event, eventType, publicContent));
            }
            deliverFollowerUpdates(commands);
            if (page == null || !Boolean.TRUE.equals(page.getHasMore())) {
                return;
            }
            Long nextCursor = parseCollaborationNeedFollowerCursor(page.getNextCursor());
            if (nextCursor == null || !visitedCursors.add(nextCursor)) {
                log.warn("collaboration need follower cursor did not advance: eventId={} needId={} cursor={} nextCursor={}",
                        event.getEventId(), event.getNeedId(), cursor, page.getNextCursor());
                return;
            }
            cursor = nextCursor;
        }
    }

    private Long parseCollaborationNeedFollowerCursor(String nextCursor) {
        if (nextCursor == null || nextCursor.isBlank()) {
            return null;
        }
        try {
            long cursor = Long.parseLong(nextCursor.trim());
            return cursor >= 0 ? cursor : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void notifyCollaborationNeedStateReceiver(
            Long receiverUid,
            CollaborationNeedStateChangedEvent event,
            String eventType,
            Map<String, Object> content) {
        runQuietly(
                () -> notificationFacade.notifySystem(receiverUid, null, event.getNeedId(), content),
                ACTION_COLLABORATION_NEED_STATE_CHANGED + ":" + eventType,
                receiverUid,
                0L,
                TYPE_SYSTEM,
                null,
                event.getNeedId(),
                content);
    }

    private Map<String, Object> collaborationNeedStateContent(
            CollaborationNeedStateChangedEvent event, String eventType, boolean includePrivateNote) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("action", ACTION_COLLABORATION_NEED_STATE_CHANGED);
        content.put("needId", event.getNeedId());
        content.put("eventType", eventType);
        content.put("status", normalizeCollaborationNeedValue(event.getToStatus()));
        if ("MERGED".equals(eventType)) {
            content.put("targetNeedId", event.getTargetNeedId());
        }
        content.put("targetPath", collaborationNeedTargetPath(event, eventType));
        content.put("dedupKey", COLLABORATION_NEED_DEDUP_PREFIX + event.getEventId());
        if (includePrivateNote) {
            String note = safeCollaborationNeedNote(event.getNote());
            if (note != null) {
                content.put("reasonText", note);
            }
        }
        return content;
    }

    private String collaborationNeedTargetPath(
            CollaborationNeedStateChangedEvent event, String eventType) {
        Long targetNeedId = "MERGED".equals(eventType)
                ? event.getTargetNeedId()
                : event.getNeedId();
        return "/collaboration/needs/" + targetNeedId;
    }

    private String safeCollaborationNeedNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String value = note.trim();
        return value.length() <= COLLABORATION_NEED_SAFE_NOTE_LENGTH
                ? value
                : value.substring(0, COLLABORATION_NEED_SAFE_NOTE_LENGTH);
    }

    private String normalizeCollaborationNeedValue(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private IllegalArgumentException incompleteCollaborationNeedEvent(String field) {
        return new IllegalArgumentException(
                "incomplete collaboration need state-changed event: " + field);
    }

    private Set<Long> notifyMentions(Long senderUid, Long postId, Long commentId, String text, Set<Long> excludedUids) {
        Set<String> names = extractMentionNames(text);
        if (names.isEmpty()) {
            return Set.of();
        }
        Map<String, Long> matched;
        try {
            matched = userFacade.findUserIdsByNicknames(names);
        } catch (Exception e) {
            log.warn("resolve notification mentions failed: {}", e.getMessage());
            return Set.of();
        }
        Set<Long> notifiedUids = new HashSet<>();
        for (Long receiverUid : matched.values()) {
            if (receiverUid != null && (excludedUids == null || !excludedUids.contains(receiverUid))) {
                Map<String, Object> content = commentId == null
                        ? Map.of("action", "mention", "postId", postId)
                        : Map.of("action", "mention", "postId", postId, "commentId", commentId);
                runQuietly(() -> notificationFacade.notifyMention(receiverUid, senderUid, postId, commentId),
                        "user mention", receiverUid, senderUid, TYPE_MENTION,
                        commentId == null ? TARGET_POST : TARGET_COMMENT,
                        commentId == null ? postId : commentId,
                        content);
                notifiedUids.add(receiverUid);
            }
        }
        return notifiedUids;
    }

    private void notifyDiscussionFollowers(CommentCreatedEvent event, Set<Long> excluded) {
        if (event == null || event.getPostId() == null || event.getCommentId() == null) {
            return;
        }
        String action = isAuthorReply(event) ? ACTION_DISCUSSION_FOLLOW_AUTHOR_REPLY : ACTION_DISCUSSION_FOLLOW_COMMENT;
        String cursor = null;
        int fanoutCount = 0;
        do {
            PageResult<Long> page;
            try {
                page = discussionFollowFacade.followerUidsForNotification(
                        event.getPostId(), excluded, cursor, DISCUSSION_FOLLOW_NOTIFICATION_BATCH_SIZE);
            } catch (Exception e) {
                log.warn("load discussion followers failed: {}", e.getMessage());
                return;
            }
            List<SubscriptionUpdateDeliveryCommand> commands = new ArrayList<>();
            for (Long receiverUid : safeItems(page)) {
                if (receiverUid == null || receiverUid <= 0 || excluded.contains(receiverUid)) {
                    continue;
                }
                Map<String, Object> content = discussionFollowContent(action, event.getPostId(), event.getCommentId());
                commands.add(discussionFollowerCommand(
                        receiverUid,
                        event.getUid(),
                        event.getPostId(),
                        event.getCommentId(),
                        action,
                        "DISCUSSION_COMMENT_CREATED",
                        "discussion_comment_created:" + event.getCommentId(),
                        content,
                        event.getTimestamp()));
            }
            for (SubscriptionUpdateDeliveryResult result : deliverFollowerUpdates(commands)) {
                if (result.delivered()) {
                    Long receiverUid = result.command().receiverUid();
                    markDiscussionFollowerNotified(event, receiverUid);
                }
            }
            fanoutCount += commands.size();
            cursor = page == null ? null : page.getNextCursor();
        } while (cursor != null);
    }

    private void notifyDiscussionFollowers(CommentQualitySignalChangedEvent event, String action, Set<Long> excluded) {
        if (event == null || event.getPostId() == null || event.getCommentId() == null || event.getOperatorUid() == null) {
            return;
        }
        String cursor = null;
        int fanoutCount = 0;
        do {
            PageResult<Long> page;
            try {
                page = discussionFollowFacade.followerUidsForNotification(
                        event.getPostId(), excluded, cursor, DISCUSSION_FOLLOW_NOTIFICATION_BATCH_SIZE);
            } catch (Exception e) {
                log.warn("load discussion followers for quality signal failed: {}", e.getMessage());
                return;
            }
            List<SubscriptionUpdateDeliveryCommand> commands = new ArrayList<>();
            for (Long receiverUid : safeItems(page)) {
                if (receiverUid == null || receiverUid <= 0 || excluded.contains(receiverUid)) {
                    continue;
                }
                Map<String, Object> content = discussionFollowContent(action, event.getPostId(), event.getCommentId());
                commands.add(discussionFollowerCommand(
                        receiverUid,
                        event.getOperatorUid(),
                        event.getPostId(),
                        event.getCommentId(),
                        action,
                        "DISCUSSION_QUALITY_" + action.toUpperCase(Locale.ROOT),
                        "discussion_quality:" + action + ":" + event.getCommentId(),
                        content,
                        event.getTimestamp()));
            }
            for (SubscriptionUpdateDeliveryResult result : deliverFollowerUpdates(commands)) {
                if (result.delivered()) {
                    markDiscussionFollowerNotified(
                            event.getPostId(), result.command().receiverUid(), event.getCommentId());
                }
            }
            fanoutCount += commands.size();
            cursor = page == null ? null : page.getNextCursor();
        } while (cursor != null);
    }

    private List<Long> safeItems(PageResult<Long> page) {
        return page == null || page.getItems() == null ? List.of() : page.getItems();
    }

    private void markDiscussionFollowerNotified(CommentCreatedEvent event, Long receiverUid) {
        markDiscussionFollowerNotified(event.getPostId(), receiverUid, event.getCommentId());
    }

    private void markDiscussionFollowerNotified(Long postId, Long receiverUid, Long commentId) {
        try {
            discussionFollowFacade.markNotified(postId, receiverUid, commentId);
        } catch (Exception e) {
            log.warn("mark discussion follower notified failed: {}", e.getMessage());
        }
    }

    private String actionForQualitySignal(CommentQualitySignalChangedEvent event) {
        if (event == null || !Boolean.TRUE.equals(event.getActive())) {
            return null;
        }
        String signalType = normalizeSignalType(event.getSignalType());
        return switch (signalType) {
            case "FEATURED" -> ACTION_DISCUSSION_FOLLOW_FEATURED_REPLY;
            case "AUTHOR_PINNED" -> ACTION_DISCUSSION_FOLLOW_AUTHOR_PINNED;
            case "AUTHOR_REPLY" -> ACTION_DISCUSSION_FOLLOW_AUTHOR_REPLY;
            default -> null;
        };
    }

    private String normalizeSignalType(String signalType) {
        if (signalType == null || signalType.isBlank()) {
            return "";
        }
        return signalType.trim().replace('-', '_').toUpperCase();
    }

    private boolean isAuthorReply(CommentCreatedEvent event) {
        return event != null
                && event.getUid() != null
                && event.getPostAuthorId() != null
                && event.getUid().equals(event.getPostAuthorId());
    }

    private Map<String, Object> discussionFollowContent(String action, Long postId, Long commentId) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("action", action);
        content.put("postId", postId);
        content.put("commentId", commentId);
        content.put("targetPath", "/post/" + postId + "#comments");
        String message = discussionFollowMessage(action);
        if (message != null) {
            content.put("message", message);
        }
        return content;
    }

    private String discussionFollowMessage(String action) {
        return switch (action) {
            case ACTION_DISCUSSION_FOLLOW_FEATURED_REPLY -> "A followed discussion has a featured reply.";
            case ACTION_DISCUSSION_FOLLOW_AUTHOR_PINNED -> "The author pinned an important reply.";
            case ACTION_DISCUSSION_FOLLOW_AUTHOR_REPLY -> "The author added a new reply.";
            default -> null;
        };
    }

    private void addExcludedUid(Set<Long> excluded, Long uid) {
        if (excluded != null && uid != null && uid > 0) {
            excluded.add(uid);
        }
    }

    private List<SubscriptionUpdateDeliveryResult> deliverFollowerUpdates(
            List<SubscriptionUpdateDeliveryCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        if (subscriptionUpdateDeliveryService == null) {
            throw new IllegalStateException("subscription update delivery service is unavailable");
        }
        List<SubscriptionUpdateDeliveryResult> results =
                subscriptionUpdateDeliveryService.deliverForSource(commands);
        for (SubscriptionUpdateDeliveryResult result : results) {
            if (result.retryable()) {
                SubscriptionUpdateDeliveryCommand command = result.command();
                if (retryService == null || !retryService.enqueueSubscriptionUpdateDelivery(
                        command, result.deliveryMode(), result.failure())) {
                    throw new IllegalStateException(
                            "subscription update delivery failed and retry task was not persisted",
                            result.failure());
                }
                continue;
            }
        }
        return results;
    }

    private SubscriptionUpdateDeliveryCommand collaborationNeedFollowerCommand(
            Long receiverUid,
            CollaborationNeedStateChangedEvent event,
            String eventType,
            Map<String, Object> content) {
        Long resourceId = "MERGED".equals(eventType) ? event.getTargetNeedId() : event.getNeedId();
        Map<String, Object> payload = new LinkedHashMap<>(content);
        payload.put("summary", "你关注的共建需求有公开进展。");
        return new SubscriptionUpdateDeliveryCommand(
                receiverUid,
                "NEED",
                event.getNeedId(),
                "NEED",
                resourceId,
                "NEED_" + eventType,
                COLLABORATION_NEED_DEDUP_PREFIX + event.getEventId(),
                event.getActorUid(),
                SubscriptionUpdateNotificationKind.SYSTEM,
                null,
                event.getNeedId(),
                payload,
                eventOccurredAt(event.getOccurredAt()));
    }

    private SubscriptionUpdateDeliveryCommand discussionFollowerCommand(
            Long receiverUid,
            Long actorUid,
            Long postId,
            Long commentId,
            String action,
            String eventType,
            String eventKey,
            Map<String, Object> content,
            Long timestamp) {
        Map<String, Object> payload = new LinkedHashMap<>(content);
        payload.put("summary", discussionFollowMessage(action) == null
                ? "你关注的讨论有新的公开回应。"
                : discussionFollowMessage(action));
        SubscriptionUpdateNotificationKind kind =
                ACTION_DISCUSSION_FOLLOW_COMMENT.equals(action)
                        ? SubscriptionUpdateNotificationKind.DISCUSSION_FOLLOW_COMMENT
                        : SubscriptionUpdateNotificationKind.DISCUSSION_FOLLOW_QUALITY_COMMENT;
        return new SubscriptionUpdateDeliveryCommand(
                receiverUid,
                "DISCUSSION",
                postId,
                "POST",
                postId,
                eventType,
                eventKey,
                actorUid,
                kind,
                TARGET_COMMENT,
                commentId,
                payload,
                eventOccurredAt(timestamp));
    }

    private SubscriptionUpdateDeliveryCommand topicFollowerCommand(
            Long receiverUid,
            PostPublishedEvent event,
            PostPublishedEvent.TopicNotificationTarget topic,
            Map<String, Object> content) {
        Map<String, Object> payload = new LinkedHashMap<>(content);
        payload.put("topicId", topic.getTopicId());
        payload.put("topicSlug", topic.getTopicSlug());
        payload.put("topicName", topic.getTopicName());
        payload.put("targetPath", "/post/" + event.getPostId());
        payload.put("summary", "你关注的话题有新的公开内容。");
        return new SubscriptionUpdateDeliveryCommand(
                receiverUid,
                "TOPIC",
                topic.getTopicId(),
                "POST",
                event.getPostId(),
                "TOPIC_POST_PUBLISHED",
                "topic_post_published:" + event.getPostId(),
                event.getAuthorId(),
                SubscriptionUpdateNotificationKind.SYSTEM,
                TARGET_POST,
                event.getPostId(),
                payload,
                eventOccurredAt(event.getTimestamp()));
    }

    private Instant eventOccurredAt(Long timestamp) {
        return isPositive(timestamp) ? Instant.ofEpochMilli(timestamp) : Instant.now();
    }

    private void notifyTopicFollowers(PostPublishedEvent event) {
        if (event == null || event.getPostId() == null || event.getTopicNotificationTargets() == null
                || event.getTopicNotificationTargets().isEmpty()) {
            return;
        }
        if (!isPublicPublished(event)) {
            log.warn("skip topic notification for non-public post: postId={} visibility={} status={}",
                    event.getPostId(), event.getVisibility(), event.getPostStatus());
            return;
        }
        for (PostPublishedEvent.TopicNotificationTarget topic : event.getTopicNotificationTargets()) {
            if (topic == null || !isPositive(topic.getTopicId())
                    || topic.getFollowerUids() == null || topic.getFollowerUids().isEmpty()) {
                continue;
            }
            Set<Long> receivers = new LinkedHashSet<>();
            for (Long receiverUid : topic.getFollowerUids()) {
                if (receiverUid == null || receiverUid <= 0 || receiverUid.equals(event.getAuthorId())) {
                    continue;
                }
                receivers.add(receiverUid);
            }
            Map<String, Object> content = topicNotificationContent(event, List.of(topic));
            List<SubscriptionUpdateDeliveryCommand> commands = receivers.stream()
                    .map(receiverUid -> topicFollowerCommand(receiverUid, event, topic, content))
                    .toList();
            deliverFollowerUpdates(commands);
        }
    }

    private Map<String, Object> topicNotificationContent(PostPublishedEvent event,
                                                         List<PostPublishedEvent.TopicNotificationTarget> topics) {
        List<Map<String, Object>> topicData = topics == null ? List.of() : topics.stream()
                .filter(topic -> topic != null && topic.getTopicId() != null)
                .limit(5)
                .map(topic -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("topicId", topic.getTopicId());
                    item.put("topicSlug", topic.getTopicSlug());
                    item.put("topicName", topic.getTopicName());
                    return item;
                })
                .toList();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("action", "topic_post_published");
        content.put("postId", event.getPostId());
        content.put("postTitle", event.getTitle());
        if (!topicData.isEmpty()) {
            Map<String, Object> first = topicData.get(0);
            content.put("topicId", first.get("topicId"));
            content.put("topicSlug", first.get("topicSlug"));
            content.put("topicName", first.get("topicName"));
            content.put("topics", topicData);
        }
        return content;
    }

    private boolean isPublicPublished(PostPublishedEvent event) {
        return event != null
                && Integer.valueOf(POST_VIS_PUBLIC).equals(event.getVisibility())
                && Integer.valueOf(POST_STATUS_PUBLISHED).equals(event.getPostStatus());
    }

    private Map<String, Object> operationCurationSelectedContent(OperationCurationSelectedEvent event) {
        Map<String, Object> content = new LinkedHashMap<>();
        String placementLabel = event.getPlacementKey();
        String href = sanitizeOperationCurationHref(event.getEntrance(), event.getContentId());
        content.put("action", "creator_curation_feedback");
        content.put("notificationCategory", "system");
        content.put("eventType", event.getEventType());
        content.put("source", "operation-curation");
        content.put("eventId", operationCurationDedupKey(event));
        content.put("authorUid", event.getAuthorUid());
        content.put("contentId", event.getContentId());
        content.put("contentTitle", event.getContentTitle());
        content.put("placementType", event.getPlacementType());
        content.put("placementId", event.getPlacementId());
        content.put("placementKey", event.getPlacementKey());
        content.put("placementLabel", placementLabel);
        content.put("topicSlug", "TOPIC".equals(event.getPlacementType()) ? event.getPlacementKey() : null);
        content.put("sectionKey", event.getSectionKey());
        content.put("reason", event.getReason());
        content.put("reasonText", event.getReason());
        content.put("entrance", event.getEntrance());
        content.put("href", href);
        content.put("status", event.getStatus());
        content.put("dedupKey", operationCurationDedupKey(event));
        return content;
    }

    private Map<String, Object> reportReceiptContent(String sourceType, Long reportId,
                                                     String userStatus, String targetPath) {
        Map<String, Object> content = new LinkedHashMap<>();
        String normalizedStatus = normalizeReportUserStatus(userStatus);
        content.put("action", ACTION_REPORT_RECEIPT);
        content.put("sourceType", sourceType);
        content.put("reportId", reportId);
        content.put("userStatus", normalizedStatus);
        content.put("targetPath", targetPath);
        content.put("title", "Report receipt");
        content.put("message", reportReceiptMessage(normalizedStatus));
        content.put("dedupKey", ACTION_REPORT_RECEIPT + ":" + sourceType + ":" + reportId);
        return content;
    }

    private String normalizeReportUserStatus(String userStatus) {
        if (REPORT_USER_STATUS_ACTION_TAKEN.equals(userStatus)) {
            return REPORT_USER_STATUS_ACTION_TAKEN;
        }
        if (REPORT_USER_STATUS_CLOSED.equals(userStatus)) {
            return REPORT_USER_STATUS_CLOSED;
        }
        return REPORT_USER_STATUS_NOT_ACCEPTED;
    }

    private String reportReceiptMessage(String status) {
        if (REPORT_USER_STATUS_ACTION_TAKEN.equals(status)) {
            return "Your report has been reviewed and action was taken.";
        }
        if (REPORT_USER_STATUS_CLOSED.equals(status)) {
            return "Your report has been closed with no further action.";
        }
        return "Your report is being reviewed. Please watch for updates.";
    }

    private Map<String, Object> contactRequestContent(String action, Long requestId, String targetPath) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("action", action);
        content.put("requestId", requestId);
        content.put("targetPath", targetPath);
        content.put("dedupKey", action + ":" + requestId);
        return content;
    }

    private Map<String, Object> contactRequestReportReceiptContent(Long reportId) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("action", ACTION_REPORT_RECEIPT);
        content.put("sourceType", SOURCE_CONTACT_REQUEST_REPORT);
        content.put("reportId", reportId);
        content.put("userStatus", REPORT_USER_STATUS_PROCESSING);
        content.put("targetPath", CONTACT_REQUEST_INBOX_PATH);
        content.put("title", "Contact request report submitted");
        content.put("message", "Your contact request report has been submitted for review.");
        content.put("dedupKey", ACTION_REPORT_RECEIPT + ":" + SOURCE_CONTACT_REQUEST_REPORT + ":" + reportId);
        return content;
    }

    private String sanitizeOperationCurationHref(String href, Long contentId) {
        if (href != null && href.startsWith("/") && !href.startsWith("//") && !href.startsWith("/api/") && !href.contains(" ")) {
            return href;
        }
        return contentId == null ? null : "/post/" + contentId;
    }

    private String operationCurationSkippedReason(OperationCurationSelectedEvent event) {
        if (event == null) {
            return "NULL_EVENT";
        }
        if (event.getAuthorUid() == null || event.getAuthorUid() <= 0) {
            return "MISSING_AUTHOR";
        }
        if (event.getContentId() == null || event.getContentId() <= 0) {
            return "MISSING_CONTENT";
        }
        if (!"PUBLISHED".equals(event.getStatus())) {
            return "NOT_PUBLISHED";
        }
        if (!OperationCurationSelectedEvent.OPERATION_CURATION_SELECTED.equals(event.getEventType())) {
            return "UNSUPPORTED_EVENT_TYPE";
        }
        return null;
    }

    private String operationCurationDedupKey(OperationCurationSelectedEvent event) {
        if (event.getDedupKey() != null && !event.getDedupKey().isBlank()) {
            return event.getDedupKey();
        }
        return String.join(":",
                "operation_curation_selected",
                String.valueOf(event.getAuthorUid()),
                String.valueOf(event.getContentId()),
                String.valueOf(event.getPlacementType()),
                String.valueOf(event.getPlacementId()),
                String.valueOf(event.getPlacementKey()),
                String.valueOf(event.getSectionKey()),
                String.valueOf(event.getEventType()));
    }

    private Set<String> extractMentionNames(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(text);
        while (matcher.find() && result.size() < 20) {
            result.add(matcher.group(1).trim());
        }
        return result;
    }

    private String textOf(String title, String content) {
        return (title == null ? "" : title) + "\n" + (content == null ? "" : content);
    }
}
