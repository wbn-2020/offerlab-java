package com.offerlab.community.interaction.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.mq.producer.EventPublisher;
import com.offerlab.community.infra.review.ReviewQueueItemCommand;
import com.offerlab.community.infra.review.ReviewQueuePublisher;
import com.offerlab.community.interaction.api.dto.PostOutcomeCmd;
import com.offerlab.community.interaction.api.dto.PostOutcomeDTO;
import com.offerlab.community.interaction.api.dto.PostOutcomeSampleDTO;
import com.offerlab.community.interaction.api.dto.PostOutcomeSummaryDTO;
import com.offerlab.community.interaction.api.enums.PostOutcomePublicationStatus;
import com.offerlab.community.interaction.api.enums.PostOutcomeStatus;
import com.offerlab.community.interaction.api.enums.PostOutcomeType;
import com.offerlab.community.interaction.api.enums.PostOutcomeVisibility;
import com.offerlab.community.interaction.api.event.PostOutcomeChangedEvent;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.PostOutcomeMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.PostOutcomePO;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.domain.model.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PostOutcomeService {

    static final int MIN_PUBLIC_SAMPLE_SIZE = 3;
    private static final int MAX_PUBLIC_TEXT_SAMPLES = 20;
    private static final String REVIEW_SOURCE_TYPE = "POST_OUTCOME";
    private static final String MODERATION_SCOPE = "POST_OUTCOME";

    private final PostOutcomeMapper outcomeMapper;
    private final PostFacade postFacade;
    private final DomainModeratorService domainModeratorService;
    private final ContentModerationService contentModerationService;
    private final ReviewQueuePublisher reviewQueuePublisher;
    private final UserRevisitService revisitService;
    private final SnowflakeIdGenerator idGenerator;
    private final EventPublisher events;

    public PostOutcomeDTO mine(Long postId, Long uid) {
        requireId(postId);
        requireUid(uid);
        requireVisiblePost(postId, uid);
        return toDto(outcomeMapper.selectActiveMine(postId, uid));
    }

    @Transactional
    public PostOutcomeDTO saveMine(Long postId, Long uid, PostOutcomeCmd cmd) {
        requireId(postId);
        requireUid(uid);
        if (cmd == null || cmd.getOutcomeType() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        requireVisiblePost(postId, uid);
        PostDTO post = requirePostMetadata(postId);
        requireNotAuthor(uid, post);
        PostOutcomeVisibility visibility = cmd.getVisibility() == null
                ? PostOutcomeVisibility.PRIVATE : cmd.getVisibility();
        if (visibility != PostOutcomeVisibility.PRIVATE) {
            requirePublicPost(postId);
            if (Objects.equals(post.getDomain(), Post.DOMAIN_INVESTMENT)
                    && !Boolean.TRUE.equals(cmd.getRiskAcknowledged())) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                        "riskAcknowledged is required for public investment outcomes");
            }
            contentModerationService.requireUserCanPublish(uid);
            contentModerationService.checkContent(uid, MODERATION_SCOPE, REVIEW_SOURCE_TYPE, postId,
                    cmd.getContextNote(), cmd.getResultNote());
        }

        PostOutcomePO existing = outcomeMapper.selectActiveMine(postId, uid);
        PostOutcomePO row = normalizedRow(postId, uid, cmd, visibility);
        String changeType;
        if (existing == null) {
            if (cmd.getExpectedRevision() != null) {
                throw staleOutcome();
            }
            row.setId(idGenerator.nextId());
            try {
                if (outcomeMapper.insertOutcome(row) != 1) {
                    throw new BizException(ErrorCode.DATABASE_ERROR);
                }
            } catch (DuplicateKeyException e) {
                throw staleOutcome();
            }
            row = requireOutcome(row.getId());
            changeType = "CREATED";
        } else {
            if (cmd.getExpectedRevision() == null || cmd.getExpectedRevision() <= 0) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "expectedRevision is required");
            }
            row.setId(existing.getId());
            if (outcomeMapper.updateIfRevision(row, cmd.getExpectedRevision(), LocalDateTime.now()) != 1) {
                throw staleOutcome();
            }
            row = requireOutcome(existing.getId());
            changeType = "UPDATED";
        }
        syncRevisit(post, row);
        if (row.getPublicationStatus().equals(PostOutcomePublicationStatus.PENDING_REVIEW.name())) {
            publishReviewQueueItem(post, row);
        } else {
            closeReviewQueueItem(row, "outcome is private");
        }
        publishChanged(post, row, changeType);
        return toDto(row);
    }

    @Transactional
    public void withdrawMine(Long postId, Long uid, Integer expectedRevision) {
        requireId(postId);
        requireUid(uid);
        if (expectedRevision == null || expectedRevision <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        PostOutcomePO existing = outcomeMapper.selectActiveMine(postId, uid);
        if (existing == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        PostDTO post = requirePostMetadata(postId);
        if (outcomeMapper.withdrawIfRevision(existing.getId(), postId, uid,
                expectedRevision, LocalDateTime.now()) != 1) {
            throw staleOutcome();
        }
        revisitService.cancelPostOutcome(uid, existing.getId());
        PostOutcomePO withdrawn = requireOutcome(existing.getId());
        closeReviewQueueItem(withdrawn, "outcome withdrawn");
        publishChanged(post, withdrawn, "WITHDRAWN");
    }

    public PostOutcomeSummaryDTO publicSummary(Long postId) {
        requireId(postId);
        PostDTO post = requirePublicPost(postId);
        Map<PostOutcomeType, Long> counts = new EnumMap<>(PostOutcomeType.class);
        for (PostOutcomeType type : PostOutcomeType.values()) {
            counts.put(type, 0L);
        }
        for (Map<String, Object> row : safe(outcomeMapper.countPublicByType(postId, post.getAuthorId()))) {
            PostOutcomeType type = parseType(value(row, "outcomeType", "outcome_type"));
            if (type != null) {
                counts.put(type, Math.max(0L, longValue(value(row, "outcomeCount", "outcome_count"))));
            }
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        if (total < MIN_PUBLIC_SAMPLE_SIZE) {
            return PostOutcomeSummaryDTO.builder()
                    .postId(postId)
                    .minimumSampleSize(MIN_PUBLIC_SAMPLE_SIZE)
                    .minimumSampleMet(false)
                    .publicSampleCount(total)
                    .outcomeCounts(Map.of())
                    .samples(List.of())
                    .build();
        }
        List<PostOutcomeSampleDTO> samples = outcomeMapper.listPublicSamples(
                        postId, post.getAuthorId(), MAX_PUBLIC_TEXT_SAMPLES).stream()
                .filter(row -> StringUtils.hasText(row.getResultNote()))
                .map(row -> PostOutcomeSampleDTO.builder()
                        .id(row.getId())
                        .outcomeType(parseType(row.getOutcomeType()))
                        .resultNote(row.getResultNote())
                        .contributorUid("PUBLIC_ATTRIBUTED".equals(row.getVisibility()) ? row.getUid() : null)
                        .createdAt(row.getCreatedAt())
                        .build())
                .toList();
        return PostOutcomeSummaryDTO.builder()
                .postId(postId)
                .minimumSampleSize(MIN_PUBLIC_SAMPLE_SIZE)
                .minimumSampleMet(true)
                .publicSampleCount(total)
                .outcomeCounts(Map.copyOf(counts))
                .samples(samples)
                .build();
    }

    @Transactional
    public PostOutcomeDTO review(Long outcomeId, Long reviewerUid, boolean approved, String note) {
        requireId(outcomeId);
        requireUid(reviewerUid);
        String reviewNote = normalizeRequired(note, 1000);
        PostOutcomePO outcome = requireOutcome(outcomeId);
        PostDTO post = postFacade.getPostMetadata(outcome.getPostId());
        if (post == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        domainModeratorService.requireModerateDomain(reviewerUid, post.getDomain());
        if (Objects.equals(reviewerUid, outcome.getUid())
                || Objects.equals(reviewerUid, post.getAuthorId())) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "outcome contributors and post authors cannot review this outcome");
        }
        if (!PostOutcomePublicationStatus.PENDING_REVIEW.name().equals(outcome.getPublicationStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        if (approved) {
            requirePublicPost(outcome.getPostId());
        }
        if (outcomeMapper.reviewPending(outcomeId, reviewerUid, approved, reviewNote) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        PostOutcomePO reviewed = requireOutcome(outcomeId);
        reviewQueuePublisher.resolve(REVIEW_SOURCE_TYPE, outcomeId,
                approved ? "approved" : "rejected",
                approved ? "outcome published" : "outcome rejected",
                reviewNote, reviewerUid);
        publishChanged(post, reviewed, approved ? "PUBLISHED" : "REJECTED");
        return toDto(reviewed);
    }

    private PostOutcomePO normalizedRow(Long postId, Long uid, PostOutcomeCmd cmd,
                                        PostOutcomeVisibility visibility) {
        PostOutcomePO row = new PostOutcomePO();
        row.setPostId(postId);
        row.setUid(uid);
        row.setOutcomeType(cmd.getOutcomeType().name());
        row.setContextNote(normalizeOptional(cmd.getContextNote(), 1000));
        row.setResultNote(normalizeOptional(cmd.getResultNote(), 2000));
        row.setVisibility(visibility.name());
        row.setPublicationStatus(visibility == PostOutcomeVisibility.PRIVATE
                ? PostOutcomePublicationStatus.PRIVATE.name()
                : PostOutcomePublicationStatus.PENDING_REVIEW.name());
        row.setConsentedAt(visibility == PostOutcomeVisibility.PRIVATE ? null : LocalDateTime.now());
        row.setFollowUpAt(normalizeFollowUp(cmd.getFollowUpAt()));
        return row;
    }

    private void syncRevisit(PostDTO post, PostOutcomePO outcome) {
        if (outcome.getFollowUpAt() == null) {
            revisitService.cancelPostOutcome(outcome.getUid(), outcome.getId());
            return;
        }
        revisitService.schedulePostOutcome(outcome.getUid(), outcome.getId(), outcome.getPostId(),
                outcome.getRevision(), post.getTitle(), outcome.getFollowUpAt());
    }

    private void publishReviewQueueItem(PostDTO post, PostOutcomePO outcome) {
        reviewQueuePublisher.reopen(new ReviewQueueItemCommand(
                REVIEW_SOURCE_TYPE,
                outcome.getId(),
                "Practice outcome for post " + post.getId(),
                summary(outcome),
                "medium",
                outcome.getUid(),
                60,
                "{\"postId\":" + post.getId() + ",\"revision\":" + outcome.getRevision() + "}",
                "public outcome consent submitted for review"
        ));
    }

    private void closeReviewQueueItem(PostOutcomePO outcome, String reason) {
        reviewQueuePublisher.resolve(REVIEW_SOURCE_TYPE, outcome.getId(),
                "closed", reason, reason, null);
    }

    private void publishChanged(PostDTO post, PostOutcomePO outcome, String changeType) {
        events.publish(PostOutcomeChangedEvent.builder()
                .outcomeId(outcome.getId())
                .postId(outcome.getPostId())
                .uid(outcome.getUid())
                .postAuthorUid(post == null ? null : post.getAuthorId())
                .outcomeType(outcome.getOutcomeType())
                .visibility(outcome.getVisibility())
                .publicationStatus(outcome.getPublicationStatus())
                .outcomeStatus(outcome.getOutcomeStatus())
                .outcomeRevision(outcome.getRevision())
                .changeType(changeType)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    private PostDTO requireVisiblePost(Long postId, Long uid) {
        PostDTO post = postFacade.getPost(postId, uid);
        if (post == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private PostDTO requirePublicPost(Long postId) {
        PostDTO post = postFacade.getPostMetadata(postId);
        if (post == null
                || !Objects.equals(post.getPostStatus(), Post.STATUS_PUBLISHED)
                || !Objects.equals(post.getVisibility(), Post.VIS_PUBLIC)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return post;
    }

    private PostDTO requirePostMetadata(Long postId) {
        PostDTO post = postFacade.getPostMetadata(postId);
        if (post == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private static void requireNotAuthor(Long uid, PostDTO post) {
        if (post != null && Objects.equals(uid, post.getAuthorId())) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "post authors cannot submit outcomes for their own posts");
        }
    }

    private PostOutcomePO requireOutcome(Long id) {
        PostOutcomePO row = outcomeMapper.selectActiveById(id);
        if (row == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return row;
    }

    private static PostOutcomeDTO toDto(PostOutcomePO row) {
        if (row == null) {
            return null;
        }
        return PostOutcomeDTO.builder()
                .id(row.getId())
                .postId(row.getPostId())
                .uid(row.getUid())
                .outcomeType(PostOutcomeType.valueOf(row.getOutcomeType()))
                .contextNote(row.getContextNote())
                .resultNote(row.getResultNote())
                .visibility(PostOutcomeVisibility.valueOf(row.getVisibility()))
                .publicationStatus(PostOutcomePublicationStatus.valueOf(row.getPublicationStatus()))
                .consentedAt(row.getConsentedAt())
                .reviewerUid(row.getReviewerUid())
                .reviewNote(row.getReviewNote())
                .reviewedAt(row.getReviewedAt())
                .followUpAt(row.getFollowUpAt())
                .outcomeStatus(PostOutcomeStatus.valueOf(row.getOutcomeStatus()))
                .revision(row.getRevision())
                .createTime(row.getCreateTime())
                .updateTime(row.getUpdateTime())
                .build();
    }

    private static LocalDateTime normalizeFollowUp(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        if (!value.isAfter(LocalDateTime.now()) || value.isAfter(LocalDateTime.now().plusYears(1))) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "followUpAt must be within the next year");
        }
        return value;
    }

    private static String normalizeOptional(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static String normalizeRequired(String value, int maxLength) {
        String normalized = normalizeOptional(value, maxLength);
        if (normalized == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "review note is required");
        }
        return normalized;
    }

    private static String summary(PostOutcomePO outcome) {
        return ("type=" + outcome.getOutcomeType()
                + "; context=" + Objects.toString(outcome.getContextNote(), "")
                + "; result=" + Objects.toString(outcome.getResultNote(), "")).substring(
                0, Math.min(500, ("type=" + outcome.getOutcomeType()
                        + "; context=" + Objects.toString(outcome.getContextNote(), "")
                        + "; result=" + Objects.toString(outcome.getResultNote(), "")).length()));
    }

    private static PostOutcomeType parseType(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return PostOutcomeType.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Object value(Map<String, Object> row, String... keys) {
        if (row == null) {
            return null;
        }
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
            String upper = key.toUpperCase(Locale.ROOT);
            if (row.containsKey(upper)) {
                return row.get(upper);
            }
        }
        return null;
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static void requireUid(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static BizException staleOutcome() {
        return new BizException(ErrorCode.INVALID_STATUS.getCode(),
                "Outcome changed, please refresh and retry");
    }
}
