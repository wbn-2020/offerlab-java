package com.offerlab.community.api;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.exception.SystemException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.search.application.SearchContentGapService;
import com.offerlab.community.search.application.SearchIndexTaskService;
import com.offerlab.community.search.controller.SearchAdminController;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SearchAdminControllerApiTest {
    @Mock
    private SearchIndexTaskService taskService;
    @Mock
    private SearchContentGapService contentGapService;
    @Mock
    private AdminPermissionService adminPermissionService;
    @Mock
    private AdminAuditService adminAuditService;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new SearchAdminController(taskService, contentGapService, adminPermissionService, adminAuditService), jwtService);
    }

    @Test
    void ordinaryUserCannotStartSearchIndexRebuildTask() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(11L);
        doThrow(new BizException(ErrorCode.FORBIDDEN))
                .when(adminPermissionService)
                .requireAdmin(11L);

        mvc.perform(post("/api/v1/search/admin/rebuild")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"rebuild post index\",\"confirmationPhrase\":\"CONFIRM\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        verifyNoInteractions(taskService, adminAuditService);
    }

    @Test
    void adminSearchIndexRebuildTaskIsAudited() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        SearchIndexTaskService.SearchIndexTask task = SearchIndexTaskService.SearchIndexTask.builder()
                .taskId("search-task-1")
                .type("POST_INDEX_REBUILD")
                .status("PENDING")
                .operatorUid(7L)
                .build();
        when(taskService.submitRebuildTask(7L)).thenReturn(task);

        mvc.perform(post("/api/v1/search/admin/rebuild")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"rebuild post index\",\"confirmationPhrase\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value("search-task-1"));

        verify(adminPermissionService).requireAdmin(7L);
        verify(taskService).submitRebuildTask(7L);
        verify(adminAuditService).recordRequired(7L, "POST_INDEX_REBUILD_TASK", "SEARCH_INDEX", "search-task-1",
                null,
                Map.of("taskId", "search-task-1", "type", "POST_INDEX_REBUILD", "status", "PENDING"),
                "rebuild post index");
    }

    @Test
    void searchIndexRebuildRequiresServerSideRiskConfirmation() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        for (String body : List.of(
                "{}",
                "{\"remark\":\"rebuild post index\"}",
                "{\"remark\":\"rebuild post index\",\"confirmationPhrase\":\"WRONG\"}"
        )) {
            mvc.perform(post("/api/v1/search/admin/rebuild")
                            .header("Authorization", "Bearer token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
        }

        verify(adminPermissionService, times(3)).requireAdmin(7L);
        verifyNoInteractions(taskService, adminAuditService);
    }

    @Test
    void searchIndexRebuildFailsClosedWhenAuditIsNotWritable() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        doThrow(new SystemException("audit unavailable"))
                .when(adminAuditService)
                .requireWritable("POST_INDEX_REBUILD_TASK", "SEARCH_INDEX", null);

        mvc.perform(post("/api/v1/search/admin/rebuild")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"rebuild post index\",\"confirmationPhrase\":\"CONFIRM\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()));

        verify(adminPermissionService).requireAdmin(7L);
        verify(adminAuditService).requireWritable("POST_INDEX_REBUILD_TASK", "SEARCH_INDEX", null);
        verifyNoInteractions(taskService);
    }
}
