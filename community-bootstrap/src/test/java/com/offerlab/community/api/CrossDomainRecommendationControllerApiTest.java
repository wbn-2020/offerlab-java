package com.offerlab.community.api;

import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.feed.api.FeedFacade;
import com.offerlab.community.feed.api.dto.CrossDomainRecommendationVO;
import com.offerlab.community.feed.api.dto.FeedItemVO;
import com.offerlab.community.feed.controller.CrossDomainRecommendationController;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CrossDomainRecommendationControllerApiTest {
    @Mock
    private FeedFacade feedFacade;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new CrossDomainRecommendationController(feedFacade), jwtService);
    }

    @Test
    void crossDomainRecommendationsRequireLogin() throws Exception {
        mvc.perform(get("/api/v1/recommendations/cross-domain"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(feedFacade);
    }

    @Test
    void crossDomainRecommendationsReturnExplainablePayload() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(feedFacade.getCrossDomainRecommendations(7L, null, 2)).thenReturn(PageResult.of(List.of(
                CrossDomainRecommendationVO.builder()
                        .item(FeedItemVO.builder()
                                .post(PostBriefDTO.builder()
                                        .id(4201L)
                                        .title("城市通勤经验")
                                        .domain(4)
                                        .createTime(LocalDateTime.of(2026, 6, 1, 8, 0))
                                        .build())
                                .build())
                        .sourceDomain(1)
                        .sourceDomainName("技术")
                        .targetDomain(4)
                        .targetDomainName("生活")
                        .recommendationReason("从技术成长延伸到城市生活话题，匹配你关注的通勤与租房经验")
                        .degraded(false)
                        .build()
        ), "next-1", true).withMetadata("cross-domain", false, null, null));

        mvc.perform(get("/api/v1/recommendations/cross-domain")
                        .header("Authorization", "Bearer token")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.degraded").value(false))
                .andExpect(jsonPath("$.data.source").value("cross-domain"))
                .andExpect(jsonPath("$.data.items[0].item.post.id").value(4201))
                .andExpect(jsonPath("$.data.items[0].sourceDomain").value(1))
                .andExpect(jsonPath("$.data.items[0].targetDomain").value(4))
                .andExpect(jsonPath("$.data.items[0].recommendationReason").value("从技术成长延伸到城市生活话题，匹配你关注的通勤与租房经验"))
                .andExpect(jsonPath("$.data.items[0].degraded").value(false));

        verify(feedFacade).getCrossDomainRecommendations(7L, null, 2);
    }
}
