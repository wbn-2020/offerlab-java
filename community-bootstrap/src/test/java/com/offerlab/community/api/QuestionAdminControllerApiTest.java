package com.offerlab.community.api;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.question.application.QuestionFacade;
import com.offerlab.community.question.application.QuestionIndexTaskService;
import com.offerlab.community.question.controller.QuestionAdminController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

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
}
