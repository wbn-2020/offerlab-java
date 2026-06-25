package com.offerlab.community.api;

import com.offerlab.community.analytics.api.dto.ContentAssistDashboardDTO;
import com.offerlab.community.analytics.application.ContentAssistDashboardService;
import com.offerlab.community.analytics.controller.ContentAssistDashboardController;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContentAssistDashboardControllerApiTest {

    @Mock
    private ContentAssistDashboardService dashboardService;
    @Mock
    private AdminPermissionService adminPermissionService;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new ContentAssistDashboardController(dashboardService, adminPermissionService), jwtService);
    }

    @Test
    void contentAssistDashboardRequiresLogin() throws Exception {
        mvc.perform(get("/api/v1/dashboard/ai/content-assist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(dashboardService, adminPermissionService);
    }

    @Test
    void authenticatedButNonOpsUserCannotReadDashboard() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(5L);
        doThrow(new BizException(ErrorCode.FORBIDDEN))
                .when(adminPermissionService)
                .requireScope(5L, AdminPermissionService.ROLE_OPS);

        mvc.perform(get("/api/v1/dashboard/ai/content-assist")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        verify(adminPermissionService).requireScope(5L, AdminPermissionService.ROLE_OPS);
        verifyNoInteractions(dashboardService);
    }

    @Test
    void authenticatedDashboardReturnsAggregatedOutput() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(5L);
        when(dashboardService.summary(14)).thenReturn(ContentAssistDashboardDTO.builder()
                .days(14)
                .totalRequests(12L)
                .aiSuccessRequests(3L)
                .fallbackRequests(4L)
                .ruleOnlyRequests(5L)
                .uniqueUsers(6L)
                .totalPromptTokens(150L)
                .totalCompletionTokens(90L)
                .estimatedCostMicros(420L)
                .sceneStats(List.of(ContentAssistDashboardDTO.BucketDTO.builder()
                        .name("WRITING")
                        .count(6L)
                        .estimatedCostMicros(300L)
                        .build()))
                .providerStats(List.of(ContentAssistDashboardDTO.BucketDTO.builder()
                        .name("rules")
                        .count(9L)
                        .estimatedCostMicros(0L)
                        .build()))
                .errorStats(List.of(ContentAssistDashboardDTO.BucketDTO.builder()
                        .name("AI_INVALID_RESPONSE")
                        .count(2L)
                        .estimatedCostMicros(0L)
                        .build()))
                .recentErrors(List.of(ContentAssistDashboardDTO.ErrorSampleDTO.builder()
                        .scene("WRITING")
                        .provider("deepseek")
                        .errorCode("AI_INVALID_RESPONSE")
                        .createTime(LocalDateTime.of(2026, 6, 24, 12, 0))
                        .build()))
                .build());

        mvc.perform(get("/api/v1/dashboard/ai/content-assist")
                        .header("Authorization", "Bearer token")
                        .param("days", "14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.days").value(14))
                .andExpect(jsonPath("$.data.totalRequests").value(12))
                .andExpect(jsonPath("$.data.sceneStats[0].name").value("WRITING"))
                .andExpect(jsonPath("$.data.errorStats[0].name").value("AI_INVALID_RESPONSE"))
                .andExpect(jsonPath("$.data.recentErrors[0].provider").value("deepseek"));

        verify(adminPermissionService).requireScope(5L, AdminPermissionService.ROLE_OPS);
        verify(dashboardService).summary(14);
    }
}
