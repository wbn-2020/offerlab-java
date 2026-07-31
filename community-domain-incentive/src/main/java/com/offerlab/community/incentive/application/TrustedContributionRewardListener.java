package com.offerlab.community.incentive.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.offerlab.community.incentive.api.IncentiveDtos.RewardInboxCmd;
import com.offerlab.community.incentive.infrastructure.IncentiveMapper;
import com.offerlab.community.interaction.api.event.CommentReportReviewedEvent;
import com.offerlab.community.interaction.api.event.CommentUnavailableEvent;
import com.offerlab.community.post.api.event.OperationCurationSelectedEvent;
import com.offerlab.community.post.api.event.PostDeletedEvent;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.post.api.event.PostReportReviewedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrustedContributionRewardListener {
    private static final Set<String> ACCEPTED_SUGGESTION_DECISIONS =
            Set.of("ACCEPTED", "PARTIAL_ACCEPTED", "MERGED");
    private static final Set<String> POST_SCOPED_REWARD_EVENTS = Set.of(
            "FIRST_QUALIFIED_PUBLIC_POST", "QUALIFIED_PUBLIC_POST", "ANSWER_ACCEPTED",
            "CONTENT_SUGGESTION_ACCEPTED", "POST_FRESHNESS_UPDATED",
            "COMMENT_HELPFUL_THRESHOLD_REACHED", "OPERATION_CURATION_SELECTED");

    private final AccountLedgerService accountLedgerService;
    private final IncentiveMapper mapper;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onPostPublished(PostPublishedEvent event) {
        if (event == null || event.getPostId() == null || event.getAuthorId() == null
                || mapper.countQualifiedPublicPost(event.getPostId(), event.getAuthorId()) != 1) {
            return;
        }
        String domain = domainCode(event.getDomain());
        long qualifiedCount = mapper.countQualifiedPublicPostsByAuthor(event.getAuthorId());
        if (mapper.isFirstQualifiedPublicPost(event.getPostId(), event.getAuthorId()) == 1) {
            enqueuePair("FIRST_QUALIFIED_PUBLIC_POST", "post:" + event.getPostId(),
                    event.getAuthorId(), domain,
                    "FIRST_QUALIFIED_POST_POINT_V1", "FIRST_QUALIFIED_POST_REPUTATION_V1",
                    Map.of("postId", event.getPostId(), "qualifiedPostCount", qualifiedCount));
            return;
        }
        if (incentivesEnabled(domain)) {
            enqueueStrict("QUALIFIED_PUBLIC_POST", "post:" + event.getPostId(), event.getAuthorId(),
                    "GLOBAL", domain, "QUALIFIED_POST_POINT_V1", "POINT",
                    Map.of("postId", event.getPostId(), "qualifiedPostCount", qualifiedCount));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPostDeletedLocal(PostDeletedEvent event) {
        if (event == null || event.getPostId() == null) {
            return;
        }
        invalidateBestEffort(
                "POST", String.valueOf(event.getPostId()), "Trusted content was deleted");
    }

    public void onPostDeletedStrict(PostDeletedEvent event) {
        if (event == null || event.getPostId() == null) {
            return;
        }
        invalidateStrict("POST", String.valueOf(event.getPostId()), "Trusted content was deleted");
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true,
            condition = "#root.event.getClass().getSimpleName() == 'AnswerAcceptedEvent'")
    public void onAnswerAccepted(Object event) {
        if (event == null) return;
        Long postId = readLong(event, "postId");
        Long commentId = readLong(event, "commentId");
        enqueuePair("ANSWER_ACCEPTED", "answer:" + postId + ":" + commentId,
                readLong(event, "commentAuthorUid"), mapper.selectPostDomainCode(postId),
                "ACCEPTED_ANSWER_POINT_V1", "ACCEPTED_ANSWER_REPUTATION_V1",
                Map.of("postId", value(postId), "commentId", value(commentId)));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true,
            condition = "#root.event.getClass().getSimpleName() == 'ContentSuggestionDecidedEvent'")
    public void onSuggestionDecided(Object event) {
        if (event == null) return;
        String decision = readString(event, "decision");
        if (!ACCEPTED_SUGGESTION_DECISIONS.contains(decision)) return;
        Long postId = readLong(event, "postId");
        enqueuePair("CONTENT_SUGGESTION_ACCEPTED", "suggestion:" + readLong(event, "suggestionId"),
                readLong(event, "submitterUid"), mapper.selectPostDomainCode(postId),
                "SUGGESTION_ACCEPTED_POINT_V1", "SUGGESTION_ACCEPTED_REPUTATION_V1",
                Map.of("postId", value(postId), "decision", value(decision)));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true,
            condition = "#root.event.getClass().getSimpleName() == 'PostFreshnessChangedEvent'")
    public void onFreshnessUpdated(Object event) {
        if (event == null) return;
        String status = readString(event, "freshnessStatus");
        String previous = readString(event, "previousStatus");
        if (!"UPDATED".equals(status)
                || !Set.of("POSSIBLY_STALE", "AWAITING_AUTHOR_CONFIRMATION")
                .contains(previous)) {
            return;
        }
        Long postId = readLong(event, "postId");
        enqueuePair("POST_FRESHNESS_UPDATED",
                "freshness-cycle:" + postId + ":" + previous,
                readLong(event, "postAuthorUid"), mapper.selectPostDomainCode(postId),
                "FRESHNESS_UPDATED_POINT_V1", "FRESHNESS_UPDATED_REPUTATION_V1",
                Map.of("postId", value(postId), "previousStatus", value(previous)));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onOperationSelected(OperationCurationSelectedEvent event) {
        if (event == null) return;
        String source = event.getDedupKey() == null
                ? "curation:" + event.getPlacementType() + ":" + event.getPlacementId() + ":" + event.getContentId()
                : "curation:" + event.getDedupKey();
        enqueuePair("OPERATION_CURATION_SELECTED", source, event.getAuthorUid(),
                mapper.selectPostDomainCode(event.getContentId()),
                "OPERATION_SELECTED_POINT_V1", "OPERATION_SELECTED_REPUTATION_V1",
                Map.of("contentId", value(event.getContentId()), "placementType", value(event.getPlacementType())));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onPostReportReward(PostReportReviewedEvent event) {
        if (event == null || !"ACTION_TAKEN".equals(event.getUserStatus())) return;
        enqueuePair("POST_REPORT_ACTION_TAKEN", "post-report:" + event.getReportId(),
                event.getReporterUid(), mapper.selectPostDomainCode(event.getPostId()),
                "REPORT_ACTION_TAKEN_POINT_V1", "REPORT_ACTION_TAKEN_REPUTATION_V1",
                Map.of("postId", value(event.getPostId()), "reportId", value(event.getReportId())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPostReportInvalidationLocal(PostReportReviewedEvent event) {
        if (event == null || !"ACTION_TAKEN".equals(event.getUserStatus())) return;
        invalidateBestEffort(
                "POST", String.valueOf(event.getPostId()), "Post report resulted in content removal");
    }

    public void onPostReportReviewedStrict(PostReportReviewedEvent event) {
        if (event == null || !"ACTION_TAKEN".equals(event.getUserStatus())) return;
        invalidateStrict(
                "POST", String.valueOf(event.getPostId()), "Post report resulted in content removal");
        onPostReportReward(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true,
            condition = "#root.event.getClass().getSimpleName() == 'AnswerAcceptanceInvalidatedEvent'")
    public void onAnswerAcceptanceInvalidatedLocal(Object event) {
        if (event == null) return;
        invalidateBestEffort("ANSWER_ACCEPTED",
                answerReference(event), "Accepted answer is no longer valid");
    }

    public void onAnswerAcceptanceInvalidatedStrict(Object event) {
        if (event == null) return;
        invalidateStrict("ANSWER_ACCEPTED",
                answerReference(event), "Accepted answer is no longer valid");
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onCommentReportReviewed(CommentReportReviewedEvent event) {
        if (event == null || !"ACTION_TAKEN".equals(event.getUserStatus())) return;
        enqueuePair("COMMENT_REPORT_ACTION_TAKEN", "comment-report:" + event.getReportId(),
                event.getReporterUid(), mapper.selectPostDomainCode(event.getPostId()),
                "REPORT_ACTION_TAKEN_POINT_V1", "REPORT_ACTION_TAKEN_REPUTATION_V1",
                Map.of("postId", value(event.getPostId()), "commentId", value(event.getCommentId())));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true,
            condition = "#root.event.getClass().getSimpleName() == 'CommentHelpfulThresholdReachedEvent'")
    public void onCommentHelpfulThreshold(Object event) {
        if (event == null) return;
        Long commentId = readLong(event, "commentId");
        Long commentAuthorId = readLong(event, "commentAuthorId");
        Integer helpfulCount = readInteger(event, "helpfulCount");
        if (commentId == null || commentAuthorId == null
                || helpfulCount == null || helpfulCount < 3) {
            return;
        }
        Long postId = readLong(event, "postId");
        enqueuePair("COMMENT_HELPFUL_THRESHOLD_REACHED", "comment:" + commentId,
                commentAuthorId, mapper.selectPostDomainCode(postId),
                "HELPFUL_COMMENT_POINT_V1", "HELPFUL_COMMENT_REPUTATION_V1",
                 Map.of("postId", value(postId), "commentId", commentId,
                         "helpfulCount", helpfulCount));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCommentUnavailableLocal(CommentUnavailableEvent event) {
        invalidateCommentBestEffort(event);
    }

    public void onCommentUnavailableStrict(CommentUnavailableEvent event) {
        invalidateCommentStrict(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true,
            condition = "#root.event.getClass().getSimpleName() == 'CollaborationContributionAcceptedEvent'")
    public void onCollaborationAccepted(Object event) {
        if (event == null) return;
        String stableKey = readString(event, "stableKey");
        String contributionType = readString(event, "contributionType");
        Long sourceId = readLong(event, "sourceId");
        String source = stableKey == null
                ? "collaboration:" + contributionType + ":" + sourceId
                : "collaboration:" + stableKey;
        enqueuePair("COLLABORATION_CONTRIBUTION_ACCEPTED", source, readLong(event, "contributorUid"),
                domainCode(readInteger(event, "domain")), "COLLAB_ACCEPTED_POINT_V1", "COLLAB_ACCEPTED_REPUTATION_V1",
                Map.of("contributionType", value(contributionType),
                        "sourceId", value(sourceId), "targetPostId", value(readLong(event, "targetPostId"))));
    }

    private void enqueuePair(String eventType, String source, Long recipientUid, String domainCode,
                             String pointRule, String reputationRule, Map<String, Object> payload) {
        if (recipientUid == null || recipientUid <= 0 || !incentivesEnabled(domainCode)) return;
        enqueueStrict(eventType, source, recipientUid, "GLOBAL", domainCode, pointRule, "POINT", payload);
        enqueueStrict(eventType, source, recipientUid, domainCode, domainCode,
                reputationRule, "REPUTATION", payload);
    }

    private void invalidateBestEffort(String referenceType, String referenceId, String reason) {
        try {
            var job = accountLedgerService.enqueueTrustedRewardInvalidation(
                    referenceType, referenceId, reason);
            accountLedgerService.processInvalidationJobInline(job.getId());
        } catch (RuntimeException e) {
            log.error("local trusted reward invalidation failed; outbox consumer will retry: type={} id={}",
                    referenceType, referenceId, e);
        }
    }

    private void invalidateStrict(String referenceType, String referenceId, String reason) {
        var job = accountLedgerService.enqueueTrustedRewardInvalidation(
                referenceType, referenceId, reason);
        try {
            accountLedgerService.processInvalidationJobInline(job.getId());
        } catch (RuntimeException e) {
            log.error("trusted reward invalidation job persisted but inline processing failed: jobId={} type={} id={}",
                    job.getId(), referenceType, referenceId, e);
        }
    }

    private void invalidateCommentBestEffort(CommentUnavailableEvent event) {
        if (event == null || event.getCommentId() == null || event.getCommentId() <= 0) {
            return;
        }
        invalidateBestEffort(commentReferenceType(event), String.valueOf(event.getCommentId()),
                event.getReason() == null ? "Comment is no longer available" : event.getReason());
    }

    private void invalidateCommentStrict(CommentUnavailableEvent event) {
        if (event == null || event.getCommentId() == null || event.getCommentId() <= 0) {
            throw new IllegalArgumentException("comment unavailable event requires commentId");
        }
        invalidateStrict(commentReferenceType(event), String.valueOf(event.getCommentId()),
                event.getReason() == null ? "Comment is no longer available" : event.getReason());
    }

    private static String commentReferenceType(CommentUnavailableEvent event) {
        return Boolean.TRUE.equals(event.getCascade()) ? "COMMENT_BRANCH" : "COMMENT";
    }

    private static String answerReference(Object event) {
        Long postId = readLong(event, "postId");
        Long commentId = readLong(event, "commentId");
        if (postId == null || commentId == null) {
            throw new IllegalArgumentException("answer invalidation requires postId and commentId");
        }
        return "answer:" + postId + ":" + commentId;
    }

    private boolean incentivesEnabled(String domainCode) {
        if (domainCode == null) {
            return false;
        }
        return mapper.countIncentiveEnabledDomain(domainCode) == 1;
    }

    private void enqueueStrict(String eventType, String source, Long recipientUid, String domainCode,
                               String eventDomainCode,
                               String ruleCode, String accountKind, Map<String, Object> payload) {
        RewardInboxCmd cmd = new RewardInboxCmd();
        cmd.setStableKey(stableKey(eventType, source, accountKind));
        cmd.setEventType(eventType);
        cmd.setRecipientUid(recipientUid);
        cmd.setDomainCode(domainCode);
        cmd.setEventDomainCode(eventDomainCode);
        cmd.setSourceReferenceType(eventType);
        cmd.setSourceReferenceId(source);
        Object parentPostId = payload.get("postId");
        if (parentPostId == null) {
            parentPostId = payload.get("contentId");
        }
        if (POST_SCOPED_REWARD_EVENTS.contains(eventType) && parentPostId != null
                && !String.valueOf(parentPostId).isBlank()) {
            cmd.setParentReferenceType("POST");
            cmd.setParentReferenceId(String.valueOf(parentPostId));
        }
        cmd.setRuleCode(ruleCode);
        cmd.setRuleVersion(1);
        cmd.setPayloadJson(writePayload(payload));
        cmd.setReason("Trusted transactional/outbox contribution event");
        accountLedgerService.receiveTrustedReward(cmd);
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("trusted reward payload serialization failed", e);
        }
    }

    private static String stableKey(String eventType, String source, String accountKind) {
        String raw = "TRUST:" + eventType + ":" + source + ":" + accountKind;
        if (raw.length() <= 96) return raw;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return "TRUST:" + eventType.substring(0, Math.min(eventType.length(), 24))
                    + ":" + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return raw.substring(0, 96);
        }
    }

    private static String domainCode(Integer domain) {
        if (domain == null) return null;
        return switch (domain) {
            case 1 -> "TECH";
            case 2 -> "CAREER";
            case 3 -> "READING";
            case 4 -> "LIFESTYLE";
            case 5 -> "INVESTMENT";
            default -> null;
        };
    }

    private static Object value(Object value) {
        return value == null ? "" : value;
    }

    private static Object read(Object event, String property) {
        if (event instanceof Map<?, ?> map) {
            return map.get(property);
        }
        try {
            String getter = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
            return event.getClass().getMethod(getter).invoke(event);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("incomplete trusted reward event property: " + property, e);
        }
    }

    private static Long readLong(Object event, String property) {
        Object value = read(event, property);
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Integer readInteger(Object event, String property) {
        Object value = read(event, property);
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String readString(Object event, String property) {
        Object value = read(event, property);
        return value == null ? null : String.valueOf(value);
    }
}
