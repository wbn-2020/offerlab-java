package com.offerlab.community.api;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.moderation.ModerationAdminService;
import com.offerlab.community.infra.mq.outbox.OutboxMessageMapper;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.AdminRoleMapper;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.notification.application.NotificationRetryService;
import com.offerlab.community.search.application.PostSearchIndexer;
import com.offerlab.community.search.application.SearchAnalyticsService;
import com.offerlab.community.search.application.SearchIndexRetryService;
import com.offerlab.community.search.controller.OpsController;
import com.offerlab.community.user.api.UserFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OpsControllerStatusApiTest {
    @Mock
    private PostSearchIndexer indexer;
    @Mock
    private SearchIndexRetryService searchIndexRetryService;
    @Mock
    private SearchAnalyticsService searchAnalyticsService;
    @Mock
    private NotificationRetryService notificationRetryService;
    @Mock
    private OutboxMessageMapper outboxMessageMapper;
    @Mock
    private AdminRoleMapper adminRoleMapper;
    @Mock
    private AdminPermissionService adminPermissionService;
    @Mock
    private AdminAuditService adminAuditService;
    @Mock
    private ModerationAdminService moderationAdminService;
    @Mock
    private MigrationCheckService migrationCheckService;
    @Mock
    private UserFacade userFacade;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new OpsController(
                indexer,
                searchIndexRetryService,
                searchAnalyticsService,
                notificationRetryService,
                outboxMessageMapper,
                adminRoleMapper,
                adminPermissionService,
                adminAuditService,
                moderationAdminService,
                migrationCheckService,
                userFacade
        ), jwtService);
    }

    @Test
    void opsStatusIncludesNotificationRetrySummary() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(adminPermissionService.whitelistEnabled()).thenReturn(false);
        when(adminPermissionService.roleTableEnabled()).thenReturn(true);
        when(adminPermissionService.mode()).thenReturn("RBAC");
        when(indexer.status()).thenReturn(Map.of("available", true, "indexReady", true));
        when(searchIndexRetryService.status()).thenReturn(Map.of(
                "byStatus", Map.of("pending", 1L, "done", 2L, "failed", 3L, "running", 4L),
                "duePending", 5L
        ));
        when(notificationRetryService.status()).thenReturn(Map.of(
                "byStatus", Map.of("pending", 6L, "done", 7L, "failed", 8L, "running", 9L),
                "duePending", 10L
        ));
        when(outboxMessageMapper.countByStatus()).thenReturn(List.of(Map.of("status", 2, "count", 11L)));
        when(outboxMessageMapper.countDuePending()).thenReturn(12L);

        mvc.perform(get("/api/v1/ops/status")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.notificationRetry.byStatus.failed").value(8))
                .andExpect(jsonPath("$.data.notificationRetry.duePending").value(10))
                .andExpect(jsonPath("$.data.searchIndexRetry.byStatus.running").value(4))
                .andExpect(jsonPath("$.data.outbox.byStatus.failed").value(11));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verify(notificationRetryService).status();
    }

    @Test
    void ordinaryUserCannotReadOpsStatus() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(11L);
        doThrow(new BizException(ErrorCode.FORBIDDEN))
                .when(adminPermissionService)
                .requireScope(11L, AdminPermissionService.ROLE_OPS);

        mvc.perform(get("/api/v1/ops/status")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(indexer, searchIndexRetryService, notificationRetryService, outboxMessageMapper);
    }
}
