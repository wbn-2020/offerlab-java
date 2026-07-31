package com.offerlab.community.api;

import com.offerlab.community.analytics.api.dto.CreatorCurationFeedbackDTO;
import com.offerlab.community.analytics.api.dto.CreatorGrowthWorkspaceDTO;
import com.offerlab.community.analytics.api.dto.CreatorRepresentativePostCmd;
import com.offerlab.community.analytics.application.CreatorCurationFeedbackService;
import com.offerlab.community.analytics.application.CreatorGrowthService;
import com.offerlab.community.analytics.controller.CreatorGrowthController;
import com.offerlab.community.analytics.controller.TrustedContentDashboardController;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.security.JwtService;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CreatorGrowthControllerApiTest {

    @Mock
    private CreatorGrowthService creatorGrowthService;
    @Mock
    private CreatorCurationFeedbackService creatorCurationFeedbackService;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new CreatorGrowthController(creatorGrowthService, creatorCurationFeedbackService), jwtService);
    }

    @Test
    void creatorGrowthWorkspaceRequiresLogin() throws Exception {
        mvc.perform(get("/api/v1/creator-growth/workspace"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(creatorGrowthService);
    }

    @Test
    void trustedContentDashboardRequiresLogin() throws Exception {
        MockMvc trustedContentMvc = ApiTestSupport.mvc(
                new TrustedContentDashboardController(creatorGrowthService), jwtService);

        trustedContentMvc.perform(get("/api/v1/dashboard/trusted-content"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(creatorGrowthService);
    }

    @Test
    void authenticatedUserCanReadStandaloneTrustedContentDashboard() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(18L);
        when(creatorGrowthService.trustedContent(eq(18L))).thenReturn(
                CreatorGrowthWorkspaceDTO.TrustedContentDTO.builder()
                        .degraded(false)
                        .pendingSuggestions(2L)
                        .freshnessAwaitingConfirmation(1L)
                        .unresolvedQuestions(3L)
                        .usefulFeedback7Days(4L)
                        .usefulFeedback30Days(8L)
                        .effectiveReads7Days(20L)
                        .effectiveReads30Days(70L)
                        .build());
        MockMvc trustedContentMvc = ApiTestSupport.mvc(
                new TrustedContentDashboardController(creatorGrowthService), jwtService);

        trustedContentMvc.perform(get("/api/v1/dashboard/trusted-content")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.degraded").value(false))
                .andExpect(jsonPath("$.data.pendingSuggestions").value(2))
                .andExpect(jsonPath("$.data.effectiveReads30Days").value(70));

        verify(creatorGrowthService).trustedContent(18L);
    }

    @Test
    void authenticatedCreatorGrowthWorkspaceReturnsPhase10P0Contract() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(18L);
        when(creatorGrowthService.workspace(eq(18L))).thenReturn(CreatorGrowthWorkspaceDTO.builder()
                .creatorFeedbackSummary(CreatorGrowthWorkspaceDTO.CreatorFeedbackSummaryDTO.builder()
                        .headline("Last 7 days feedback is easy to compare with last 30 days.")
                        .windows(List.of(
                                CreatorGrowthWorkspaceDTO.FeedbackWindowDTO.builder()
                                        .key("last_7_days")
                                        .days(7)
                                        .feedbackCount(12L)
                                        .trendText("Last 7 days have active discussion.")
                                        .build(),
                                CreatorGrowthWorkspaceDTO.FeedbackWindowDTO.builder()
                                        .key("last_30_days")
                                        .days(30)
                                        .feedbackCount(38L)
                                        .trendText("Last 30 days show stable feedback.")
                                        .build()))
                        .build())
                .creatorTopPosts(List.of(CreatorGrowthWorkspaceDTO.CreatorTopPostDTO.builder()
                        .postId(1001L)
                        .title("Spring cache fallback review")
                        .visibilityScope("creator_only")
                        .feedbackCount(16L)
                        .build()))
                .creatorReplyOpportunities(List.of(CreatorGrowthWorkspaceDTO.CreatorReplyOpportunityDTO.builder()
                        .commentId(3001L)
                        .postId(1001L)
                        .postTitle("Spring cache fallback review")
                        .commentExcerpt("Can you expand the rollback boundary?")
                        .reason("Recent question worth a calm follow-up.")
                        .build()))
                .representativePosts(List.of(CreatorGrowthWorkspaceDTO.RepresentativePostDTO.builder()
                        .postId(1001L)
                        .title("Spring cache fallback review")
                        .source("auto_profile_candidate")
                        .publicVisible(true)
                        .boundaryCopy("Profile display only, not platform endorsement or commercial placement.")
                        .build()))
                .creatorTopicIdeas(List.of(CreatorGrowthWorkspaceDTO.CreatorTopicIdeaDTO.builder()
                        .ideaId("domain-1-follow-up")
                        .title("Extend a topic with visible feedback")
                        .source("own_post_feedback")
                        .reason("Turn recent discussion into a follow-up note.")
                        .suggestedContentType("review")
                        .jumpParams(Map.of("domain", "1"))
                        .build()))
                .creatorDigestNotification(CreatorGrowthWorkspaceDTO.CreatorDigestNotificationDTO.builder()
                        .frequency("weekly_digest_only")
                        .copy("Low-frequency creator digest, delivered only through existing preferences.")
                        .build())
                .nonPaymentIncentiveCopy(List.of("Use representative posts and public series as profile display."))
                .build());

        mvc.perform(get("/api/v1/creator-growth/workspace")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.creatorFeedbackSummary.windows[0].key").value("last_7_days"))
                .andExpect(jsonPath("$.data.creatorFeedbackSummary.windows[1].key").value("last_30_days"))
                .andExpect(jsonPath("$.data.creatorTopPosts[0].visibilityScope").value("creator_only"))
                .andExpect(jsonPath("$.data.creatorReplyOpportunities[0].commenterUid").doesNotExist())
                .andExpect(jsonPath("$.data.representativePosts[0].source").value("auto_profile_candidate"))
                .andExpect(jsonPath("$.data.creatorTopicIdeas[0].source").value("own_post_feedback"))
                .andExpect(jsonPath("$.data.creatorTopicIdeas[0].jumpParams.domain").value("1"))
                .andExpect(jsonPath("$.data.creatorDigestNotification.frequency").value("weekly_digest_only"));

        verify(creatorGrowthService).workspace(18L);
    }

    @Test
    void authenticatedCreatorCanReadCurationFeedback() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(18L);
        when(creatorCurationFeedbackService.summary(eq(18L))).thenReturn(
                CreatorCurationFeedbackDTO.CreatorCurationFeedbackSummaryDTO.builder()
                        .degraded(false)
                        .fallbackReason(null)
                        .total(1)
                        .items(List.of(CreatorCurationFeedbackDTO.builder()
                                .contentId(1001L)
                                .contentTitle("Spring cache fallback review")
                                .placementType("SLOT")
                                .placementId(2001L)
                                .placementLabel("HOME_FEATURED")
                                .reasonText("Selected for a public operation slot.")
                                .href("/api/v1/creator-growth/curation-feedback?placementType=SLOT&placementKey=HOME_FEATURED")
                                .status("PUBLISHED")
                                .source("operation-curation")
                                .build()))
                        .recentItems(List.of())
                        .build());

        mvc.perform(get("/api/v1/creator-growth/curation-feedback")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].contentId").value(1001L))
                .andExpect(jsonPath("$.data.items[0].placementType").value("SLOT"))
                .andExpect(jsonPath("$.data.items[0].placementLabel").value("HOME_FEATURED"))
                .andExpect(jsonPath("$.data.items[0].reasonText").value("Selected for a public operation slot."))
                .andExpect(jsonPath("$.data.items[0].href").value("/api/v1/creator-growth/curation-feedback?placementType=SLOT&placementKey=HOME_FEATURED"))
                .andExpect(jsonPath("$.data.items[0].status").value("PUBLISHED"));

        verify(creatorCurationFeedbackService).summary(18L);
    }

    @Test
    void authenticatedCreatorCanUpdateRepresentativePosts() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(18L);
        when(creatorGrowthService.updateRepresentativePosts(eq(18L), org.mockito.ArgumentMatchers.any(CreatorRepresentativePostCmd.class)))
                .thenReturn(List.of(CreatorGrowthWorkspaceDTO.RepresentativePostDTO.builder()
                        .postId(1002L)
                        .title("Second public note")
                        .source("manual_profile_display")
                        .publicVisible(true)
                        .boundaryCopy("Profile display only, not platform endorsement or commercial placement.")
                        .build()));

        mvc.perform(put("/api/v1/creator-growth/representative-posts")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postIds": [1002, 1001]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].postId").value(1002L))
                .andExpect(jsonPath("$.data[0].source").value("manual_profile_display"))
                .andExpect(jsonPath("$.data[0].publicVisible").value(true));

        verify(creatorGrowthService).updateRepresentativePosts(eq(18L), org.mockito.ArgumentMatchers.any(CreatorRepresentativePostCmd.class));
    }
}
