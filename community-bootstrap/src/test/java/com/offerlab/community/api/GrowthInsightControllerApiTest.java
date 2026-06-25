package com.offerlab.community.api;

import com.offerlab.community.analytics.api.dto.GrowthProfileDTO;
import com.offerlab.community.analytics.api.dto.GrowthReportDTO;
import com.offerlab.community.analytics.application.GrowthInsightService;
import com.offerlab.community.analytics.controller.GrowthInsightController;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.post.domain.model.PostDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GrowthInsightControllerApiTest {

    @Mock
    private GrowthInsightService growthInsightService;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new GrowthInsightController(growthInsightService), jwtService);
    }

    @Test
    void growthProfileRequiresLogin() throws Exception {
        mvc.perform(get("/api/v1/growth/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(growthInsightService);
    }

    @Test
    void authenticatedGrowthProfileReturnsExplainableDimensions() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(18L);
        when(growthInsightService.profile(eq(18L), eq(30))).thenReturn(GrowthProfileDTO.builder()
                .days(30)
                .degraded(true)
                .degradationReasons(List.of("growth events fallback"))
                .strongestDomain("Technology")
                .emergingDomain("Career")
                .nextFocus("Continue turning engineering writeups into a reusable series.")
                .domains(List.of(
                        GrowthProfileDTO.DomainProfileDTO.builder()
                                .domain(1)
                                .domainName(PostDomain.fromCode(1).getDisplayName())
                                .postCount(4L)
                                .seriesCount(1L)
                                .activeDays(3L)
                                .interactionCount(27L)
                                .viewCount(180L)
                                .dimensions(List.of(
                                        GrowthProfileDTO.DimensionDTO.builder()
                                                .key("activity")
                                                .label("Activity")
                                                .score(66)
                                                .explanation("Published 4 posts across 3 active days")
                                                .build(),
                                        GrowthProfileDTO.DimensionDTO.builder()
                                                .key("depth")
                                                .label("Depth")
                                                .score(74)
                                                .explanation("Already grouped into a series with long-form content")
                                                .build()))
                                .representativePosts(List.of(
                                        GrowthProfileDTO.ReferencePostDTO.builder()
                                                .postId(1001L)
                                                .title("Spring Cloud incident review")
                                                .domain(1)
                                                .heat(42L)
                                                .featured(true)
                                                .build()))
                                .build()))
                .build());

        mvc.perform(get("/api/v1/growth/profile")
                        .header("Authorization", "Bearer token")
                        .param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.days").value(30))
                .andExpect(jsonPath("$.data.degraded").value(true))
                .andExpect(jsonPath("$.data.domains[0].domain").value(1))
                .andExpect(jsonPath("$.data.domains[0].dimensions[0].key").value("activity"))
                .andExpect(jsonPath("$.data.domains[0].representativePosts[0].postId").value(1001L));

        verify(growthInsightService).profile(18L, 30);
    }

    @Test
    void authenticatedGrowthReportReturnsPeriodSummary() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(21L);
        when(growthInsightService.report(eq(21L), eq("weekly"))).thenReturn(GrowthReportDTO.builder()
                .period("weekly")
                .days(7)
                .degraded(false)
                .publishedPostCount(2L)
                .interactionCount(18L)
                .featuredPostCount(1L)
                .seriesContributionCount(1L)
                .nextActions(List.of("Move this week's engineering reviews into the series workbench."))
                .domainChanges(List.of(
                        GrowthReportDTO.DomainChangeDTO.builder()
                                .domain(1)
                                .domainName(PostDomain.fromCode(1).getDisplayName())
                                .currentPostCount(2L)
                                .previousPostCount(1L)
                                .trend("up")
                                .reason("Engineering output increased in this window.")
                                .build()))
                .highlightPosts(List.of(
                        GrowthReportDTO.HighlightPostDTO.builder()
                                .postId(2002L)
                                .title("Redis cache fallback review")
                                .domain(1)
                                .domainName(PostDomain.fromCode(1).getDisplayName())
                                .interactionCount(12L)
                                .featured(false)
                                .build()))
                .build());

        mvc.perform(get("/api/v1/growth/report")
                        .header("Authorization", "Bearer token")
                        .param("period", "weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.period").value("weekly"))
                .andExpect(jsonPath("$.data.days").value(7))
                .andExpect(jsonPath("$.data.domainChanges[0].trend").value("up"))
                .andExpect(jsonPath("$.data.highlightPosts[0].postId").value(2002L));

        verify(growthInsightService).report(21L, "weekly");
    }
}
