package com.offerlab.community.api;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.exception.SystemException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.moderation.ModerationAdminService;
import com.offerlab.community.infra.mq.outbox.OutboxMessage;
import com.offerlab.community.infra.mq.outbox.OutboxMessageMapper;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.AdminRoleMapper;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.notification.application.NotificationRetryService;
import com.offerlab.community.search.application.PostSearchIndexer;
import com.offerlab.community.search.application.SearchAnalyticsService;
import com.offerlab.community.search.application.SearchIndexRetryService;
import com.offerlab.community.search.controller.OpsController;
import com.offerlab.community.search.infrastructure.persistence.mapper.SearchIndexRetryTaskMapper;
import com.offerlab.community.search.infrastructure.persistence.po.SearchIndexRetryTaskPO;
import com.offerlab.community.user.api.UserFacade;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OpsControllerWriteApiTest {
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
    void emptyOutboxRetryBatchIdsReturn400() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/ops/outbox/retry-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verifyNoInteractions(adminPermissionService, outboxMessageMapper, adminAuditService);
    }

    @Test
    void ordinaryUserCannotRetryOutboxBatch() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(11L);
        doThrow(new BizException(ErrorCode.FORBIDDEN))
                .when(adminPermissionService)
                .requireScope(11L, AdminPermissionService.ROLE_OPS);

        mvc.perform(post("/api/v1/ops/outbox/retry-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        verifyNoInteractions(outboxMessageMapper, adminAuditService);
    }

    @Test
    void opsCanRetryOutboxBatchWithDistinctPositiveIds() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(outboxMessageMapper.markFailedForRetryBatch(List.of(1L, 2L))).thenReturn(2);

        mvc.perform(post("/api/v1/ops/outbox/retry-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2,1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.requested").value(2))
                .andExpect(jsonPath("$.data.retried").value(2));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verify(outboxMessageMapper).markFailedForRetryBatch(List.of(1L, 2L));
        verify(adminAuditService).recordRequired(7L, "OUTBOX_RETRY_BATCH", "OUTBOX", null,
                List.of(1L, 2L), Map.of("retried", 2), null);
    }

    @Test
    void opsCanPreviewOutboxRetryBatchWithoutAuditWrite() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        OutboxMessage failed = new OutboxMessage();
        failed.setId(1L);
        failed.setTopic("post-events");
        failed.setAggregateId(99L);
        failed.setMsgStatus(2);
        failed.setRetryCount(3);
        OutboxMessage sent = new OutboxMessage();
        sent.setId(2L);
        sent.setTopic("post-events");
        sent.setAggregateId(100L);
        sent.setMsgStatus(1);
        sent.setRetryCount(0);
        when(outboxMessageMapper.findById(1L)).thenReturn(failed);
        when(outboxMessageMapper.findById(2L)).thenReturn(sent);

        mvc.perform(post("/api/v1/ops/outbox/retry-batch/preview")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2,1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.operation").value("OUTBOX_RETRY_BATCH"))
                .andExpect(jsonPath("$.data.requested").value(2))
                .andExpect(jsonPath("$.data.eligible").value(1))
                .andExpect(jsonPath("$.data.skipped").value(1))
                .andExpect(jsonPath("$.data.items[0].eligible").value(true))
                .andExpect(jsonPath("$.data.items[0].objectLabel").value("post-events:99"))
                .andExpect(jsonPath("$.data.items[1].reason").value("STATUS_NOT_FAILED"));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verify(outboxMessageMapper).findById(1L);
        verify(outboxMessageMapper).findById(2L);
        verifyNoInteractions(adminAuditService);
    }

    @Test
    void outboxRetryBatchFailsClosedWhenAuditIsNotWritable() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        doThrow(new SystemException("audit unavailable"))
                .when(adminAuditService)
                .requireWritable("OUTBOX_RETRY_BATCH", "OUTBOX", null);

        mvc.perform(post("/api/v1/ops/outbox/retry-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2]}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verify(adminAuditService).requireWritable("OUTBOX_RETRY_BATCH", "OUTBOX", null);
        verifyNoInteractions(outboxMessageMapper);
    }

    @Test
    void invalidSearchIndexReplayBatchIdsReturn400() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/ops/search-index-retry-tasks/replay-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[0]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verifyNoInteractions(adminPermissionService, searchIndexRetryService, adminAuditService);
    }

    @Test
    void wrongRoleCannotReplaySearchIndexBatch() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(11L);
        doThrow(new BizException(ErrorCode.FORBIDDEN))
                .when(adminPermissionService)
                .requireScope(11L, AdminPermissionService.ROLE_OPS);

        mvc.perform(post("/api/v1/ops/search-index-retry-tasks/replay-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[9]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        verifyNoInteractions(searchIndexRetryService, adminAuditService);
    }

    @Test
    void opsCanPreviewSearchIndexReplayBatchWithoutAuditWrite() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        SearchIndexRetryTaskPO failed = new SearchIndexRetryTaskPO();
        failed.setId(9L);
        failed.setPostId(200L);
        failed.setOperation("INDEX");
        failed.setTaskStatus(SearchIndexRetryTaskMapper.STATUS_FAILED);
        failed.setRetryCount(2);
        SearchIndexRetryTaskPO pending = new SearchIndexRetryTaskPO();
        pending.setId(10L);
        pending.setPostId(201L);
        pending.setOperation("DELETE");
        pending.setTaskStatus(SearchIndexRetryTaskMapper.STATUS_PENDING);
        pending.setRetryCount(1);
        when(searchIndexRetryService.findById(9L)).thenReturn(failed);
        when(searchIndexRetryService.findById(10L)).thenReturn(pending);

        mvc.perform(post("/api/v1/ops/search-index-retry-tasks/replay-batch/preview")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[9,10,9]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.operation").value("SEARCH_INDEX_RETRY_REPLAY_BATCH"))
                .andExpect(jsonPath("$.data.requested").value(2))
                .andExpect(jsonPath("$.data.eligible").value(1))
                .andExpect(jsonPath("$.data.skipped").value(1))
                .andExpect(jsonPath("$.data.items[0].objectLabel").value("post:200"))
                .andExpect(jsonPath("$.data.items[0].operation").value("INDEX"))
                .andExpect(jsonPath("$.data.items[1].reason").value("STATUS_NOT_FAILED"));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verify(searchIndexRetryService).findById(9L);
        verify(searchIndexRetryService).findById(10L);
        verifyNoInteractions(adminAuditService);
    }
}
