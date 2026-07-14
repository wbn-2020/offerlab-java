package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.CreatorGrowthWorkspaceDTO;
import com.offerlab.community.analytics.api.dto.CreatorRepresentativePostCmd;
import com.offerlab.community.analytics.api.dto.CreatorCurationFeedbackDTO;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.CreatorRepresentativePostMapper;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.GrowthInsightMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentSeriesMapper;
import com.offerlab.community.post.infrastructure.persistence.po.ContentSeriesPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatorGrowthServiceTest {

    @Mock
    private GrowthInsightMapper growthInsightMapper;
    @Mock
    private ContentSeriesMapper contentSeriesMapper;
    @Mock
    private CreatorRepresentativePostMapper representativePostMapper;
    @Mock
    private CreatorCurationFeedbackService creatorCurationFeedbackService;
    @Mock
    private SnowflakeIdGenerator idGenerator;

    private CreatorGrowthService creatorGrowthService;

    @BeforeEach
    void setUp() {
        creatorGrowthService = new CreatorGrowthService(growthInsightMapper, contentSeriesMapper,
                representativePostMapper, creatorCurationFeedbackService, idGenerator);
    }

    @Test
    void workspaceBuildsCreatorFeedbackTopPostsReplyOpportunitiesRepresentativePostsAndTopicIdeas() {
        List<Map<String, Object>> sevenDayRows = List.of(Map.of(
                "domain", 1,
                "postCount", 2L,
                "featuredCount", 1L,
                "likeCount", 8L,
                "favoriteCount", 3L,
                "commentCount", 5L,
                "viewCount", 120L,
                "activeDays", 2L,
                "avgContentLength", 940L));
        List<Map<String, Object>> thirtyDayRows = List.of(Map.of(
                "domain", 1,
                "postCount", 6L,
                "featuredCount", 1L,
                "likeCount", 18L,
                "favoriteCount", 7L,
                "commentCount", 10L,
                "viewCount", 420L,
                "activeDays", 5L,
                "avgContentLength", 860L));
        when(growthInsightMapper.selectAuthorDomainStats(eq(8L), any(LocalDateTime.class)))
                .thenReturn(sevenDayRows)
                .thenReturn(thirtyDayRows)
                .thenReturn(thirtyDayRows);
        Map<String, Object> healthyTrustedContent = Map.of(
                "pendingSuggestions", 2L,
                "freshnessAwaitingConfirmation", 1L,
                "unresolvedQuestions", 3L,
                "usefulFeedback7Days", 4L,
                "usefulFeedback30Days", 8L,
                "effectiveReads7Days", 20L,
                "effectiveReads30Days", 70L);
        Map<String, Object> invalidTrustedContent = Map.of(
                "pendingSuggestions", "not-a-number",
                "freshnessAwaitingConfirmation", 0L,
                "unresolvedQuestions", 0L,
                "usefulFeedback7Days", 0L,
                "usefulFeedback30Days", 0L,
                "effectiveReads7Days", 0L,
                "effectiveReads30Days", 0L);
        when(growthInsightMapper.selectTrustedContentSummary(
                eq(8L),
                eq(Post.TYPE_COMMUNITY_QUESTION),
                any(LocalDateTime.class),
                any(LocalDateTime.class)))
                .thenReturn(healthyTrustedContent);
        when(growthInsightMapper.selectRepresentativePosts(eq(8L), any(LocalDateTime.class), eq(5))).thenReturn(List.of(
                Map.of(
                        "postId", 1001L,
                        "title", "Spring cache fallback review",
                        "domain", 1,
                        "featured", 1,
                        "interactionCount", 16L)));
        when(growthInsightMapper.selectCreatorReplyOpportunities(eq(8L), any(LocalDateTime.class), eq(5))).thenReturn(List.of(
                Map.of(
                        "commentId", 3001L,
                        "postId", 1001L,
                        "postTitle", "Spring cache fallback review",
                        "commentExcerpt", "Can you expand the rollback boundary?",
                        "likeCount", 3L,
                        "createTime", LocalDateTime.now().minusDays(1))));
        ContentSeriesPO series = new ContentSeriesPO();
        series.setId(9001L);
        series.setTitle("Cache reliability notes");
        series.setDescription("Public notes about incidents and recovery.");
        series.setDomain(1);
        series.setVisibility(1);
        when(contentSeriesMapper.tableExists()).thenReturn(1);
        when(contentSeriesMapper.selectPublicByCreatorUid(8L, 0L, 3)).thenReturn(List.of(series));
        when(creatorCurationFeedbackService.summary(8L)).thenReturn(CreatorCurationFeedbackDTO.CreatorCurationFeedbackSummaryDTO.builder()
                .updatedAt(LocalDateTime.of(2026, 7, 6, 0, 0))
                .degraded(false)
                .total(1)
                .items(List.of(CreatorCurationFeedbackDTO.builder()
                        .contentId(1001L)
                        .contentTitle("Spring cache fallback review")
                        .placementType("SLOT")
                        .placementLabel("HOME_FEATURED")
                        .reasonText("Selected for a public operation slot.")
                        .href("/post/1001")
                        .status("PUBLISHED")
                        .source("operation-curation")
                        .publicVisible(true)
                        .anonymousProtected(true)
                        .build()))
                .recentItems(List.of())
                .build());

        CreatorGrowthWorkspaceDTO workspace = creatorGrowthService.workspace(8L);

        assertEquals("remote", workspace.getSource());
        assertEquals(30, workspace.getPeriodDays());
        assertFalse(workspace.isDegraded());
        assertNull(workspace.getFallbackReason());
        assertNotNull(workspace.getTrustedContent());
        assertFalse(workspace.getTrustedContent().isDegraded());
        assertNull(workspace.getTrustedContent().getFallbackReason());
        assertEquals(2L, workspace.getTrustedContent().getPendingSuggestions());
        assertEquals(70L, workspace.getTrustedContent().getEffectiveReads30Days());
        assertEquals(6L, workspace.getSummary().getPublicPostCount());
        assertEquals(1, workspace.getSummary().getCurationInclusionCount());
        assertEquals(1, workspace.getMaintainablePosts().size());
        assertEquals("public", workspace.getMaintainablePosts().get(0).getVisibility());
        assertEquals(1, workspace.getCurationFeedback().size());
        assertFalse(workspace.getActions().isEmpty());
        assertEquals(2, workspace.getCreatorFeedbackSummary().getWindows().size());
        assertEquals("last_7_days", workspace.getCreatorFeedbackSummary().getWindows().get(0).getKey());
        assertEquals(16L, workspace.getCreatorFeedbackSummary().getWindows().get(0).getFeedbackCount());
        assertEquals("last_30_days", workspace.getCreatorFeedbackSummary().getWindows().get(1).getKey());
        assertEquals(1, workspace.getCreatorTopPosts().size());
        assertEquals(1001L, workspace.getCreatorTopPosts().get(0).getPostId());
        assertEquals("creator_only", workspace.getCreatorTopPosts().get(0).getVisibilityScope());
        assertEquals(1, workspace.getCreatorReplyOpportunities().size());
        assertEquals(3001L, workspace.getCreatorReplyOpportunities().get(0).getCommentId());
        assertEquals(1, workspace.getRepresentativePosts().size());
        assertEquals("auto_profile_candidate", workspace.getRepresentativePosts().get(0).getSource());
        assertTrue(workspace.getRepresentativePosts().get(0).isPublicVisible());
        assertEquals(1, workspace.getPublicSeries().size());
        assertTrue(workspace.getCreatorTopicIdeas().size() >= 1);
        assertTrue(workspace.getCreatorTopicIdeas().size() <= 3);
        assertTrue(workspace.getCreatorTopicIdeas().stream()
                .anyMatch(item -> item.getJumpParams().containsKey("domain")));
        assertTrue(workspace.getCreatorTopicIdeas().stream()
                .allMatch(item -> item.getTitle() != null && !item.getTitle().isBlank()));
        assertFalse(workspace.getNonPaymentIncentiveCopy().isEmpty());
        assertEquals("weekly_digest_only", workspace.getCreatorDigestNotification().getFrequency());

        doThrow(new IllegalStateException("trusted-content query failed"))
                .when(growthInsightMapper)
                .selectTrustedContentSummary(
                        eq(8L),
                        eq(Post.TYPE_COMMUNITY_QUESTION),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class));

        CreatorGrowthWorkspaceDTO queryFailedWorkspace = creatorGrowthService.workspace(8L);

        assertEquals("remote", queryFailedWorkspace.getSource());
        assertTrue(queryFailedWorkspace.isDegraded());
        assertEquals("TRUSTED_CONTENT_QUERY_FAILED", queryFailedWorkspace.getFallbackReason());
        assertTrue(queryFailedWorkspace.getTrustedContent().isDegraded());
        assertEquals("TRUSTED_CONTENT_QUERY_FAILED", queryFailedWorkspace.getTrustedContent().getFallbackReason());
        assertNull(queryFailedWorkspace.getTrustedContent().getPendingSuggestions());

        doReturn(null)
                .when(growthInsightMapper)
                .selectTrustedContentSummary(
                        eq(8L),
                        eq(Post.TYPE_COMMUNITY_QUESTION),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class));

        CreatorGrowthWorkspaceDTO missingRowWorkspace = creatorGrowthService.workspace(8L);

        assertTrue(missingRowWorkspace.isDegraded());
        assertEquals("TRUSTED_CONTENT_ROW_MISSING", missingRowWorkspace.getFallbackReason());
        assertTrue(missingRowWorkspace.getTrustedContent().isDegraded());
        assertEquals("TRUSTED_CONTENT_ROW_MISSING", missingRowWorkspace.getTrustedContent().getFallbackReason());
        assertNull(missingRowWorkspace.getTrustedContent().getPendingSuggestions());

        doReturn(invalidTrustedContent)
                .when(growthInsightMapper)
                .selectTrustedContentSummary(
                        eq(8L),
                        eq(Post.TYPE_COMMUNITY_QUESTION),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class));

        CreatorGrowthWorkspaceDTO invalidRowWorkspace = creatorGrowthService.workspace(8L);

        assertTrue(invalidRowWorkspace.isDegraded());
        assertEquals("TRUSTED_CONTENT_ROW_INVALID", invalidRowWorkspace.getFallbackReason());
        assertTrue(invalidRowWorkspace.getTrustedContent().isDegraded());
        assertEquals("TRUSTED_CONTENT_ROW_INVALID", invalidRowWorkspace.getTrustedContent().getFallbackReason());
        assertNull(invalidRowWorkspace.getTrustedContent().getPendingSuggestions());

        doThrow(new IllegalStateException("trusted-content query failed"))
                .when(growthInsightMapper)
                .selectTrustedContentSummary(
                        eq(8L),
                        eq(Post.TYPE_COMMUNITY_QUESTION),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class));
        when(creatorCurationFeedbackService.summary(8L)).thenReturn(
                CreatorCurationFeedbackDTO.CreatorCurationFeedbackSummaryDTO.builder()
                        .updatedAt(LocalDateTime.of(2026, 7, 6, 0, 0))
                        .degraded(true)
                        .fallbackReason("FEEDBACK_SOURCE_UNAVAILABLE")
                        .total(0)
                        .items(List.of())
                        .recentItems(List.of())
                        .build());

        CreatorGrowthWorkspaceDTO multiDegradedWorkspace = creatorGrowthService.workspace(8L);

        assertEquals("fallback", multiDegradedWorkspace.getSource());
        assertTrue(multiDegradedWorkspace.isDegraded());
        assertEquals("FEEDBACK_SOURCE_UNAVAILABLE", multiDegradedWorkspace.getFallbackReason());
        assertEquals("TRUSTED_CONTENT_QUERY_FAILED",
                multiDegradedWorkspace.getTrustedContent().getFallbackReason());
    }

    @Test
    void investmentTopicIdeasUseNeutralCopyWithoutProfessionalEndorsement() {
        List<Map<String, Object>> sevenDayRows = List.of(Map.of(
                "domain", 5,
                "postCount", 1L,
                "featuredCount", 0L,
                "likeCount", 2L,
                "favoriteCount", 1L,
                "commentCount", 1L,
                "viewCount", 80L,
                "activeDays", 1L,
                "avgContentLength", 500L));
        List<Map<String, Object>> thirtyDayRows = List.of(Map.of(
                "domain", 5,
                "postCount", 2L,
                "featuredCount", 0L,
                "likeCount", 4L,
                "favoriteCount", 2L,
                "commentCount", 2L,
                "viewCount", 160L,
                "activeDays", 2L,
                "avgContentLength", 520L));
        when(growthInsightMapper.selectAuthorDomainStats(eq(9L), any(LocalDateTime.class)))
                .thenReturn(sevenDayRows)
                .thenReturn(thirtyDayRows)
                .thenReturn(thirtyDayRows);
        when(growthInsightMapper.selectTrustedContentSummary(
                eq(9L),
                eq(Post.TYPE_COMMUNITY_QUESTION),
                any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(Map.of(
                        "pendingSuggestions", 0L,
                        "freshnessAwaitingConfirmation", 0L,
                        "unresolvedQuestions", 0L,
                        "usefulFeedback7Days", 0L,
                        "usefulFeedback30Days", 0L,
                        "effectiveReads7Days", 0L,
                        "effectiveReads30Days", 0L));
        when(growthInsightMapper.selectRepresentativePosts(eq(9L), any(LocalDateTime.class), eq(5))).thenReturn(List.of(
                Map.of(
                        "postId", 2001L,
                        "title", "Risk context note",
                        "domain", 5,
                        "featured", 0,
                        "interactionCount", 4L)));
        when(growthInsightMapper.selectCreatorReplyOpportunities(eq(9L), any(LocalDateTime.class), eq(5))).thenReturn(List.of());
        when(contentSeriesMapper.tableExists()).thenReturn(0);
        when(creatorCurationFeedbackService.summary(9L)).thenReturn(CreatorCurationFeedbackDTO.CreatorCurationFeedbackSummaryDTO.builder()
                .updatedAt(LocalDateTime.of(2026, 7, 6, 0, 0))
                .degraded(false)
                .total(0)
                .items(List.of())
                .recentItems(List.of())
                .build());

        CreatorGrowthWorkspaceDTO workspace = creatorGrowthService.workspace(9L);
        String allCopy = String.join(" ",
                workspace.getCreatorTopicIdeas().stream().map(CreatorGrowthWorkspaceDTO.CreatorTopicIdeaDTO::getReason).toList())
                .toLowerCase();

        assertTrue(allCopy.contains("neutral"));
        assertFalse(allCopy.contains("expert"));
        assertFalse(allCopy.contains("authority"));
        assertFalse(allCopy.contains("certified"));
        assertFalse(allCopy.contains("influence"));
        assertEquals(1, workspace.getRepresentativePosts().size());
        assertEquals("neutral_profile_candidate", workspace.getRepresentativePosts().get(0).getSource());
        assertTrue(workspace.getRepresentativePosts().get(0).getBoundaryCopy().toLowerCase().contains("risk context"));
        assertTrue(workspace.getCreatorTopPosts().get(0).getReason().toLowerCase().contains("risk context"));
        assertFalse(workspace.getTrustedContent().isDegraded());
        assertNull(workspace.getTrustedContent().getFallbackReason());
        assertEquals(0L, workspace.getTrustedContent().getPendingSuggestions());
        assertEquals(0L, workspace.getTrustedContent().getEffectiveReads30Days());
    }

    @Test
    void trustedContentDashboardLoadsOnlyTrustedContentMetricsAndTasks() {
        when(growthInsightMapper.selectTrustedContentSummary(
                eq(12L),
                eq(Post.TYPE_COMMUNITY_QUESTION),
                any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(Map.of(
                "pendingSuggestions", 2L,
                "freshnessAwaitingConfirmation", 1L,
                "unresolvedQuestions", 3L,
                "usefulFeedback7Days", 4L,
                "usefulFeedback30Days", 8L,
                "effectiveReads7Days", 20L,
                "effectiveReads30Days", 70L));
        when(growthInsightMapper.selectPendingSuggestionItems(12L)).thenReturn(List.of());
        when(growthInsightMapper.selectFreshnessItems(12L)).thenReturn(List.of());
        when(growthInsightMapper.selectPendingQuestionItems(12L, Post.TYPE_COMMUNITY_QUESTION))
                .thenReturn(List.of());

        CreatorGrowthWorkspaceDTO.TrustedContentDTO trustedContent = creatorGrowthService.trustedContent(12L);

        assertFalse(trustedContent.isDegraded());
        assertEquals(2L, trustedContent.getPendingSuggestions());
        assertEquals(70L, trustedContent.getEffectiveReads30Days());
        assertNotNull(trustedContent.getPendingSuggestionItems());
        verify(growthInsightMapper).selectTrustedContentSummary(
                eq(12L),
                eq(Post.TYPE_COMMUNITY_QUESTION),
                any(LocalDateTime.class),
                any(LocalDateTime.class));
        verify(growthInsightMapper).selectPendingSuggestionItems(12L);
        verify(growthInsightMapper).selectFreshnessItems(12L);
        verify(growthInsightMapper).selectPendingQuestionItems(12L, Post.TYPE_COMMUNITY_QUESTION);
        verify(contentSeriesMapper, never()).tableExists();
        verify(representativePostMapper, never()).tableExists();
        verify(creatorCurationFeedbackService, never()).summary(12L);
    }

    @Test
    void workspaceKeepsZeroMetricsHealthyAndCapsTrustedContentTaskLists() {
        when(growthInsightMapper.selectAuthorDomainStats(eq(10L), any(LocalDateTime.class)))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(growthInsightMapper.selectTrustedContentSummary(
                eq(10L),
                eq(Post.TYPE_COMMUNITY_QUESTION),
                any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(Map.of(
                        "pendingSuggestions", 0L,
                        "freshnessAwaitingConfirmation", 0L,
                        "unresolvedQuestions", 0L,
                        "usefulFeedback7Days", 0L,
                        "usefulFeedback30Days", 0L,
                        "effectiveReads7Days", 0L,
                        "effectiveReads30Days", 0L));
        when(growthInsightMapper.selectPendingSuggestionItems(10L)).thenReturn(trustedContentTaskRows(
                "PENDING",
                true));
        when(growthInsightMapper.selectFreshnessItems(10L)).thenReturn(trustedContentTaskRows(
                "AWAITING_AUTHOR_CONFIRMATION",
                false));
        when(growthInsightMapper.selectPendingQuestionItems(10L, Post.TYPE_COMMUNITY_QUESTION))
                .thenReturn(trustedContentTaskRows("OPEN", false));
        when(growthInsightMapper.selectRepresentativePosts(eq(10L), any(LocalDateTime.class), eq(5)))
                .thenReturn(List.of());
        when(growthInsightMapper.selectCreatorReplyOpportunities(eq(10L), any(LocalDateTime.class), eq(5)))
                .thenReturn(List.of());
        when(contentSeriesMapper.tableExists()).thenReturn(0);
        when(representativePostMapper.tableExists()).thenReturn(0);
        when(creatorCurationFeedbackService.summary(10L)).thenReturn(
                CreatorCurationFeedbackDTO.CreatorCurationFeedbackSummaryDTO.builder()
                        .degraded(false)
                        .total(0)
                        .items(List.of())
                        .recentItems(List.of())
                        .build());

        CreatorGrowthWorkspaceDTO workspace = creatorGrowthService.workspace(10L);

        assertFalse(workspace.isDegraded());
        assertEquals("empty", workspace.getSource());
        assertEquals("NO_PUBLIC_CONTENT", workspace.getFallbackReason());
        assertFalse(workspace.getTrustedContent().isDegraded());
        assertNull(workspace.getTrustedContent().getFallbackReason());
        assertEquals(0L, workspace.getTrustedContent().getPendingSuggestions());
        assertEquals(0L, workspace.getTrustedContent().getFreshnessAwaitingConfirmation());
        assertEquals(0L, workspace.getTrustedContent().getUnresolvedQuestions());
        assertEquals(0L, workspace.getTrustedContent().getUsefulFeedback7Days());
        assertEquals(0L, workspace.getTrustedContent().getUsefulFeedback30Days());
        assertEquals(0L, workspace.getTrustedContent().getEffectiveReads7Days());
        assertEquals(0L, workspace.getTrustedContent().getEffectiveReads30Days());
        assertEquals(5, workspace.getTrustedContent().getPendingSuggestionItems().size());
        assertEquals(5, workspace.getTrustedContent().getFreshnessItems().size());
        assertEquals(5, workspace.getTrustedContent().getPendingQuestionItems().size());
        assertTrue(workspace.getTrustedContent().getPendingSuggestionItems().stream()
                .allMatch(item -> "PENDING".equals(item.getStatus()) && item.getSuggestionId() != null));
        assertTrue(workspace.getTrustedContent().getFreshnessItems().stream()
                .allMatch(item -> "AWAITING_AUTHOR_CONFIRMATION".equals(item.getStatus())
                        && item.getSuggestionId() == null));
        assertTrue(workspace.getTrustedContent().getPendingQuestionItems().stream()
                .allMatch(item -> "OPEN".equals(item.getStatus()) && item.getSuggestionId() == null));
    }

    @Test
    void updateRepresentativePostsKeepsManualOrderAndVisibleBoundary() {
        List<Long> requestedPostIds = List.of(1002L, 1001L, 1002L);
        List<Long> normalizedPostIds = List.of(1002L, 1001L);
        List<Map<String, Object>> visibleRows = List.of(
                Map.of("postId", 1002L, "title", "Second public note", "domain", 1, "featured", 0, "interactionCount", 9L),
                Map.of("postId", 1001L, "title", "First public note", "domain", 1, "featured", 0, "interactionCount", 7L));
        when(representativePostMapper.tableExists()).thenReturn(1);
        when(growthInsightMapper.selectRepresentativePostsByIds(8L, normalizedPostIds))
                .thenReturn(visibleRows)
                .thenReturn(visibleRows);
        when(idGenerator.nextId()).thenReturn(501L, 502L);
        when(representativePostMapper.selectActivePostIds(8L, 5)).thenReturn(normalizedPostIds);

        List<CreatorGrowthWorkspaceDTO.RepresentativePostDTO> saved = creatorGrowthService.updateRepresentativePosts(
                8L,
                CreatorRepresentativePostCmd.builder().postIds(requestedPostIds).build());

        assertEquals(List.of(1002L, 1001L), saved.stream()
                .map(CreatorGrowthWorkspaceDTO.RepresentativePostDTO::getPostId)
                .toList());
        assertEquals("manual_profile_display", saved.get(0).getSource());
        assertTrue(saved.get(0).isPublicVisible());
        assertTrue(saved.get(0).getBoundaryCopy().contains("Profile display only"));
        verify(representativePostMapper).softDeleteByCreatorUid(8L);
        verify(representativePostMapper).upsertActivePost(501L, 8L, 1002L, 0);
        verify(representativePostMapper).upsertActivePost(502L, 8L, 1001L, 1);
    }

    @Test
    void updateRepresentativePostsRejectsPostsOutsideCreatorVisibleScope() {
        List<Long> requestedPostIds = List.of(1001L, 9999L);
        when(representativePostMapper.tableExists()).thenReturn(1);
        when(growthInsightMapper.selectRepresentativePostsByIds(8L, requestedPostIds)).thenReturn(List.of(
                Map.of("postId", 1001L, "title", "First public note", "domain", 1, "featured", 0, "interactionCount", 7L)));

        assertThrows(BizException.class, () -> creatorGrowthService.updateRepresentativePosts(
                8L,
                CreatorRepresentativePostCmd.builder().postIds(requestedPostIds).build()));

        verify(representativePostMapper, never()).softDeleteByCreatorUid(8L);
    }

    private static List<Map<String, Object>> trustedContentTaskRows(String status, boolean suggestion) {
        return java.util.stream.LongStream.rangeClosed(1L, 6L)
                .mapToObj(index -> {
                    Map<String, Object> row = new java.util.HashMap<>();
                    row.put("postId", 4000L + index);
                    row.put("postTitle", "Trusted content task " + index);
                    row.put("status", status);
                    row.put("createdAt", LocalDateTime.of(2026, 7, 13, 8, 0).plusMinutes(index));
                    row.put("updatedAt", LocalDateTime.of(2026, 7, 13, 9, 0).plusMinutes(index));
                    if (suggestion) {
                        row.put("suggestionId", 5000L + index);
                    }
                    return row;
                })
                .toList();
    }
}
