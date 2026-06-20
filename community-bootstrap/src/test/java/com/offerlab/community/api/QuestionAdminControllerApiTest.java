package com.offerlab.community.api;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.exception.SystemException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.ops.AdminOperationIdempotencyService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.question.api.dto.AiTaskDTO;
import com.offerlab.community.question.api.dto.AiTaskDetailDTO;
import com.offerlab.community.question.api.dto.AiTaskMetricsDTO;
import com.offerlab.community.question.api.dto.CompanyAliasCmd;
import com.offerlab.community.question.api.dto.CompanyAliasDTO;
import com.offerlab.community.question.api.dto.QuestionAdminUpdateCmd;
import com.offerlab.community.question.api.dto.QuestionDTO;
import com.offerlab.community.question.api.dto.QuestionDuplicateGroupDTO;
import com.offerlab.community.question.application.QuestionConstants;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private AdminOperationIdempotencyService idempotencyService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        idempotencyService = new AdminOperationIdempotencyService();
        mvc = ApiTestSupport.mvc(
                new QuestionAdminController(questionFacade, adminPermissionService, adminAuditService, questionIndexTaskService, idempotencyService),
                jwtService);
    }

    private String previewNonce(String operation, List<?> ids) {
        return idempotencyService.issuePreview(7L, operation, ids);
    }

    @Test
    void questionOperatorCanStartManualExtractTaskWithAudit() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(questionFacade.extractPostQuestions(123L, true)).thenReturn(9001L);

        mvc.perform(post("/api/v1/admin/posts/123/extract-questions")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"manual extract after post update\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(9001));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(questionFacade).extractPostQuestions(123L, true);
        verify(adminAuditService).recordRequired(7L, "POST_QUESTION_EXTRACT_TASK", "AI_TASK", 9001L,
                Map.of("postId", 123L, "manual", true),
                Map.of("taskId", 9001L, "postId", 123L, "manual", true), "manual extract after post update");
    }

    @Test
    void manualExtractRequiresServerSideRemarkBeforeSideEffects() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        for (String body : List.of("{}", "{\"remark\":\"   \"}")) {
            mvc.perform(post("/api/v1/admin/posts/123/extract-questions")
                            .header("Authorization", "Bearer token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
        }

        verify(adminPermissionService, times(2)).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verifyNoInteractions(adminAuditService, questionFacade);
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
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"rebuild question index\",\"confirmationPhrase\":\"CONFIRM\"}"))
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
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"rebuild question index\",\"confirmationPhrase\":\"CONFIRM\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(adminAuditService).requireWritable("QUESTION_INDEX_REBUILD_TASK", "QUESTION_INDEX", null);
        verifyNoInteractions(questionIndexTaskService);
    }

    @Test
    void questionIndexRebuildTaskRequiresServerSideRiskConfirmation() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        for (String body : List.of(
                "{}",
                "{\"remark\":\"rebuild question index\"}",
                "{\"remark\":\"rebuild question index\",\"confirmationPhrase\":\"WRONG\"}"
        )) {
            mvc.perform(post("/api/v1/admin/questions/rebuild-index-task")
                            .header("Authorization", "Bearer token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
        }

        verify(adminPermissionService, times(3)).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verifyNoInteractions(adminAuditService, questionIndexTaskService);
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
    void questionOperatorCanListAiTasksWithOperationalFields() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(questionFacade.listTasks(2, 50)).thenReturn(List.of(AiTaskDTO.builder()
                .id(501L)
                .postId(123L)
                .taskType("POST_QUESTION_EXTRACT")
                .taskStatus(2)
                .retryCount(1)
                .questionCount(4)
                .provider("rule")
                .fallbackUsed(true)
                .totalTokens(0)
                .estimatedCostMicros(0L)
                .build()));

        mvc.perform(get("/api/v1/admin/ai-tasks?status=2&limit=50")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(501))
                .andExpect(jsonPath("$.data[0].taskStatus").value(2))
                .andExpect(jsonPath("$.data[0].questionCount").value(4))
                .andExpect(jsonPath("$.data[0].fallbackUsed").value(true))
                .andExpect(jsonPath("$.data[0].provider").value("rule"));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(questionFacade).listTasks(2, 50);
        verifyNoInteractions(adminAuditService);
    }

    @Test
    void questionOperatorCanReadAiTaskDetailWithOperationalFields() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        AiTaskDTO task = AiTaskDTO.builder()
                .id(501L)
                .postId(123L)
                .taskType("POST_QUESTION_EXTRACT")
                .taskStatus(2)
                .questionCount(4)
                .provider("rule")
                .fallbackUsed(false)
                .build();
        AiTaskDTO retry = AiTaskDTO.builder()
                .id(502L)
                .postId(123L)
                .taskType("POST_QUESTION_EXTRACT")
                .taskStatus(3)
                .questionCount(0)
                .provider("deepseek")
                .fallbackUsed(true)
                .build();
        when(questionFacade.getTaskDetail(501L)).thenReturn(AiTaskDetailDTO.builder()
                .task(task)
                .sourcePostId(123L)
                .sourcePostTitle("Java interview recap")
                .retryRecords(List.of(retry))
                .build());

        mvc.perform(get("/api/v1/admin/ai-tasks/501")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.task.id").value(501))
                .andExpect(jsonPath("$.data.task.taskStatus").value(2))
                .andExpect(jsonPath("$.data.task.questionCount").value(4))
                .andExpect(jsonPath("$.data.retryRecords[0].taskStatus").value(3))
                .andExpect(jsonPath("$.data.retryRecords[0].questionCount").value(0));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(questionFacade).getTaskDetail(501L);
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
    void aiTaskRetryRequiresCriticalConfirmationAndIdempotencyBeforeSideEffects() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/admin/ai-tasks/501/retry")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"retry failed AI task\",\"confirmationPhrase\":\"CONFIRM\",\"idempotencyKey\":\"ai-retry-key-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(questionFacade, never()).retryTask(501L);
        verifyNoInteractions(adminAuditService);
    }

    @Test
    void questionIndexTaskRetryRequiresCriticalConfirmationAndIdempotencyBeforeSideEffects() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/admin/questions/index-tasks/task-1/retry")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"retry failed question index task\",\"confirmationPhrase\":\"CONFIRM\",\"idempotencyKey\":\"index-retry-key-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(questionIndexTaskService, never()).retryTask("task-1");
        verifyNoInteractions(adminAuditService);
    }

    @Test
    void aiTaskRetryPreviewAndExecuteUsePreviewBoundIdempotencyKey() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(questionFacade.getTaskDetail(501L)).thenReturn(AiTaskDetailDTO.builder()
                .task(AiTaskDTO.builder()
                        .id(501L)
                        .postId(123L)
                        .taskStatus(QuestionConstants.TASK_FAILED)
                        .retryCount(1)
                        .build())
                .sourcePostId(123L)
                .sourcePostTitle("Java interview recap")
                .build());
        AiTaskDTO retried = AiTaskDTO.builder()
                .id(501L)
                .postId(123L)
                .taskStatus(QuestionConstants.TASK_PENDING)
                .retryCount(2)
                .build();
        when(questionFacade.retryTask(501L)).thenReturn(retried);

        mvc.perform(post("/api/v1/admin/ai-tasks/501/retry/preview")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.operation").value("AI_TASK_RETRY"))
                .andExpect(jsonPath("$.data.previewNonce").isNotEmpty())
                .andExpect(jsonPath("$.data.confirmationPhrase").value("CONFIRM"))
                .andExpect(jsonPath("$.data.items[0].eligible").value(true));

        String nonce = previewNonce("AI_TASK_RETRY", List.of(501L));
        mvc.perform(post("/api/v1/admin/ai-tasks/501/retry")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"retry failed AI task\",\"confirmationPhrase\":\"CONFIRM\",\"idempotencyKey\":\"ai-retry-key-1\",\"previewNonce\":\"" + nonce + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(501));

        verify(adminPermissionService, times(2)).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(questionFacade).getTaskDetail(501L);
        verify(adminAuditService).requireWritable("AI_TASK_RETRY", "AI_TASK", 501L);
        verify(questionFacade).retryTask(501L);
        verify(adminAuditService).recordRequired(7L, "AI_TASK_RETRY", "AI_TASK", 501L, null, retried,
                "retry failed AI task");
    }

    @Test
    void questionIndexTaskRetryPreviewAndExecuteUsePreviewBoundIdempotencyKey() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(questionIndexTaskService.getTask("task-1")).thenReturn(QuestionIndexTaskService.QuestionIndexTask.builder()
                .taskId("task-1")
                .type("QUESTION_INDEX_REBUILD")
                .status("FAILED")
                .operatorUid(7L)
                .retryable(true)
                .indexed(2)
                .failed(1)
                .total(3)
                .message("index failed")
                .build());
        QuestionIndexTaskService.QuestionIndexTask retried = QuestionIndexTaskService.QuestionIndexTask.builder()
                .taskId("task-1")
                .type("QUESTION_INDEX_REBUILD")
                .status("PENDING")
                .operatorUid(7L)
                .retryable(false)
                .build();
        when(questionIndexTaskService.retryTask("task-1")).thenReturn(retried);

        mvc.perform(post("/api/v1/admin/questions/index-tasks/task-1/retry/preview")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.operation").value("QUESTION_INDEX_REBUILD_TASK_RETRY"))
                .andExpect(jsonPath("$.data.previewNonce").isNotEmpty())
                .andExpect(jsonPath("$.data.confirmationPhrase").value("CONFIRM"))
                .andExpect(jsonPath("$.data.items[0].eligible").value(true));

        String nonce = previewNonce("QUESTION_INDEX_REBUILD_TASK_RETRY", List.of("task-1"));
        mvc.perform(post("/api/v1/admin/questions/index-tasks/task-1/retry")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"retry failed question index task\",\"confirmationPhrase\":\"CONFIRM\",\"idempotencyKey\":\"index-retry-key-1\",\"previewNonce\":\"" + nonce + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value("task-1"));

        verify(adminPermissionService, times(2)).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(questionIndexTaskService).getTask("task-1");
        verify(adminAuditService).requireWritable("QUESTION_INDEX_REBUILD_TASK_RETRY", "QUESTION_INDEX", "task-1");
        verify(questionIndexTaskService).retryTask("task-1");
        verify(adminAuditService).recordRequired(7L, "QUESTION_INDEX_REBUILD_TASK_RETRY", "QUESTION_INDEX", "task-1",
                null, retried, "retry failed question index task");
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
    void questionUpdateRequiresServerSideRiskRemarkBeforeSideEffects() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        for (String body : List.of(
                "{\"questionText\":\"updated question text\"}",
                "{\"questionText\":\"updated question text\",\"remark\":\"   \"}"
        )) {
            mvc.perform(post("/api/v1/admin/questions/42")
                            .header("Authorization", "Bearer token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
        }

        verify(adminPermissionService, times(2)).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verifyNoInteractions(questionFacade, adminAuditService);
    }

    @Test
    void questionUpdateFailsClosedWhenAuditIsNotWritable() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        doThrow(new SystemException("audit unavailable"))
                .when(adminAuditService)
                .requireWritable("QUESTION_UPDATE", "QUESTION", 42L);

        mvc.perform(post("/api/v1/admin/questions/42")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionText\":\"updated question text\",\"remark\":\"fix low quality answer\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(adminAuditService).requireWritable("QUESTION_UPDATE", "QUESTION", 42L);
        verifyNoInteractions(questionFacade);
    }

    @Test
    void questionOperatorCanUpdateQuestionWithAuditedRiskRemark() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        QuestionDTO dto = QuestionDTO.builder()
                .id(42L)
                .questionText("updated question text")
                .status(1)
                .build();
        when(questionFacade.updateQuestionAdmin(eq(42L), any(QuestionAdminUpdateCmd.class))).thenReturn(dto);

        mvc.perform(post("/api/v1/admin/questions/42")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionText\":\"updated question text\",\"remark\":\"fix low quality answer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.questionText").value("updated question text"));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(adminAuditService).requireWritable("QUESTION_UPDATE", "QUESTION", 42L);
        verify(questionFacade).updateQuestionAdmin(eq(42L), any(QuestionAdminUpdateCmd.class));
        verify(adminAuditService).recordRequired(7L, "QUESTION_UPDATE", "QUESTION", 42L, null, dto,
                "fix low quality answer");
    }

    @Test
    void duplicateGovernanceRequiresCriticalRiskConfirmationBeforeSideEffects() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/admin/questions/42/duplicates/canonical")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canonicalQuestionId\":99,\"remark\":\"choose canonical question\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
        mvc.perform(post("/api/v1/admin/questions/42/duplicates/merge-candidate")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateQuestionId\":100,\"remark\":\"merge semantic duplicate\",\"confirmationPhrase\":\"WRONG\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
        mvc.perform(post("/api/v1/admin/questions/42/duplicates/hide")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[100,101],\"confirmationPhrase\":\"CONFIRM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verify(adminPermissionService, times(3)).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verifyNoInteractions(questionFacade, adminAuditService);
    }

    @Test
    void duplicateCanonicalFailsClosedWhenAuditIsNotWritable() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        doThrow(new SystemException("audit unavailable"))
                .when(adminAuditService)
                .requireWritable("QUESTION_DUPLICATE_CANONICAL", "QUESTION", 42L);

        mvc.perform(post("/api/v1/admin/questions/42/duplicates/canonical")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canonicalQuestionId\":99,\"remark\":\"choose canonical question\",\"confirmationPhrase\":\"CONFIRM\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(adminAuditService).requireWritable("QUESTION_DUPLICATE_CANONICAL", "QUESTION", 42L);
        verifyNoInteractions(questionFacade);
    }

    @Test
    void questionOperatorCanSetDuplicateCanonicalWithAuditedCriticalRemark() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        QuestionDuplicateGroupDTO dto = QuestionDuplicateGroupDTO.builder()
                .questionId(42L)
                .canonicalId(99L)
                .questionCount(2)
                .sourcePostCount(2)
                .questions(List.of())
                .build();
        when(questionFacade.setDuplicateCanonical(42L, 99L)).thenReturn(dto);

        mvc.perform(post("/api/v1/admin/questions/42/duplicates/canonical")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canonicalQuestionId\":99,\"remark\":\"choose canonical question\",\"confirmationPhrase\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.questionId").value(42))
                .andExpect(jsonPath("$.data.canonicalId").value(99));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(adminAuditService).requireWritable("QUESTION_DUPLICATE_CANONICAL", "QUESTION", 42L);
        verify(questionFacade).setDuplicateCanonical(42L, 99L);
        verify(adminAuditService).recordRequired(eq(7L), eq("QUESTION_DUPLICATE_CANONICAL"), eq("QUESTION"),
                eq(42L), eq(null), any(Map.class), eq("choose canonical question"));
    }

    @Test
    void companyAliasWritesRequireServerSideRiskRemarkBeforeSideEffects() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/admin/company-aliases")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canonicalCompany\":\"ByteDance\",\"alias\":\"字节\",\"status\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
        mvc.perform(post("/api/v1/admin/company-aliases/8")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canonicalCompany\":\"ByteDance\",\"alias\":\"字节\",\"status\":1,\"remark\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
        mvc.perform(post("/api/v1/admin/company-aliases/8/status?status=0")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verify(adminPermissionService, times(3)).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verifyNoInteractions(questionFacade, adminAuditService);
    }

    @Test
    void companyAliasStatusFailsClosedWhenAuditIsNotWritable() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        doThrow(new SystemException("audit unavailable"))
                .when(adminAuditService)
                .requireWritable("COMPANY_ALIAS_STATUS", "COMPANY_ALIAS", 8L);

        mvc.perform(post("/api/v1/admin/company-aliases/8/status?status=0")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"disable obsolete alias\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(adminAuditService).requireWritable("COMPANY_ALIAS_STATUS", "COMPANY_ALIAS", 8L);
        verifyNoInteractions(questionFacade);
    }

    @Test
    void questionOperatorCanCreateCompanyAliasWithAuditedRiskRemark() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        CompanyAliasDTO dto = CompanyAliasDTO.builder()
                .id(8L)
                .canonicalCompany("ByteDance")
                .alias("字节")
                .status(1)
                .build();
        when(questionFacade.saveCompanyAlias(eq(null), any(CompanyAliasCmd.class))).thenReturn(dto);

        mvc.perform(post("/api/v1/admin/company-aliases")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canonicalCompany\":\"ByteDance\",\"alias\":\"字节\",\"status\":1,\"remark\":\"add verified alias\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(8))
                .andExpect(jsonPath("$.data.alias").value("字节"));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(adminAuditService).requireWritable("COMPANY_ALIAS_CREATE", "COMPANY_ALIAS", null);
        verify(questionFacade).saveCompanyAlias(eq(null), any(CompanyAliasCmd.class));
        verify(adminAuditService).recordRequired(7L, "COMPANY_ALIAS_CREATE", "COMPANY_ALIAS", 8L, null, dto,
                "add verified alias");
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
                        .content("{\"ids\":[1,2],\"status\":1,\"remark\":\"approve reviewed questions\",\"confirmationPhrase\":\"CONFIRM\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        verifyNoInteractions(questionFacade, adminAuditService);
    }

    @Test
    void questionBatchReviewFailsClosedWhenAuditIsNotWritable() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        doThrow(new SystemException("audit unavailable"))
                .when(adminAuditService)
                .requireWritable("QUESTION_REVIEW_BATCH", "QUESTION", null);

        mvc.perform(post("/api/v1/admin/questions/batch-review")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2],\"status\":1,\"remark\":\"approve reviewed questions\",\"confirmationPhrase\":\"CONFIRM\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(adminAuditService).requireWritable("QUESTION_REVIEW_BATCH", "QUESTION", null);
        verifyNoInteractions(questionFacade);
    }

    @Test
    void questionBatchReviewRequiresServerSideRiskConfirmation() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        for (String body : List.of(
                "{\"ids\":[1,2],\"status\":1}",
                "{\"ids\":[1,2],\"status\":1,\"remark\":\"approve reviewed questions\"}",
                "{\"ids\":[1,2],\"status\":1,\"remark\":\"approve reviewed questions\",\"confirmationPhrase\":\"WRONG\"}"
        )) {
            mvc.perform(post("/api/v1/admin/questions/batch-review")
                            .header("Authorization", "Bearer token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
        }

        verify(adminPermissionService, times(3)).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
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
                        .content("{\"ids\":[1,2,1],\"status\":1,\"remark\":\"approve reviewed questions\",\"confirmationPhrase\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.requested").value(2))
                .andExpect(jsonPath("$.data.reviewed").value(2))
                .andExpect(jsonPath("$.data.status").value(1));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        verify(adminAuditService).requireWritable("QUESTION_REVIEW_BATCH", "QUESTION", null);
        verify(questionFacade).reviewQuestion(1L, 1);
        verify(questionFacade).reviewQuestion(2L, 1);
        verify(adminAuditService).recordRequired(7L, "QUESTION_REVIEW_BATCH", "QUESTION", null,
                Map.of("ids", List.of(1L, 2L), "status", 1), Map.of("requested", 2, "reviewed", 2, "status", 1),
                "approve reviewed questions");
    }
}
