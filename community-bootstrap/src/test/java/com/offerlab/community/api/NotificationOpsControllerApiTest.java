package com.offerlab.community.api;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.exception.SystemException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.notification.application.NotificationRetryService;
import com.offerlab.community.notification.controller.NotificationOpsController;
import com.offerlab.community.notification.infrastructure.persistence.mapper.NotificationRetryTaskMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationRetryTaskPO;
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
class NotificationOpsControllerApiTest {
    @Mock
    private NotificationRetryService retryService;
    @Mock
    private AdminPermissionService adminPermissionService;
    @Mock
    private AdminAuditService adminAuditService;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new NotificationOpsController(retryService, adminPermissionService, adminAuditService), jwtService);
    }

    @Test
    void adminCanQueryNotificationRetryStatus() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(retryService.status()).thenReturn(Map.of(
                "byStatus", Map.of("pending", 2L, "done", 3L, "failed", 4L, "running", 5L),
                "duePending", 6L
        ));

        mvc.perform(get("/api/v1/ops/notification-retry-tasks/status")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.byStatus.failed").value(4))
                .andExpect(jsonPath("$.data.duePending").value(6));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verify(retryService).status();
        verifyNoInteractions(adminAuditService);
    }

    @Test
    void ordinaryUserCannotQueryNotificationRetryStatus() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(11L);
        doThrow(new BizException(ErrorCode.FORBIDDEN))
                .when(adminPermissionService)
                .requireScope(11L, AdminPermissionService.ROLE_OPS);

        mvc.perform(get("/api/v1/ops/notification-retry-tasks/status")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        verifyNoInteractions(retryService, adminAuditService);
    }

    @Test
    void adminCanReplayFailedNotificationRetryTask() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        NotificationRetryTaskPO task = new NotificationRetryTaskPO();
        task.setId(100L);
        task.setTaskStatus(NotificationRetryTaskMapper.STATUS_FAILED);
        when(retryService.findById(100L)).thenReturn(task);
        when(retryService.replayFailed(100L)).thenReturn(true);

        mvc.perform(post("/api/v1/ops/notification-retry-tasks/100/replay")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.replayed").value(true));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verify(retryService).findById(100L);
        verify(retryService).replayFailed(100L);
        verify(adminAuditService).recordRequired(7L, "NOTIF_RETRY_REPLAY", "NOTIF_RETRY_TASK", 100L, task, Map.of("replayed", true), null);
    }

    @Test
    void emptyNotificationReplayBatchIdsReturn400() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/ops/notification-retry-tasks/replay-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verifyNoInteractions(adminPermissionService, retryService, adminAuditService);
    }

    @Test
    void ordinaryUserCannotReplayNotificationBatch() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(11L);
        doThrow(new BizException(ErrorCode.FORBIDDEN))
                .when(adminPermissionService)
                .requireScope(11L, AdminPermissionService.ROLE_OPS);

        mvc.perform(post("/api/v1/ops/notification-retry-tasks/replay-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[3]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        verifyNoInteractions(retryService, adminAuditService);
    }

    @Test
    void adminCanReplayNotificationBatch() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(retryService.replayFailedBatch(List.of(3L, 4L))).thenReturn(2);

        mvc.perform(post("/api/v1/ops/notification-retry-tasks/replay-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[3,4,3]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.requested").value(2))
                .andExpect(jsonPath("$.data.replayed").value(2));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verify(retryService).replayFailedBatch(List.of(3L, 4L));
        verify(adminAuditService).recordRequired(7L, "NOTIF_RETRY_REPLAY_BATCH", "NOTIF_RETRY_TASK", null,
                List.of(3L, 4L), Map.of("replayed", 2), null);
    }

    @Test
    void adminCanPreviewNotificationReplayBatchWithoutAuditWrite() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        NotificationRetryTaskPO failed = new NotificationRetryTaskPO();
        failed.setId(3L);
        failed.setReceiverUid(88L);
        failed.setTargetId(99L);
        failed.setTaskStatus(NotificationRetryTaskMapper.STATUS_FAILED);
        failed.setRetryCount(2);
        NotificationRetryTaskPO done = new NotificationRetryTaskPO();
        done.setId(4L);
        done.setReceiverUid(89L);
        done.setTargetId(100L);
        done.setTaskStatus(NotificationRetryTaskMapper.STATUS_DONE);
        done.setRetryCount(0);
        when(retryService.findById(3L)).thenReturn(failed);
        when(retryService.findById(4L)).thenReturn(done);

        mvc.perform(post("/api/v1/ops/notification-retry-tasks/replay-batch/preview")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[3,4,3]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.operation").value("NOTIF_RETRY_REPLAY_BATCH"))
                .andExpect(jsonPath("$.data.requested").value(2))
                .andExpect(jsonPath("$.data.eligible").value(1))
                .andExpect(jsonPath("$.data.skipped").value(1))
                .andExpect(jsonPath("$.data.items[0].objectLabel").value("receiver:88"))
                .andExpect(jsonPath("$.data.items[0].targetId").value(99))
                .andExpect(jsonPath("$.data.items[1].reason").value("STATUS_NOT_FAILED"));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verify(retryService).findById(3L);
        verify(retryService).findById(4L);
        verifyNoInteractions(adminAuditService);
    }

    @Test
    void notificationReplayBatchFailsClosedWhenAuditIsNotWritable() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        doThrow(new SystemException("audit unavailable"))
                .when(adminAuditService)
                .requireWritable("NOTIF_RETRY_REPLAY_BATCH", "NOTIF_RETRY_TASK", null);

        mvc.perform(post("/api/v1/ops/notification-retry-tasks/replay-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[3,4]}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verify(adminAuditService).requireWritable("NOTIF_RETRY_REPLAY_BATCH", "NOTIF_RETRY_TASK", null);
        verifyNoInteractions(retryService);
    }
}
