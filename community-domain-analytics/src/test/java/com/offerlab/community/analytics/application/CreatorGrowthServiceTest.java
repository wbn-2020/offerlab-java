package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.CreatorGrowthWorkspaceDTO;
import com.offerlab.community.analytics.api.dto.CreatorRepresentativePostCmd;
import com.offerlab.community.analytics.api.dto.CreatorCurationFeedbackDTO;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.CreatorRepresentativePostMapper;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.GrowthInsightMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
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
        assertNull(workspace.getCreatorReplyOpportunities().get(0).getCommenterUid());
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
}
