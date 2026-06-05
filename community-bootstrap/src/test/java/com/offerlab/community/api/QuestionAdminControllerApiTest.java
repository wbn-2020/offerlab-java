package com.offerlab.community.api;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.exception.SystemException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.question.api.dto.AiTaskMetricsDTO;
import com.offerlab.community.question.application.QuestionFacade;
import com.offerlab.community.question.application.QuestionIndexTaskService;
import com.offerlab.community.question.controller.QuestionAdminController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class QuestionAdminControllerApiTest {
    @Mock
    private QuestionFacade questionFacade;
    @Mock
    private AdminPermissionService adminPermissionService;
    @Mock
    private AdminAuditService adminAuditService;
    @Mock
    private QuestionIndexTaskService questionIndexTaskService;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(
                new QuestionAdminController(questionFacade, adminPermissionService, adminAuditService, questionIndexTaskService),
                jwtService);
    }

    @Test
    void questionOperatorCanStartManualExtractTaskWithAudit() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(questionFacade.extractPostQuestions(123L, true)).thenReturn(9001L);

        mvc.perform(post("/api/v1/admin/posts/123/extract-questions")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(9001));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(questionFacade).extractPostQuestions(123L, true);
        verify(adminAuditService).recordRequired(7L, "POST_QUESTION_EXTRACT_TASK", "AI_TASK", 9001L,
                Map.of("postId", 123L, "manual", true),
                Map.of("taskId", 9001L, "postId", 123L, "manual", true), null);
    }

    @Test
    void invalidManualExtractPostIdReturns400BeforeSideEffects() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/admin/posts/0/extract-questions")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verifyNoInteractions(adminPermissionService, questionFacade, adminAuditService);
    }

    @Test
    void ordinaryUserCannotStartQuestionIndexRebuildTask() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(11L);
        doThrow(new BizException(ErrorCode.FORBIDDEN))
                .when(adminPermissionService)
                .requireScope(11L, AdminPermissionService.ROLE_QUESTION_OPERATOR);

        mvc.perform(post("/api/v1/admin/questions/rebuild-index-task")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        verifyNoInteractions(questionIndexTaskService);
        verifyNoInteractions(adminAuditService);
    }

    @Test
    void questionIndexRebuildTaskFailsClosedWhenAuditIsNotWritable() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        doThrow(new SystemException("audit unavailable"))
                .when(adminAuditService)
                .requireWritable("QUESTION_INDEX_REBUILD_TASK", "QUESTION_INDEX", null);

        mvc.perform(post("/api/v1/admin/questions/rebuild-index-task")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(adminAuditService).requireWritable("QUESTION_INDEX_REBUILD_TASK", "QUESTION_INDEX", null);
        verifyNoInteractions(questionIndexTaskService);
    }

    @Test
    void adminCanQueryQuestionIndexRebuildTaskStatus() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(questionIndexTaskService.getTask("task-1")).thenReturn(QuestionIndexTaskService.QuestionIndexTask.builder()
                .taskId("task-1")
                .type("QUESTION_INDEX_REBUILD")
                .status("FAILED")
                .operatorUid(7L)
                .accepted(false)
                .indexed(2)
                .failed(1)
                .total(3)
                .message("index failed")
                .retryable(true)
                .build());

        mvc.perform(get("/api/v1/admin/questions/index-tasks/task-1")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value("task-1"))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.retryable").value(true));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(questionIndexTaskService).getTask("task-1");
        verifyNoInteractions(adminAuditService);
    }

    @Test
    void questionOperatorCanQueryAiTaskMetrics() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(questionFacade.getTaskMetrics(100)).thenReturn(AiTaskMetricsDTO.builder()
                .totalTasks(5)
                .successCount(3)
                .failedCount(1)
                .runningCount(1)
                .fallbackCount(2)
                .fallbackRate(0.4D)
                .avgDurationMs(1200L)
                .p95DurationMs(2300L)
                .totalPromptTokens(1000L)
                .totalCompletionTokens(500L)
                .totalTokens(1500L)
                .estimatedCostMicros(42L)
                .providerStats(List.of(AiTaskMetricsDTO.BucketDTO.builder()
                        .name("deepseek")
                        .count(3)
                        .fallbackCount(0)
                        .avgDurationMs(900L)
                        .totalTokens(1200L)
                        .estimatedCostMicros(42L)
                        .build()))
                .errorStats(List.of(AiTaskMetricsDTO.BucketDTO.builder()
                        .name("DEEPSEEK_TIMEOUT")
                        .count(1)
                        .fallbackCount(1)
                        .avgDurationMs(0L)
                        .totalTokens(0L)
                        .estimatedCostMicros(0L)
                        .build()))
                .build());

        mvc.perform(get("/api/v1/admin/ai-tasks/metrics?limit=100")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalTasks").value(5))
                .andExpect(jsonPath("$.data.fallbackCount").value(2))
                .andExpect(jsonPath("$.data.p95DurationMs").value(2300))
                .andExpect(jsonPath("$.data.totalTokens").value(1500))
                .andExpect(jsonPath("$.data.estimatedCostMicros").value(42))
                .andExpect(jsonPath("$.data.providerStats[0].name").value("deepseek"))
                .andExpect(jsonPath("$.data.errorStats[0].name").value("DEEPSEEK_TIMEOUT"));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(questionFacade).getTaskMetrics(100);
        verifyNoInteractions(adminAuditService);
    }

    @Test
    void ordinaryUserCannotQueryQuestionIndexRebuildTaskStatus() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(11L);
        doThrow(new BizException(ErrorCode.FORBIDDEN))
                .when(adminPermissionService)
                .requireScope(11L, AdminPermissionService.ROLE_QUESTION_OPERATOR);

        mvc.perform(get("/api/v1/admin/questions/index-tasks/task-1")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        verifyNoInteractions(questionIndexTaskService);
        verifyNoInteractions(adminAuditService);
    }

    @Test
    void emptyQuestionBatchReviewIdsReturn400() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/admin/questions/batch-review")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[],\"status\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verifyNoInteractions(adminPermissionService, questionFacade, adminAuditService);
    }

    @Test
    void invalidQuestionBatchReviewStatusReturns400() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/admin/questions/batch-review")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1],\"status\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verifyNoInteractions(adminPermissionService, questionFacade, adminAuditService);
    }

    @Test
    void nullQuestionUpdateBodyReturns400BeforeSideEffects() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/admin/questions/42")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verifyNoInteractions(adminPermissionService, questionFacade, adminAuditService);
    }

    @Test
    void emptyQuestionUpdatePayloadReturns400BeforeSideEffects() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        for (String body : List.of("{}", "{\"remark\":\"needs review\"}")) {
            mvc.perform(post("/api/v1/admin/questions/42")
                            .header("Authorization", "Bearer token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
        }

        verifyNoInteractions(adminPermissionService, questionFacade, adminAuditService);
    }

    @Test
    void ordinaryUserCannotBatchReviewQuestions() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(11L);
        doThrow(new BizException(ErrorCode.FORBIDDEN))
                .when(adminPermissionService)
                .requireScope(11L, AdminPermissionService.ROLE_QUESTION_OPERATOR);

        mvc.perform(post("/api/v1/admin/questions/batch-review")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2],\"status\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        verifyNoInteractions(questionFacade, adminAuditService);
    }

    @Test
    void questionOperatorCanBatchReviewQuestions() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(questionFacade.reviewQuestion(1L, 1)).thenReturn(Map.of("questionId", 1L, "status", 1));
        when(questionFacade.reviewQuestion(2L, 1)).thenReturn(Map.of("questionId", 2L, "status", 1));

        mvc.perform(post("/api/v1/admin/questions/batch-review")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2,1],\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.requested").value(2))
                .andExpect(jsonPath("$.data.reviewed").value(2))
                .andExpect(jsonPath("$.data.status").value(1));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(questionFacade).reviewQuestion(1L, 1);
        verify(questionFacade).reviewQuestion(2L, 1);
        verify(adminAuditService).record(7L, "QUESTION_REVIEW_BATCH", "QUESTION", null,
                Map.of("ids", List.of(1L, 2L), "status", 1), Map.of("requested", 2, "reviewed", 2, "status", 1), null);
    }
}
