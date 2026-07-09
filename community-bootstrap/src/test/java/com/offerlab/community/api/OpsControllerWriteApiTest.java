package com.offerlab.community.api;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.exception.SystemException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.moderation.ModerationAdminService;
import com.offerlab.community.infra.mq.outbox.OutboxMessage;
import com.offerlab.community.infra.mq.outbox.OutboxMessageMapper;
import com.offerlab.community.infra.ops.AdminOperationIdempotencyService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.AdminRoleMapper;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.notification.application.NotificationRetryService;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.search.api.SearchFacade;
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
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
    private SearchFacade searchFacade;
    @Mock
    private PostFacade postFacade;
    @Mock
    private ElasticsearchHttpClient elasticsearch;
    @Mock
    private OutboxMessageMapper outboxMessageMapper;
    @Mock
    private AdminRoleMapper adminRoleMapper;
    @Mock
    private AdminPermissionService adminPermissionService;
    @Mock
    private AdminAuditService adminAuditService;
    @Mock
    private DomainModeratorService domainModeratorService;
    @Mock
    private ModerationAdminService moderationAdminService;
    @Mock
    private MigrationCheckService migrationCheckService;
    @Mock
    private UserFacade userFacade;
    @Mock
    private JwtService jwtService;
    @Mock
    private Environment environment;
    private AdminOperationIdempotencyService idempotencyService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        idempotencyService = new AdminOperationIdempotencyService();
        mvc = ApiTestSupport.mvc(new OpsController(
                indexer,
                searchIndexRetryService,
                searchAnalyticsService,
                notificationRetryService,
                searchFacade,
                postFacade,
                elasticsearch,
                outboxMessageMapper,
                adminRoleMapper,
                adminPermissionService,
                adminAuditService,
                domainModeratorService,
                moderationAdminService,
                migrationCheckService,
                userFacade,
                idempotencyService,
                environment
        ), jwtService);
        lenient().when(environment.getProperty("spring.profiles.active", "")).thenReturn("test");
        lenient().when(environment.getProperty("offerlab.kafka.outbox-replay.require-local-check", "true")).thenReturn("false");
    }

    private String previewNonce(String operation, List<Long> ids) {
        return idempotencyService.issuePreview(7L, operation, ids);
    }

    private static String idsPayload(int count) {
        StringBuilder builder = new StringBuilder("{\"ids\":[");
        for (int i = 1; i <= count; i++) {
            if (i > 1) {
                builder.append(',');
            }
            builder.append(i);
        }
        return builder.append("]}").toString();
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
    void outboxRetryBatchRejectsMoreThanOperatorLimitBeforeWrite() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/ops/outbox/retry-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(idsPayload(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verifyNoInteractions(adminPermissionService, outboxMessageMapper, adminAuditService);
    }

    @Test
    void adminRoleWritesRequireServerSideRiskConfirmation() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        for (String body : List.of(
                "{\"uid\":99,\"roleCode\":\"OPS\"}",
                "{\"uid\":99,\"roleCode\":\"OPS\",\"auditRemark\":\"grant ops role\"}",
                "{\"uid\":99,\"roleCode\":\"OPS\",\"auditRemark\":\"grant ops role\",\"confirmationPhrase\":\"WRONG\"}"
        )) {
            mvc.perform(post("/api/v1/ops/admins")
                            .header("Authorization", "Bearer token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
        }

        verify(adminPermissionService, times(3)).requireAdmin(7L);
        verifyNoInteractions(adminRoleMapper, adminAuditService);
    }

    @Test
    void adminRoleCreateFailsClosedWhenAuditIsNotWritable() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        doThrow(new SystemException("audit unavailable"))
                .when(adminAuditService)
                .requireWritable("ADMIN_ROLE_UPSERT", "ADMIN_ROLE", "99:OPS");

        mvc.perform(post("/api/v1/ops/admins")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":99,\"roleCode\":\"OPS\",\"auditRemark\":\"grant ops role\",\"confirmationPhrase\":\"CONFIRM\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()));

        verify(adminPermissionService).requireAdmin(7L);
        verify(adminAuditService).requireWritable("ADMIN_ROLE_UPSERT", "ADMIN_ROLE", "99:OPS");
        verifyNoInteractions(adminRoleMapper);
    }

    @Test
    void adminRoleStatusFailsClosedWhenAuditIsNotWritable() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        doThrow(new SystemException("audit unavailable"))
                .when(adminAuditService)
                .requireWritable("ADMIN_ROLE_STATUS", "ADMIN_ROLE", "99:OPS");

        mvc.perform(post("/api/v1/ops/admins/99/status")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"roleCode\":\"OPS\",\"auditRemark\":\"disable ops role\",\"confirmationPhrase\":\"CONFIRM\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()));

        verify(adminPermissionService).requireAdmin(7L);
        verify(adminAuditService).requireWritable("ADMIN_ROLE_STATUS", "ADMIN_ROLE", "99:OPS");
        verifyNoInteractions(adminRoleMapper);
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
                        .content("{\"ids\":[1,2],\"remark\":\"retry failed outbox\",\"confirmationPhrase\":\"CONFIRM\",\"idempotencyKey\":\"outbox-wrong-role-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        verifyNoInteractions(outboxMessageMapper, adminAuditService);
    }

    @Test
    void opsCanRetryOutboxBatchWithDistinctPositiveIds() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(outboxMessageMapper.markFailedForRetryBatch(List.of(1L, 2L))).thenReturn(2);
        String previewNonce = previewNonce("OUTBOX_RETRY_BATCH", List.of(1L, 2L));

        mvc.perform(post("/api/v1/ops/outbox/retry-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2,1],\"remark\":\"retry failed outbox\",\"confirmationPhrase\":\"CONFIRM\",\"idempotencyKey\":\"outbox-batch-key-1\",\"previewNonce\":\"" + previewNonce + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.requested").value(2))
                .andExpect(jsonPath("$.data.retried").value(2));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verify(outboxMessageMapper).markFailedForRetryBatch(List.of(1L, 2L));
        verify(adminAuditService).recordRequired(7L, "OUTBOX_RETRY_BATCH", "OUTBOX", null,
                List.of(1L, 2L), Map.of("retried", 2, "idempotencyKey", "outbox-batch-key-1"), "retry failed outbox");
    }

    @Test
    void outboxRetryBatchRequiresServerSideRiskConfirmation() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        for (String body : List.of(
                "{\"ids\":[1,2]}",
                "{\"ids\":[1,2],\"remark\":\"retry failed outbox\"}",
                "{\"ids\":[1,2],\"remark\":\"retry failed outbox\",\"confirmationPhrase\":\"WRONG\",\"idempotencyKey\":\"outbox-bad-confirm\"}",
                "{\"ids\":[1,2],\"remark\":\"retry failed outbox\",\"confirmationPhrase\":\"CONFIRM\"}",
                "{\"ids\":[1,2],\"remark\":\"retry failed outbox\",\"confirmationPhrase\":\"CONFIRM\",\"idempotencyKey\":\"outbox-missing-preview-1\"}"
        )) {
            mvc.perform(post("/api/v1/ops/outbox/retry-batch")
                            .header("Authorization", "Bearer token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
        }

        verify(adminPermissionService, times(5)).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verifyNoInteractions(outboxMessageMapper, adminAuditService);
    }

    @Test
    void repeatedOutboxRetryBatchWithSameIdempotencyKeyIsBlockedBeforeWrite() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(outboxMessageMapper.markFailedForRetryBatch(List.of(1L, 2L))).thenReturn(2);
        String previewNonce = previewNonce("OUTBOX_RETRY_BATCH", List.of(1L, 2L));
        String body = "{\"ids\":[1,2],\"remark\":\"retry failed outbox\",\"confirmationPhrase\":\"CONFIRM\",\"idempotencyKey\":\"outbox-repeat-key-1\",\"previewNonce\":\"" + previewNonce + "\"}";

        mvc.perform(post("/api/v1/ops/outbox/retry-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotencyKey").value("outbox-repeat-key-1"));

        mvc.perform(post("/api/v1/ops/outbox/retry-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_OPERATION.getCode()))
                .andExpect(jsonPath("$.data.errorCategory").value("DUPLICATE_ADMIN_OPERATION"));

        verify(outboxMessageMapper).markFailedForRetryBatch(List.of(1L, 2L));
        verify(adminAuditService).recordRequired(7L, "OUTBOX_RETRY_BATCH", "OUTBOX", null,
                List.of(1L, 2L), Map.of("retried", 2, "idempotencyKey", "outbox-repeat-key-1"), "retry failed outbox");
    }

    @Test
    void outboxRetryBatchRequiresMatchingPreviewNonce() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        String wrongBatchNonce = previewNonce("OUTBOX_RETRY_BATCH", List.of(2L));

        mvc.perform(post("/api/v1/ops/outbox/retry-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1],\"remark\":\"retry failed outbox\",\"confirmationPhrase\":\"CONFIRM\",\"idempotencyKey\":\"outbox-preview-key-1\",\"previewNonce\":\"" + wrongBatchNonce + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()))
                .andExpect(jsonPath("$.data.errorCategory").value("PREVIEW_NONCE_MISMATCH"));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verifyNoInteractions(outboxMessageMapper, adminAuditService);
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
                .andExpect(jsonPath("$.data.estimatedImpact").value(1))
                .andExpect(jsonPath("$.data.maxBatchSize").value(50))
                .andExpect(jsonPath("$.data.previewExpiresInSeconds").value(300))
                .andExpect(jsonPath("$.data.requiresAuditReason").value(true))
                .andExpect(jsonPath("$.data.confirmationPhrase").value("CONFIRM"))
                .andExpect(jsonPath("$.data.previewNonce").exists())
                .andExpect(jsonPath("$.data.riskReason").value("PARTIAL_SKIPPED:STATUS_NOT_FAILED;READY=1;SKIPPED=1"))
                .andExpect(jsonPath("$.data.items[0].eligible").value(true))
                .andExpect(jsonPath("$.data.items[0].objectLabel").value("post-events:99"))
                .andExpect(jsonPath("$.data.items[1].reason").value("STATUS_NOT_FAILED"));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verify(outboxMessageMapper).findById(1L);
        verify(outboxMessageMapper).findById(2L);
        verifyNoInteractions(adminAuditService);
    }

    @Test
    void outboxRetryPreviewIsBlockedWhenKafkaIsNotReady() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(environment.getProperty("offerlab.kafka.outbox-replay.require-local-check", "true")).thenReturn("true");
        when(environment.getProperty("spring.kafka.bootstrap-servers", "localhost:9092")).thenReturn("127.0.0.1:1");
        when(environment.getProperty("offerlab.kafka.topic.post-published", "post.published")).thenReturn("post.published");
        when(environment.getProperty("offerlab.kafka.consumer.feed-group", "offerlab-feed-fanout")).thenReturn("offerlab-feed-fanout");

        mvc.perform(post("/api/v1/ops/outbox/retry-batch/preview")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocked").value(true))
                .andExpect(jsonPath("$.data.blockedReason").value("KAFKA_NOT_READY"))
                .andExpect(jsonPath("$.data.eligible").value(0))
                .andExpect(jsonPath("$.data.items[0].reason").value("KAFKA_NOT_READY"))
                .andExpect(jsonPath("$.data.kafkaLocalCheck.readyForOutboxReplay").value(false));

        verifyNoInteractions(outboxMessageMapper, adminAuditService);
    }

    @Test
    void outboxRetryBatchFailsClosedWhenKafkaIsNotReady() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(environment.getProperty("offerlab.kafka.outbox-replay.require-local-check", "true")).thenReturn("true");
        when(environment.getProperty("spring.kafka.bootstrap-servers", "localhost:9092")).thenReturn("127.0.0.1:1");
        when(environment.getProperty("offerlab.kafka.topic.post-published", "post.published")).thenReturn("post.published");
        when(environment.getProperty("offerlab.kafka.consumer.feed-group", "offerlab-feed-fanout")).thenReturn("offerlab-feed-fanout");
        String previewNonce = previewNonce("OUTBOX_RETRY_BATCH", List.of(1L));

        mvc.perform(post("/api/v1/ops/outbox/retry-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1],\"remark\":\"retry failed outbox\",\"confirmationPhrase\":\"CONFIRM\",\"idempotencyKey\":\"outbox-kafka-down-1\",\"previewNonce\":\"" + previewNonce + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.DEPENDENCY_ERROR.getCode()));

        verifyNoInteractions(outboxMessageMapper, adminAuditService);
    }

    @Test
    void outboxRetryBatchFailsClosedWhenAuditIsNotWritable() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        doThrow(new SystemException("audit unavailable"))
                .when(adminAuditService)
                .requireWritable("OUTBOX_RETRY_BATCH", "OUTBOX", null);
        String previewNonce = previewNonce("OUTBOX_RETRY_BATCH", List.of(1L, 2L));

        mvc.perform(post("/api/v1/ops/outbox/retry-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2],\"remark\":\"retry failed outbox\",\"confirmationPhrase\":\"CONFIRM\",\"idempotencyKey\":\"outbox-audit-key-1\",\"previewNonce\":\"" + previewNonce + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verify(adminAuditService).requireWritable("OUTBOX_RETRY_BATCH", "OUTBOX", null);
        verifyNoInteractions(outboxMessageMapper);
    }

    @Test
    void outboxSingleRetryRejectsLegacyRemarkOnlyBeforeWrite() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        OutboxMessage failed = new OutboxMessage();
        failed.setId(1L);
        failed.setMsgStatus(2);
        when(outboxMessageMapper.findById(1L)).thenReturn(failed);

        for (String body : List.of(
                "{\"remark\":\"retry failed outbox\"}",
                "{\"remark\":\"retry failed outbox\",\"confirmationPhrase\":\"CONFIRM\"}",
                "{\"remark\":\"retry failed outbox\",\"confirmationPhrase\":\"CONFIRM\",\"idempotencyKey\":\"outbox-single-missing-preview\"}"
        )) {
            mvc.perform(post("/api/v1/ops/outbox/1/retry")
                            .header("Authorization", "Bearer token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
        }

        verify(adminPermissionService, times(3)).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verify(outboxMessageMapper, times(3)).findById(1L);
        verifyNoMoreInteractions(outboxMessageMapper);
        verifyNoInteractions(adminAuditService);
    }

    @Test
    void repeatedOutboxSingleRetryWithSameIdempotencyKeyIsBlockedBeforeWrite() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        OutboxMessage failed = new OutboxMessage();
        failed.setId(1L);
        failed.setMsgStatus(2);
        when(outboxMessageMapper.findById(1L)).thenReturn(failed);
        when(outboxMessageMapper.markFailedForRetry(1L)).thenReturn(1);
        String previewNonce = previewNonce("OUTBOX_RETRY_BATCH", List.of(1L));
        String body = "{\"remark\":\"retry failed outbox\",\"confirmationPhrase\":\"CONFIRM\",\"idempotencyKey\":\"outbox-single-key-1\",\"previewNonce\":\"" + previewNonce + "\"}";

        mvc.perform(post("/api/v1/ops/outbox/1/retry")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotencyKey").value("outbox-single-key-1"))
                .andExpect(jsonPath("$.data.retried").value(true));

        mvc.perform(post("/api/v1/ops/outbox/1/retry")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_OPERATION.getCode()))
                .andExpect(jsonPath("$.data.errorCategory").value("DUPLICATE_ADMIN_OPERATION"));

        verify(outboxMessageMapper, times(2)).findById(1L);
        verify(outboxMessageMapper).markFailedForRetry(1L);
        verify(adminAuditService).recordRequired(7L, "OUTBOX_RETRY", "OUTBOX", 1L, failed,
                Map.of("retried", true, "idempotencyKey", "outbox-single-key-1"), "retry failed outbox");
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
    void searchIndexReplayBatchRejectsMoreThanOperatorLimitBeforeWrite() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/ops/search-index-retry-tasks/replay-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(idsPayload(51)))
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
                        .content("{\"ids\":[9],\"remark\":\"replay failed search tasks\",\"confirmationPhrase\":\"CONFIRM\",\"idempotencyKey\":\"search-wrong-role-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        verifyNoInteractions(searchIndexRetryService, adminAuditService);
    }

    @Test
    void opsCanReplaySearchIndexBatchWithDistinctPositiveIds() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(searchIndexRetryService.replayFailedBatch(List.of(9L, 10L))).thenReturn(2);
        String previewNonce = previewNonce("SEARCH_INDEX_RETRY_REPLAY_BATCH", List.of(9L, 10L));

        mvc.perform(post("/api/v1/ops/search-index-retry-tasks/replay-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[9,10,9],\"remark\":\"replay failed search tasks\",\"confirmationPhrase\":\"CONFIRM\",\"idempotencyKey\":\"search-batch-key-1\",\"previewNonce\":\"" + previewNonce + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.requested").value(2))
                .andExpect(jsonPath("$.data.replayed").value(2))
                .andExpect(jsonPath("$.data.idempotencyKey").value("search-batch-key-1"));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verify(searchIndexRetryService).replayFailedBatch(List.of(9L, 10L));
        verify(adminAuditService).recordRequired(7L, "SEARCH_INDEX_RETRY_REPLAY_BATCH", "SEARCH_INDEX_RETRY_TASK", null,
                List.of(9L, 10L), Map.of("replayed", 2, "idempotencyKey", "search-batch-key-1"), "replay failed search tasks");
    }

    @Test
    void repeatedSearchIndexReplayBatchWithSameIdempotencyKeyIsBlockedBeforeWrite() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(searchIndexRetryService.replayFailedBatch(List.of(9L))).thenReturn(1);
        String previewNonce = previewNonce("SEARCH_INDEX_RETRY_REPLAY_BATCH", List.of(9L));
        String body = "{\"ids\":[9],\"remark\":\"replay failed search tasks\",\"confirmationPhrase\":\"CONFIRM\",\"idempotencyKey\":\"search-repeat-key-1\",\"previewNonce\":\"" + previewNonce + "\"}";

        mvc.perform(post("/api/v1/ops/search-index-retry-tasks/replay-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotencyKey").value("search-repeat-key-1"));

        mvc.perform(post("/api/v1/ops/search-index-retry-tasks/replay-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_OPERATION.getCode()))
                .andExpect(jsonPath("$.data.errorCategory").value("DUPLICATE_ADMIN_OPERATION"));

        verify(searchIndexRetryService).replayFailedBatch(List.of(9L));
        verify(adminAuditService).recordRequired(7L, "SEARCH_INDEX_RETRY_REPLAY_BATCH", "SEARCH_INDEX_RETRY_TASK", null,
                List.of(9L), Map.of("replayed", 1, "idempotencyKey", "search-repeat-key-1"), "replay failed search tasks");
    }

    @Test
    void searchIndexReplayBatchRequiresIdempotencyKey() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/ops/search-index-retry-tasks/replay-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[9],\"remark\":\"replay failed search tasks\",\"confirmationPhrase\":\"CONFIRM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verifyNoInteractions(searchIndexRetryService, adminAuditService);
    }

    @Test
    void searchIndexReplayBatchRequiresMatchingPreviewNonce() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        String wrongBatchNonce = previewNonce("SEARCH_INDEX_RETRY_REPLAY_BATCH", List.of(10L));

        mvc.perform(post("/api/v1/ops/search-index-retry-tasks/replay-batch")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[9],\"remark\":\"replay failed search tasks\",\"confirmationPhrase\":\"CONFIRM\",\"idempotencyKey\":\"search-preview-key-1\",\"previewNonce\":\"" + wrongBatchNonce + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()))
                .andExpect(jsonPath("$.data.errorCategory").value("PREVIEW_NONCE_MISMATCH"));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
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
                .andExpect(jsonPath("$.data.estimatedImpact").value(1))
                .andExpect(jsonPath("$.data.maxBatchSize").value(50))
                .andExpect(jsonPath("$.data.previewExpiresInSeconds").value(300))
                .andExpect(jsonPath("$.data.requiresAuditReason").value(true))
                .andExpect(jsonPath("$.data.confirmationPhrase").value("CONFIRM"))
                .andExpect(jsonPath("$.data.previewNonce").exists())
                .andExpect(jsonPath("$.data.riskReason").value("PARTIAL_SKIPPED:STATUS_NOT_FAILED;READY=1;SKIPPED=1"))
                .andExpect(jsonPath("$.data.items[0].objectLabel").value("post:200"))
                .andExpect(jsonPath("$.data.items[0].operation").value("INDEX"))
                .andExpect(jsonPath("$.data.items[1].reason").value("STATUS_NOT_FAILED"));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verify(searchIndexRetryService).findById(9L);
        verify(searchIndexRetryService).findById(10L);
        verifyNoInteractions(adminAuditService);
    }

    @Test
    void searchIndexSingleReplayRejectsLegacyRemarkOnlyBeforeWrite() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        SearchIndexRetryTaskPO failed = new SearchIndexRetryTaskPO();
        failed.setId(9L);
        failed.setTaskStatus(SearchIndexRetryTaskMapper.STATUS_FAILED);
        when(searchIndexRetryService.findById(9L)).thenReturn(failed);

        for (String body : List.of(
                "{\"remark\":\"replay failed search task\"}",
                "{\"remark\":\"replay failed search task\",\"confirmationPhrase\":\"CONFIRM\"}",
                "{\"remark\":\"replay failed search task\",\"confirmationPhrase\":\"CONFIRM\",\"idempotencyKey\":\"search-single-missing-preview\"}"
        )) {
            mvc.perform(post("/api/v1/ops/search-index-retry-tasks/9/replay")
                            .header("Authorization", "Bearer token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
        }

        verify(adminPermissionService, times(3)).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verify(searchIndexRetryService, times(3)).findById(9L);
        verifyNoMoreInteractions(searchIndexRetryService);
        verifyNoInteractions(adminAuditService);
    }

    @Test
    void repeatedSearchIndexSingleReplayWithSameIdempotencyKeyIsBlockedBeforeWrite() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        SearchIndexRetryTaskPO failed = new SearchIndexRetryTaskPO();
        failed.setId(9L);
        failed.setTaskStatus(SearchIndexRetryTaskMapper.STATUS_FAILED);
        when(searchIndexRetryService.findById(9L)).thenReturn(failed);
        when(searchIndexRetryService.replayFailed(9L)).thenReturn(true);
        String previewNonce = previewNonce("SEARCH_INDEX_RETRY_REPLAY_BATCH", List.of(9L));
        String body = "{\"remark\":\"replay failed search task\",\"confirmationPhrase\":\"CONFIRM\",\"idempotencyKey\":\"search-single-key-1\",\"previewNonce\":\"" + previewNonce + "\"}";

        mvc.perform(post("/api/v1/ops/search-index-retry-tasks/9/replay")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotencyKey").value("search-single-key-1"))
                .andExpect(jsonPath("$.data.replayed").value(true));

        mvc.perform(post("/api/v1/ops/search-index-retry-tasks/9/replay")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_OPERATION.getCode()))
                .andExpect(jsonPath("$.data.errorCategory").value("DUPLICATE_ADMIN_OPERATION"));

        verify(searchIndexRetryService, times(2)).findById(9L);
        verify(searchIndexRetryService).replayFailed(9L);
        verify(adminAuditService).recordRequired(7L, "SEARCH_INDEX_RETRY_REPLAY", "SEARCH_INDEX_RETRY_TASK", 9L,
                failed, Map.of("replayed", true, "idempotencyKey", "search-single-key-1"), "replay failed search task");
    }
}
