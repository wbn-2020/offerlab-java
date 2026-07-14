package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.CreatorCurationFeedbackDTO;
import com.offerlab.community.analytics.api.dto.CreatorGrowthWorkspaceDTO;
import com.offerlab.community.analytics.api.dto.CreatorRepresentativePostCmd;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.CreatorRepresentativePostMapper;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.GrowthInsightMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentSeriesMapper;
import com.offerlab.community.post.infrastructure.persistence.po.ContentSeriesPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreatorGrowthService {

    private static final int INVESTMENT_DOMAIN = 5;
    private static final int REPRESENTATIVE_DISPLAY_LIMIT = 3;
    private static final int REPRESENTATIVE_SAVE_LIMIT = 5;
    private static final String AUTO_REPRESENTATIVE_SOURCE = "auto_profile_candidate";
    private static final String HIGH_RISK_REPRESENTATIVE_SOURCE = "neutral_profile_candidate";
    private static final String MANUAL_REPRESENTATIVE_SOURCE = "manual_profile_display";
    private static final String TRUSTED_CONTENT_QUERY_FAILED = "TRUSTED_CONTENT_QUERY_FAILED";
    private static final String TRUSTED_CONTENT_ROW_MISSING = "TRUSTED_CONTENT_ROW_MISSING";
    private static final String TRUSTED_CONTENT_ROW_INVALID = "TRUSTED_CONTENT_ROW_INVALID";
    private static final List<String> TRUSTED_CONTENT_METRIC_FIELDS = List.of(
            "pendingSuggestions",
            "freshnessAwaitingConfirmation",
            "unresolvedQuestions",
            "usefulFeedback7Days",
            "usefulFeedback30Days",
            "effectiveReads7Days",
            "effectiveReads30Days");

    private final GrowthInsightMapper growthInsightMapper;
    private final ContentSeriesMapper contentSeriesMapper;
    private final CreatorRepresentativePostMapper representativePostMapper;
    private final CreatorCurationFeedbackService creatorCurationFeedbackService;
    private final SnowflakeIdGenerator idGenerator;

    public CreatorGrowthWorkspaceDTO workspace(Long uid) {
        requireUser(uid);
        CreatorGrowthWorkspaceDTO.CreatorFeedbackSummaryDTO summary = feedbackSummary(uid);
        LocalDateTime since30 = sinceDays(30);
        CreatorGrowthWorkspaceDTO.TrustedContentDTO trustedContent = trustedContent(uid, since30);
        List<CreatorGrowthWorkspaceDTO.CreatorTopPostDTO> topPosts = topPosts(uid, since30);
        List<CreatorGrowthWorkspaceDTO.CreatorReplyOpportunityDTO> replyOpportunities = replyOpportunities(uid);
        List<CreatorGrowthWorkspaceDTO.PublicSeriesDTO> publicSeries = publicSeries(uid);
        List<CreatorGrowthWorkspaceDTO.CreatorTopicIdeaDTO> topicIdeas = topicIdeasFrom(
                summary,
                replyOpportunities,
                publicSeries,
                safeRows(growthInsightMapper.selectAuthorDomainStats(uid, since30)));
        List<CreatorGrowthWorkspaceDTO.RepresentativePostDTO> representativePosts =
                representativePosts(uid, topPosts, REPRESENTATIVE_DISPLAY_LIMIT);
        CreatorCurationFeedbackDTO.CreatorCurationFeedbackSummaryDTO curationSummary = curationFeedbackSummary(uid);
        List<CreatorCurationFeedbackDTO> curationFeedback = safeList(curationSummary.getRecentItems()).isEmpty()
                ? safeList(curationSummary.getItems())
                : safeList(curationSummary.getRecentItems());
        List<CreatorGrowthWorkspaceDTO.MaintainablePostDTO> maintainablePosts =
                maintainablePosts(topPosts, replyOpportunities);
        List<CreatorGrowthWorkspaceDTO.WorkspaceActionDTO> actions =
                workspaceActions(maintainablePosts, replyOpportunities, topicIdeas, curationFeedback);
        return CreatorGrowthWorkspaceDTO.builder()
                .source(workspaceSource(summary, curationSummary, topPosts, replyOpportunities, curationFeedback))
                .periodDays(30)
                .degraded(curationSummary.isDegraded() || trustedContent.isDegraded())
                .fallbackReason(workspaceFallbackReason(
                        summary,
                        curationSummary,
                        trustedContent,
                        topPosts,
                        replyOpportunities,
                        curationFeedback))
                .summary(workspaceSummary(summary, curationSummary, representativePosts, replyOpportunities))
                .trustedContent(trustedContent)
                .maintainablePosts(maintainablePosts)
                .curationFeedback(curationFeedback)
                .actions(actions)
                .creatorFeedbackSummary(summary)
                .creatorTopPosts(topPosts)
                .creatorReplyOpportunities(replyOpportunities)
                .representativePosts(representativePosts)
                .publicSeries(publicSeries)
                .creatorTopicIdeas(topicIdeas)
                .creatorDigestNotification(CreatorGrowthWorkspaceDTO.CreatorDigestNotificationDTO.builder()
                        .frequency("weekly_digest_only")
                        .copy("Low-frequency creator digest, delivered only through existing preferences.")
                        .build())
                .nonPaymentIncentiveCopy(List.of(
                        "Use representative posts, public series, and useful discussion as profile display.",
                        "Feedback is for creator review and follow-up planning, not commercial placement."))
                .build();
    }

    public CreatorGrowthWorkspaceDTO.TrustedContentDTO trustedContent(Long uid) {
        requireUser(uid);
        return trustedContent(uid, sinceDays(30));
    }

    private CreatorGrowthWorkspaceDTO.TrustedContentDTO trustedContent(Long uid, LocalDateTime since30) {
        try {
            Map<String, Object> row = growthInsightMapper.selectTrustedContentSummary(
                    uid,
                    Post.TYPE_COMMUNITY_QUESTION,
                    sinceDays(7),
                    since30);
            if (row == null) {
                log.warn("creator trusted-content row missing: uid={}", uid);
                return degradedTrustedContent(TRUSTED_CONTENT_ROW_MISSING);
            }
            Map<String, Long> metrics = new LinkedHashMap<>();
            List<String> invalidFields = new ArrayList<>();
            for (String field : TRUSTED_CONTENT_METRIC_FIELDS) {
                Long metric = trustedContentMetric(row, field);
                if (metric == null) {
                    invalidFields.add(field);
                } else {
                    metrics.put(field, metric);
                }
            }
            if (!invalidFields.isEmpty()) {
                log.warn("creator trusted-content row invalid: uid={}, invalidFields={}", uid, invalidFields);
                return degradedTrustedContent(TRUSTED_CONTENT_ROW_INVALID);
            }
            return CreatorGrowthWorkspaceDTO.TrustedContentDTO.builder()
                    .degraded(false)
                    .pendingSuggestions(metrics.get("pendingSuggestions"))
                    .freshnessAwaitingConfirmation(metrics.get("freshnessAwaitingConfirmation"))
                    .unresolvedQuestions(metrics.get("unresolvedQuestions"))
                    .usefulFeedback7Days(metrics.get("usefulFeedback7Days"))
                    .usefulFeedback30Days(metrics.get("usefulFeedback30Days"))
                    .effectiveReads7Days(metrics.get("effectiveReads7Days"))
                    .effectiveReads30Days(metrics.get("effectiveReads30Days"))
                    .pendingSuggestionItems(taskItems(
                            growthInsightMapper.selectPendingSuggestionItems(uid),
                            true))
                    .freshnessItems(taskItems(
                            growthInsightMapper.selectFreshnessItems(uid),
                            false))
                    .pendingQuestionItems(taskItems(
                            growthInsightMapper.selectPendingQuestionItems(uid, Post.TYPE_COMMUNITY_QUESTION),
                            false))
                    .build();
        } catch (RuntimeException e) {
            log.warn("creator trusted-content query failed: uid={}", uid, e);
            return degradedTrustedContent(TRUSTED_CONTENT_QUERY_FAILED);
        }
    }

    private static CreatorGrowthWorkspaceDTO.TrustedContentDTO degradedTrustedContent(String fallbackReason) {
        return CreatorGrowthWorkspaceDTO.TrustedContentDTO.builder()
                .degraded(true)
                .fallbackReason(fallbackReason)
                .build();
    }

    private static List<CreatorGrowthWorkspaceDTO.TrustedContentTaskItemDTO> taskItems(
            List<Map<String, Object>> rows,
            boolean suggestion
    ) {
        return safeRows(rows).stream()
                .limit(5)
                .map(row -> CreatorGrowthWorkspaceDTO.TrustedContentTaskItemDTO.builder()
                        .postId(asLongObject(row.get("postId")))
                        .suggestionId(suggestion ? asLongObject(row.get("suggestionId")) : null)
                        .postTitle(compact(text(row.get("postTitle")), 160))
                        .status(compact(text(row.get("status")), 64))
                        .createdAt(asDateTime(row.get("createdAt")))
                        .updatedAt(asDateTime(row.get("updatedAt")))
                        .href("/post/" + asLongObject(row.get("postId")))
                        .build())
                .filter(item -> item.getPostId() != null)
                .toList();
    }

    public CreatorGrowthWorkspaceDTO.CreatorFeedbackSummaryDTO feedbackSummary(Long uid) {
        requireUser(uid);
        List<Map<String, Object>> sevenDayRows = safeRows(growthInsightMapper.selectAuthorDomainStats(uid, sinceDays(7)));
        List<Map<String, Object>> thirtyDayRows = safeRows(growthInsightMapper.selectAuthorDomainStats(uid, sinceDays(30)));
        CreatorGrowthWorkspaceDTO.FeedbackWindowDTO sevenDays = feedbackWindow("last_7_days", 7, sevenDayRows);
        CreatorGrowthWorkspaceDTO.FeedbackWindowDTO thirtyDays = feedbackWindow("last_30_days", 30, thirtyDayRows);
        return CreatorGrowthWorkspaceDTO.CreatorFeedbackSummaryDTO.builder()
                .headline("Last 7 days and last 30 days are compared with visible counts only.")
                .windows(List.of(sevenDays, thirtyDays))
                .build();
    }

    public List<CreatorGrowthWorkspaceDTO.CreatorTopicIdeaDTO> topicIdeas(Long uid) {
        requireUser(uid);
        return topicIdeasFrom(feedbackSummary(uid), replyOpportunities(uid), publicSeries(uid),
                safeRows(growthInsightMapper.selectAuthorDomainStats(uid, sinceDays(30))));
    }

    public List<CreatorGrowthWorkspaceDTO.RepresentativePostDTO> representativePosts(Long uid) {
        requireUser(uid);
        return representativePosts(uid, topPosts(uid, sinceDays(30)), REPRESENTATIVE_SAVE_LIMIT);
    }

    @Transactional
    public List<CreatorGrowthWorkspaceDTO.RepresentativePostDTO> updateRepresentativePosts(
            Long uid,
            CreatorRepresentativePostCmd cmd) {
        requireUser(uid);
        requireRepresentativeTable();
        List<Long> postIds = normalizePostIds(cmd == null ? null : cmd.getPostIds());
        if (postIds.isEmpty()) {
            representativePostMapper.softDeleteByCreatorUid(uid);
            return List.of();
        }
        List<Map<String, Object>> visibleRows = safeRows(growthInsightMapper.selectRepresentativePostsByIds(uid, postIds));
        if (visibleRows.size() != postIds.size()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "Representative posts must be your public visible posts");
        }
        representativePostMapper.softDeleteByCreatorUid(uid);
        for (int i = 0; i < postIds.size(); i++) {
            representativePostMapper.upsertActivePost(idGenerator.nextId(), uid, postIds.get(i), i);
        }
        return manualRepresentativePosts(uid, REPRESENTATIVE_SAVE_LIMIT);
    }

    private List<CreatorGrowthWorkspaceDTO.CreatorTopPostDTO> topPosts(Long uid, LocalDateTime since) {
        return safeRows(growthInsightMapper.selectRepresentativePosts(uid, since, 5)).stream()
                .map(row -> {
                    Integer domain = asInteger(row.get("domain"));
                    long feedbackCount = asLong(row.get("interactionCount"));
                    return CreatorGrowthWorkspaceDTO.CreatorTopPostDTO.builder()
                            .postId(asLongObject(row.get("postId")))
                            .title(text(row.get("title")))
                            .domain(domain)
                            .domainName(domainName(domain))
                            .feedbackCount(feedbackCount)
                            .featured(asLong(row.get("featured")) > 0)
                            .reason(topPostReason(domain, feedbackCount))
                            .visibilityScope("creator_only")
                            .build();
                })
                .filter(item -> item.getPostId() != null)
                .toList();
    }

    private List<CreatorGrowthWorkspaceDTO.CreatorReplyOpportunityDTO> replyOpportunities(Long uid) {
        try {
            return safeRows(growthInsightMapper.selectCreatorReplyOpportunities(uid, sinceDays(7), 5)).stream()
                    .map(row -> CreatorGrowthWorkspaceDTO.CreatorReplyOpportunityDTO.builder()
                            .commentId(asLongObject(row.get("commentId")))
                            .postId(asLongObject(row.get("postId")))
                            .postTitle(text(row.get("postTitle")))
                            .commentExcerpt(compact(text(row.get("commentExcerpt")), 120))
                            .likeCount(asLong(row.get("likeCount")))
                            .reason("Recent discussion worth a calm follow-up.")
                            .build())
                    .filter(item -> item.getCommentId() != null && item.getPostId() != null)
                    .toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private List<CreatorGrowthWorkspaceDTO.RepresentativePostDTO> representativePosts(
            Long uid,
            List<CreatorGrowthWorkspaceDTO.CreatorTopPostDTO> topPosts,
            int limit) {
        List<CreatorGrowthWorkspaceDTO.RepresentativePostDTO> manual = manualRepresentativePosts(uid, limit);
        if (!manual.isEmpty()) {
            return manual;
        }
        return autoRepresentativePosts(topPosts, limit);
    }

    private List<CreatorGrowthWorkspaceDTO.RepresentativePostDTO> autoRepresentativePosts(
            List<CreatorGrowthWorkspaceDTO.CreatorTopPostDTO> topPosts,
            int limit) {
        return safeList(topPosts).stream()
                .limit(limit)
                .map(item -> CreatorGrowthWorkspaceDTO.RepresentativePostDTO.builder()
                        .postId(item.getPostId())
                        .title(item.getTitle())
                        .domain(item.getDomain())
                        .domainName(item.getDomainName())
                        .feedbackCount(item.getFeedbackCount())
                        .source(item.getDomain() != null && item.getDomain() == INVESTMENT_DOMAIN
                                ? HIGH_RISK_REPRESENTATIVE_SOURCE
                                : AUTO_REPRESENTATIVE_SOURCE)
                        .publicVisible(true)
                        .boundaryCopy(item.getDomain() != null && item.getDomain() == INVESTMENT_DOMAIN
                                ? "Neutral profile display with risk context, not professional endorsement or commercial placement."
                                : "Profile display only, not platform endorsement or commercial placement.")
                        .build())
                .toList();
    }

    private List<CreatorGrowthWorkspaceDTO.RepresentativePostDTO> manualRepresentativePosts(Long uid, int limit) {
        try {
            if (representativePostMapper.tableExists() <= 0) {
                return List.of();
            }
            List<Long> postIds = safeList(representativePostMapper.selectActivePostIds(uid, limit)).stream()
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .limit(limit)
                    .toList();
            if (postIds.isEmpty()) {
                return List.of();
            }
            return safeRows(growthInsightMapper.selectRepresentativePostsByIds(uid, postIds)).stream()
                    .map(row -> {
                        Integer domain = asInteger(row.get("domain"));
                        return CreatorGrowthWorkspaceDTO.RepresentativePostDTO.builder()
                                .postId(asLongObject(row.get("postId")))
                                .title(text(row.get("title")))
                                .domain(domain)
                                .domainName(domainName(domain))
                                .feedbackCount(asLong(row.get("interactionCount")))
                                .source(domain != null && domain == INVESTMENT_DOMAIN
                                        ? HIGH_RISK_REPRESENTATIVE_SOURCE
                                        : MANUAL_REPRESENTATIVE_SOURCE)
                                .publicVisible(true)
                                .boundaryCopy(domain != null && domain == INVESTMENT_DOMAIN
                                        ? "Neutral profile display with risk context, not professional endorsement or commercial placement."
                                        : "Profile display only, not platform endorsement or commercial placement.")
                                .build();
                    })
                    .filter(item -> item.getPostId() != null)
                    .toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private List<CreatorGrowthWorkspaceDTO.PublicSeriesDTO> publicSeries(Long uid) {
        try {
            if (contentSeriesMapper.tableExists() <= 0) {
                return List.of();
            }
            return safeList(contentSeriesMapper.selectPublicByCreatorUid(uid, 0L, 3)).stream()
                    .filter(item -> item.getId() != null)
                    .map(item -> CreatorGrowthWorkspaceDTO.PublicSeriesDTO.builder()
                            .seriesId(item.getId())
                            .title(item.getTitle())
                            .description(item.getDescription())
                            .domain(item.getDomain())
                            .domainName(domainName(item.getDomain()))
                            .build())
                    .toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private CreatorCurationFeedbackDTO.CreatorCurationFeedbackSummaryDTO curationFeedbackSummary(Long uid) {
        try {
            return creatorCurationFeedbackService.summary(uid);
        } catch (RuntimeException ignored) {
            return CreatorCurationFeedbackDTO.CreatorCurationFeedbackSummaryDTO.builder()
                    .updatedAt(LocalDateTime.now())
                    .degraded(true)
                    .fallbackReason("FEEDBACK_SOURCE_UNAVAILABLE")
                    .total(0)
                    .items(List.of())
                    .recentItems(List.of())
                    .build();
        }
    }

    private static CreatorGrowthWorkspaceDTO.CreatorWorkspaceSummaryDTO workspaceSummary(
            CreatorGrowthWorkspaceDTO.CreatorFeedbackSummaryDTO summary,
            CreatorCurationFeedbackDTO.CreatorCurationFeedbackSummaryDTO curationSummary,
            List<CreatorGrowthWorkspaceDTO.RepresentativePostDTO> representativePosts,
            List<CreatorGrowthWorkspaceDTO.CreatorReplyOpportunityDTO> replyOpportunities) {
        CreatorGrowthWorkspaceDTO.FeedbackWindowDTO thirtyDays = window(summary, "last_30_days");
        return CreatorGrowthWorkspaceDTO.CreatorWorkspaceSummaryDTO.builder()
                .publicPostCount(thirtyDays.getPostCount())
                .recentFavoriteCount(thirtyDays.getFavoriteCount())
                .recentCommentCount(thirtyDays.getCommentCount())
                .curationInclusionCount(curationSummary.getTotal())
                .representativePostCount(safeList(representativePosts).size())
                .replyOpportunityCount(safeList(replyOpportunities).size())
                .updatedAt(curationSummary.getUpdatedAt() == null ? LocalDateTime.now() : curationSummary.getUpdatedAt())
                .build();
    }

    private static List<CreatorGrowthWorkspaceDTO.MaintainablePostDTO> maintainablePosts(
            List<CreatorGrowthWorkspaceDTO.CreatorTopPostDTO> topPosts,
            List<CreatorGrowthWorkspaceDTO.CreatorReplyOpportunityDTO> replyOpportunities) {
        return safeList(topPosts).stream()
                .limit(5)
                .map(post -> CreatorGrowthWorkspaceDTO.MaintainablePostDTO.builder()
                        .postId(post.getPostId())
                        .title(post.getTitle())
                        .type("post")
                        .visibility("public")
                        .href(post.getPostId() == null ? null : "/post/" + post.getPostId())
                        .editHref(post.getPostId() == null ? null : "/editor?source=creator_workbench&postId=" + post.getPostId())
                        .primarySignal(hasReplyOpportunity(post.getPostId(), replyOpportunities)
                                ? "recent_comments"
                                : (post.isFeatured() ? "curation_inclusion" : "visible_feedback"))
                        .signalText(maintainableSignalText(post, replyOpportunities))
                        .suggestedAction(hasReplyOpportunity(post.getPostId(), replyOpportunities) ? "reply" : "update")
                        .reasonText(post.getReason())
                        .updatedAt(LocalDateTime.now())
                        .build())
                .toList();
    }

    private static List<CreatorGrowthWorkspaceDTO.WorkspaceActionDTO> workspaceActions(
            List<CreatorGrowthWorkspaceDTO.MaintainablePostDTO> maintainablePosts,
            List<CreatorGrowthWorkspaceDTO.CreatorReplyOpportunityDTO> replyOpportunities,
            List<CreatorGrowthWorkspaceDTO.CreatorTopicIdeaDTO> topicIdeas,
            List<CreatorCurationFeedbackDTO> curationFeedback) {
        List<CreatorGrowthWorkspaceDTO.WorkspaceActionDTO> actions = new ArrayList<>();
        if (!safeList(replyOpportunities).isEmpty()) {
            CreatorGrowthWorkspaceDTO.CreatorReplyOpportunityDTO reply = replyOpportunities.get(0);
            actions.add(CreatorGrowthWorkspaceDTO.WorkspaceActionDTO.builder()
                    .actionId("reply-" + reply.getCommentId())
                    .type("reply")
                    .label("Reply to a public discussion")
                    .href(reply.getPostId() == null ? null : "/post/" + reply.getPostId() + "#comment-" + reply.getCommentId())
                    .postId(reply.getPostId())
                    .source("public_comment")
                    .reasonText(reply.getReason())
                    .build());
        }
        if (!safeList(curationFeedback).isEmpty()) {
            CreatorCurationFeedbackDTO feedback = curationFeedback.get(0);
            actions.add(CreatorGrowthWorkspaceDTO.WorkspaceActionDTO.builder()
                    .actionId("curation-" + feedback.getContentId())
                    .type("review_curation")
                    .label("Review where a public post was included")
                    .href(feedback.getHref())
                    .postId(feedback.getContentId())
                    .source("operation-curation")
                    .reasonText(feedback.getReasonText())
                    .build());
        }
        if (!safeList(maintainablePosts).isEmpty()) {
            CreatorGrowthWorkspaceDTO.MaintainablePostDTO post = maintainablePosts.get(0);
            actions.add(CreatorGrowthWorkspaceDTO.WorkspaceActionDTO.builder()
                    .actionId("maintain-" + post.getPostId())
                    .type(post.getSuggestedAction())
                    .label("Improve a public post")
                    .href(post.getEditHref())
                    .postId(post.getPostId())
                    .source("creator_workbench")
                    .reasonText(post.getReasonText())
                    .build());
        }
        if (!safeList(topicIdeas).isEmpty()) {
            CreatorGrowthWorkspaceDTO.CreatorTopicIdeaDTO idea = topicIdeas.get(0);
            actions.add(CreatorGrowthWorkspaceDTO.WorkspaceActionDTO.builder()
                    .actionId(idea.getIdeaId())
                    .type("continue_writing")
                    .label("Continue with a public note")
                    .href("/editor?source=creator_workbench&ideaId=" + idea.getIdeaId())
                    .ideaId(idea.getIdeaId())
                    .source(idea.getSource())
                    .reasonText(idea.getReason())
                    .build());
        }
        return actions.stream()
                .collect(LinkedHashMap<String, CreatorGrowthWorkspaceDTO.WorkspaceActionDTO>::new,
                        (map, item) -> map.putIfAbsent(item.getActionId(), item),
                        LinkedHashMap::putAll)
                .values()
                .stream()
                .limit(4)
                .toList();
    }

    private static String workspaceSource(CreatorGrowthWorkspaceDTO.CreatorFeedbackSummaryDTO summary,
                                          CreatorCurationFeedbackDTO.CreatorCurationFeedbackSummaryDTO curationSummary,
                                          List<CreatorGrowthWorkspaceDTO.CreatorTopPostDTO> topPosts,
                                          List<CreatorGrowthWorkspaceDTO.CreatorReplyOpportunityDTO> replyOpportunities,
                                          List<CreatorCurationFeedbackDTO> curationFeedback) {
        if (curationSummary.isDegraded()) {
            return "fallback";
        }
        return hasPublicWorkspaceData(summary, topPosts, replyOpportunities, curationFeedback) ? "remote" : "empty";
    }

    private static String workspaceFallbackReason(CreatorGrowthWorkspaceDTO.CreatorFeedbackSummaryDTO summary,
                                                  CreatorCurationFeedbackDTO.CreatorCurationFeedbackSummaryDTO curationSummary,
                                                  CreatorGrowthWorkspaceDTO.TrustedContentDTO trustedContent,
                                                  List<CreatorGrowthWorkspaceDTO.CreatorTopPostDTO> topPosts,
                                                  List<CreatorGrowthWorkspaceDTO.CreatorReplyOpportunityDTO> replyOpportunities,
                                                  List<CreatorCurationFeedbackDTO> curationFeedback) {
        if (curationSummary.isDegraded()) {
            return curationSummary.getFallbackReason();
        }
        if (trustedContent.isDegraded()) {
            return trustedContent.getFallbackReason();
        }
        return hasPublicWorkspaceData(summary, topPosts, replyOpportunities, curationFeedback) ? null : "NO_PUBLIC_CONTENT";
    }

    private static boolean hasPublicWorkspaceData(CreatorGrowthWorkspaceDTO.CreatorFeedbackSummaryDTO summary,
                                                  List<CreatorGrowthWorkspaceDTO.CreatorTopPostDTO> topPosts,
                                                  List<CreatorGrowthWorkspaceDTO.CreatorReplyOpportunityDTO> replyOpportunities,
                                                  List<CreatorCurationFeedbackDTO> curationFeedback) {
        return window(summary, "last_30_days").getPostCount() > 0
                || !safeList(topPosts).isEmpty()
                || !safeList(replyOpportunities).isEmpty()
                || !safeList(curationFeedback).isEmpty();
    }

    private static CreatorGrowthWorkspaceDTO.FeedbackWindowDTO window(
            CreatorGrowthWorkspaceDTO.CreatorFeedbackSummaryDTO summary,
            String key) {
        return safeList(summary == null ? null : summary.getWindows()).stream()
                .filter(item -> key.equals(item.getKey()))
                .findFirst()
                .orElse(CreatorGrowthWorkspaceDTO.FeedbackWindowDTO.builder()
                        .key(key)
                        .days(30)
                        .postCount(0L)
                        .favoriteCount(0L)
                        .commentCount(0L)
                        .feedbackCount(0L)
                        .build());
    }

    private static boolean hasReplyOpportunity(
            Long postId,
            List<CreatorGrowthWorkspaceDTO.CreatorReplyOpportunityDTO> replyOpportunities) {
        return postId != null && safeList(replyOpportunities).stream()
                .anyMatch(reply -> postId.equals(reply.getPostId()));
    }

    private static String maintainableSignalText(
            CreatorGrowthWorkspaceDTO.CreatorTopPostDTO post,
            List<CreatorGrowthWorkspaceDTO.CreatorReplyOpportunityDTO> replyOpportunities) {
        if (hasReplyOpportunity(post.getPostId(), replyOpportunities)) {
            return "Recent public discussion can be answered.";
        }
        if (post.isFeatured()) {
            return "This public post has been included in a curated surface.";
        }
        long feedbackCount = post.getFeedbackCount() == null ? 0L : post.getFeedbackCount();
        return feedbackCount > 0
                ? feedbackCount + " visible feedback signals in the recent window."
                : "Public post available for a calm update.";
    }

    private List<CreatorGrowthWorkspaceDTO.CreatorTopicIdeaDTO> topicIdeasFrom(
            CreatorGrowthWorkspaceDTO.CreatorFeedbackSummaryDTO summary,
            List<CreatorGrowthWorkspaceDTO.CreatorReplyOpportunityDTO> replyOpportunities,
            List<CreatorGrowthWorkspaceDTO.PublicSeriesDTO> publicSeries,
            List<Map<String, Object>> thirtyDayRows) {
        List<CreatorGrowthWorkspaceDTO.CreatorTopicIdeaDTO> ideas = new ArrayList<>();
        Integer strongestDomain = strongestDomain(thirtyDayRows);
        if (!safeList(replyOpportunities).isEmpty()) {
            CreatorGrowthWorkspaceDTO.CreatorReplyOpportunityDTO first = replyOpportunities.get(0);
            ideas.add(CreatorGrowthWorkspaceDTO.CreatorTopicIdeaDTO.builder()
                    .ideaId("reply-" + first.getPostId())
                    .title("Answer a recent discussion")
                    .source("comment_question")
                    .reason("Turn a recent question into a follow-up note with boundaries and context.")
                    .suggestedContentType("review")
                    .jumpParams(Map.of("postId", String.valueOf(first.getPostId())))
                    .build());
        }
        if (!safeList(publicSeries).isEmpty()) {
            CreatorGrowthWorkspaceDTO.PublicSeriesDTO firstSeries = publicSeries.get(0);
            ideas.add(CreatorGrowthWorkspaceDTO.CreatorTopicIdeaDTO.builder()
                    .ideaId("series-" + firstSeries.getSeriesId())
                    .title("Continue a public series")
                    .source("series_gap")
                    .reason("Continue an existing public series with one practical follow-up.")
                    .suggestedContentType("checklist")
                    .jumpParams(Map.of("seriesId", String.valueOf(firstSeries.getSeriesId())))
                    .build());
        }
        if (strongestDomain != null) {
            ideas.add(CreatorGrowthWorkspaceDTO.CreatorTopicIdeaDTO.builder()
                    .ideaId("domain-" + strongestDomain + "-follow-up")
                    .title(domainIdeaTitle(strongestDomain))
                    .source("own_post_feedback")
                    .reason(topicReason(strongestDomain, summary))
                    .suggestedContentType(strongestDomain == INVESTMENT_DOMAIN ? "note" : "review")
                    .jumpParams(Map.of("domain", String.valueOf(strongestDomain)))
                    .build());
        }
        if (ideas.isEmpty()) {
            ideas.add(CreatorGrowthWorkspaceDTO.CreatorTopicIdeaDTO.builder()
                    .ideaId("first-public-note")
                    .title("Write a first public note")
                    .source("content_type_template")
                    .reason("Start with one public note that explains context, steps, and limits.")
                    .suggestedContentType("note")
                    .jumpParams(Map.of("template", "public_note"))
                    .build());
        }
        return ideas.stream()
                .collect(LinkedHashMap<String, CreatorGrowthWorkspaceDTO.CreatorTopicIdeaDTO>::new,
                        (map, item) -> map.putIfAbsent(item.getIdeaId(), item),
                        LinkedHashMap::putAll)
                .values()
                .stream()
                .limit(3)
                .toList();
    }

    private CreatorGrowthWorkspaceDTO.FeedbackWindowDTO feedbackWindow(String key, int days, List<Map<String, Object>> rows) {
        long postCount = sum(rows, "postCount");
        long likeCount = sum(rows, "likeCount");
        long favoriteCount = sum(rows, "favoriteCount");
        long commentCount = sum(rows, "commentCount");
        long viewCount = sum(rows, "viewCount");
        long feedbackCount = likeCount + favoriteCount + commentCount;
        return CreatorGrowthWorkspaceDTO.FeedbackWindowDTO.builder()
                .key(key)
                .days(days)
                .postCount(postCount)
                .likeCount(likeCount)
                .favoriteCount(favoriteCount)
                .commentCount(commentCount)
                .viewCount(viewCount)
                .feedbackCount(feedbackCount)
                .trendText(windowText(days, postCount, feedbackCount))
                .build();
    }

    private static String topicReason(Integer domain, CreatorGrowthWorkspaceDTO.CreatorFeedbackSummaryDTO summary) {
        if (domain != null && domain == INVESTMENT_DOMAIN) {
            return "Use a neutral framing and add risk context before extending this topic.";
        }
        long feedback = safeList(summary.getWindows()).stream()
                .filter(item -> "last_30_days".equals(item.getKey()))
                .mapToLong(item -> item.getFeedbackCount() == null ? 0L : item.getFeedbackCount())
                .findFirst()
                .orElse(0L);
        if (feedback > 0) {
            return "Recent visible feedback suggests this topic can become a clearer follow-up.";
        }
        return "A steady public note can help readers understand the topic from context to result.";
    }

    private static String topPostReason(Integer domain, long feedbackCount) {
        if (domain != null && domain == INVESTMENT_DOMAIN) {
            return "Visible feedback only; keep risk context and avoid endorsement.";
        }
        return feedbackCount > 0
                ? "Visible feedback makes this a useful candidate for follow-up."
                : "Recent public post available for creator review.";
    }

    private static String domainIdeaTitle(Integer domain) {
        if (domain != null && domain == INVESTMENT_DOMAIN) {
            return "Add context to a risk-sensitive topic";
        }
        return "Extend a topic with visible feedback";
    }

    private static String windowText(int days, long postCount, long feedbackCount) {
        if (postCount == 0) {
            return "Last " + days + " days have no public post yet.";
        }
        if (feedbackCount == 0) {
            return "Last " + days + " days have public posts but limited visible feedback.";
        }
        return "Last " + days + " days collected " + feedbackCount + " visible feedback signals.";
    }

    private static Integer strongestDomain(List<Map<String, Object>> rows) {
        return safeRows(rows).stream()
                .max(Comparator.comparingLong(row -> asLong(row.get("postCount")) * 10L + interactionCount(row)))
                .map(row -> asInteger(row.get("domain")))
                .orElse(null);
    }

    private static long interactionCount(Map<String, Object> row) {
        return asLong(row.get("likeCount")) + asLong(row.get("favoriteCount")) + asLong(row.get("commentCount"));
    }

    private static long sum(List<Map<String, Object>> rows, String key) {
        return safeRows(rows).stream().mapToLong(row -> asLong(row.get(key))).sum();
    }

    private static LocalDateTime sinceDays(int days) {
        return LocalDate.now().minusDays(days - 1L).atStartOfDay();
    }

    private static String domainName(Integer domain) {
        if (domain == null) {
            return "Technology";
        }
        return switch (domain) {
            case 2 -> "Career";
            case 3 -> "Reading";
            case 4 -> "Lifestyle";
            case 5 -> "Investment";
            default -> "Technology";
        };
    }

    private static String compact(String value, int limit) {
        if (!StringUtils.hasText(value) || value.length() <= limit) {
            return value;
        }
        return value.substring(0, Math.max(0, limit - 3)) + "...";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static LocalDateTime asDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime();
        }
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Long trustedContentMetric(Map<String, Object> row, String key) {
        if (!row.containsKey(key)) {
            return null;
        }
        Object value = row.get(key);
        if (!StringUtils.hasText(value == null ? null : String.valueOf(value))) {
            return null;
        }
        try {
            long metric = new BigDecimal(String.valueOf(value)).longValueExact();
            return metric >= 0 ? metric : null;
        } catch (NumberFormatException | ArithmeticException ignored) {
            return null;
        }
    }

    private void requireRepresentativeTable() {
        try {
            if (representativePostMapper.tableExists() > 0) {
                return;
            }
        } catch (RuntimeException ignored) {
            // handled below
        }
        throw new BizException(ErrorCode.DATABASE_ERROR.getCode(), "Representative post table is unavailable");
    }

    private static void requireUser(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static List<Map<String, Object>> safeRows(List<Map<String, Object>> rows) {
        return rows == null ? List.of() : rows;
    }

    private static <T> List<T> safeList(List<T> items) {
        return items == null ? List.of() : items;
    }

    private static List<Long> normalizePostIds(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        return postIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(REPRESENTATIVE_SAVE_LIMIT)
                .toList();
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static Long asLongObject(Object value) {
        if (value == null) {
            return null;
        }
        long number = asLong(value);
        return number <= 0 ? null : number;
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
