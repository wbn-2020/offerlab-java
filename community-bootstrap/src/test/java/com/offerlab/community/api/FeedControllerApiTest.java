package com.offerlab.community.api;

import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.feed.api.FeedFacade;
import com.offerlab.community.feed.api.dto.ChannelHotBoardVO;
import com.offerlab.community.feed.api.dto.FeedControlVO;
import com.offerlab.community.feed.controller.FeedController;
import com.offerlab.community.infra.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FeedControllerApiTest {
    @Mock
    private FeedFacade feedFacade;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new FeedController(feedFacade), jwtService);
    }

    @Test
    void invalidLatestDomainReturnsParamErrorBeforeFacade() throws Exception {
        mvc.perform(get("/api/v1/feeds/latest")
                        .param("domain", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verifyNoInteractions(feedFacade);
    }

    @Test
    void publicFeedDoesNotSerializeInternalPaginationMetadata() throws Exception {
        when(feedFacade.getRecommendFeed(null, null, 20, null))
                .thenReturn(PageResult.<com.offerlab.community.feed.api.dto.FeedItemVO>empty()
                        .withMetadata("mysql", true, "elasticsearch_unavailable", 200)
                        .withDiagnostic("queryPlan", "private"));

        mvc.perform(get("/api/v1/feeds/recommend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.source").doesNotExist())
                .andExpect(jsonPath("$.data.degraded").doesNotExist())
                .andExpect(jsonPath("$.data.fallbackReason").doesNotExist())
                .andExpect(jsonPath("$.data.scanLimit").doesNotExist())
                .andExpect(jsonPath("$.data.diagnostics").doesNotExist());
    }

    @Test
    void authenticatedUserCanManagePrivateFeedControls() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        FeedControlVO control = FeedControlVO.builder()
                .id(901L)
                .controlType("AUTHOR")
                .targetId(19L)
                .action("BLOCK_AUTHOR")
                .targetLabel("已屏蔽作者")
                .build();
        when(feedFacade.blockAuthor(7L, 19L)).thenReturn(control);
        when(feedFacade.listControls(eq(7L), eq(null), eq(20)))
                .thenReturn(PageResult.of(java.util.List.of(control), null, false));

        mvc.perform(post("/api/v1/feeds/author-controls")
                        .header("Authorization", "Bearer token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"authorUid\":19}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(901))
                .andExpect(jsonPath("$.data.controlType").value("AUTHOR"))
                .andExpect(jsonPath("$.data.targetId").value(19));

        mvc.perform(get("/api/v1/feeds/controls")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].controlType").value("AUTHOR"))
                .andExpect(jsonPath("$.data.items[0].targetLabel").value("已屏蔽作者"));

        mvc.perform(delete("/api/v1/feeds/author-controls/19")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/feeds/controls/901")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(feedFacade).unblockAuthor(7L, 19L);
        verify(feedFacade).deleteControl(7L, 901L);
    }

    @Test
    void publicChannelHotBoardUsesBoundedChannelReadContract() throws Exception {
        ChannelHotBoardVO board = ChannelHotBoardVO.builder()
                .domain(1)
                .ruleVersion("channel-hot.v1")
                .items(java.util.List.of())
                .build();
        when(feedFacade.getChannelHotBoard(null, 1, 20)).thenReturn(board);

        mvc.perform(get("/api/v1/feeds/channels/1/hot-board")
                        .param("size", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.domain").value(1))
                .andExpect(jsonPath("$.data.ruleVersion").value("channel-hot.v1"));

        verify(feedFacade).getChannelHotBoard(null, 1, 20);
    }

    @Test
    void feedbackForwardsCanonicalReasonCodeForAuthenticatedReader() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/feeds/feedback")
                        .header("Authorization", "Bearer token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"postId\":19,\"action\":\"HIDE\",\"reasonCode\":\"NOT_RELEVANT\"}"))
                .andExpect(status().isOk());

        verify(feedFacade).recordFeedback(7L, 19L, "HIDE", null, "NOT_RELEVANT");
    }

    @Test
    void blankFeedbackReasonCodeKeepsLegacyCompatibilityPath() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/feeds/feedback")
                        .header("Authorization", "Bearer token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"postId\":19,\"action\":\"HIDE\",\"reasonCode\":\"   \"}"))
                .andExpect(status().isOk());

        verify(feedFacade).recordFeedback(7L, 19L, "HIDE", null, "   ");
    }

    @Test
    void invalidFeedbackReasonCodeReturnsParamErrorBeforeFacade() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/feeds/feedback")
                        .header("Authorization", "Bearer token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"postId\":19,\"action\":\"HIDE\",\"reasonCode\":\"UNSUPPORTED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verifyNoInteractions(feedFacade);
    }
}
