package com.offerlab.community.api;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.question.api.dto.MockInterviewAnswerDTO;
import com.offerlab.community.question.api.dto.MockInterviewSessionDTO;
import com.offerlab.community.question.application.MockInterviewService;
import com.offerlab.community.question.controller.MockInterviewController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MockInterviewControllerApiTest {
    @Mock
    private MockInterviewService mockInterviewService;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new MockInterviewController(mockInterviewService), jwtService);
    }

    @Test
    void startRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/mock-interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":3}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(mockInterviewService);
    }

    @Test
    void submitRejectsInvalidBodyBeforeCallingService() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(42L);

        mvc.perform(post("/api/v1/mock-interviews/7/submit")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verifyNoInteractions(mockInterviewService);
    }

    @Test
    void detailReturnsBusinessErrorForOtherUsersSession() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(42L);
        when(mockInterviewService.get(42L, 7L)).thenThrow(new BizException(ErrorCode.RESOURCE_NOT_FOUND));

        mvc.perform(get("/api/v1/mock-interviews/7")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()));

        verify(mockInterviewService).get(42L, 7L);
    }

    @Test
    void detailExposesAiReviewTransparencyFields() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(42L);
        when(mockInterviewService.get(42L, 7L)).thenReturn(MockInterviewSessionDTO.builder()
                .id(7L)
                .status("completed")
                .answers(List.of(MockInterviewAnswerDTO.builder()
                        .id(100L)
                        .sessionId(7L)
                        .questionId(9L)
                        .sequenceNo(1)
                        .answerText("answer")
                        .score(4)
                        .aiReviewed(true)
                        .aiReviewStatus("SUCCEEDED")
                        .aiReviewProvider("deepseek")
                        .aiReviewTaskId("mock-review-test")
                        .aiReviewFallbackUsed(false)
                        .aiReviewDurationMs(321L)
                        .aiReviewPromptTokens(120)
                        .aiReviewCompletionTokens(45)
                        .aiReviewEstimatedCostMicros(99L)
                        .aiReviewErrorCode(null)
                        .build()))
                .build());

        mvc.perform(get("/api/v1/mock-interviews/7")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answers[0].aiReviewProvider").value("deepseek"))
                .andExpect(jsonPath("$.data.answers[0].aiReviewTaskId").value("mock-review-test"))
                .andExpect(jsonPath("$.data.answers[0].aiReviewFallbackUsed").value(false))
                .andExpect(jsonPath("$.data.answers[0].aiReviewDurationMs").value(321))
                .andExpect(jsonPath("$.data.answers[0].aiReviewPromptTokens").value(120))
                .andExpect(jsonPath("$.data.answers[0].aiReviewCompletionTokens").value(45))
                .andExpect(jsonPath("$.data.answers[0].aiReviewEstimatedCostMicros").value(99));

        verify(mockInterviewService).get(42L, 7L);
    }
}
