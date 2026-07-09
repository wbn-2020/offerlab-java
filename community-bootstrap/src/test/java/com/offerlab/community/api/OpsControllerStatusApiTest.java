package com.offerlab.community.api;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.infra.moderation.ModerationAdminService;
import com.offerlab.community.infra.mq.outbox.OutboxMessage;
import com.offerlab.community.infra.mq.outbox.OutboxMessageMapper;
import com.offerlab.community.infra.ops.AdminOperationIdempotencyService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.AdminRoleMapper;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.notification.application.NotificationRetryService;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.search.api.SearchFacade;
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
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
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
                new AdminOperationIdempotencyService(),
                environment
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
        when(outboxMessageMapper.listRecent(OutboxMessageMapper.STATUS_FAILED, 1)).thenReturn(List.of(outboxMessage()));
        when(outboxMessageMapper.listRecent(OutboxMessageMapper.STATUS_PENDING, 1)).thenReturn(List.of());

        mvc.perform(get("/api/v1/ops/status")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.notificationRetry.byStatus.failed").value(8))
                .andExpect(jsonPath("$.data.notificationRetry.duePending").value(10))
                .andExpect(jsonPath("$.data.searchIndexRetry.byStatus.running").value(4))
                .andExpect(jsonPath("$.data.outbox.status").value("DEGRADED"))
                .andExpect(jsonPath("$.data.outbox.attentionRequired").value(true))
                .andExpect(jsonPath("$.data.outbox.byStatus.failed").value(11))
                .andExpect(jsonPath("$.data.outbox.diagnostics.failedSample.id").value(3001))
                .andExpect(jsonPath("$.data.outbox.diagnostics.failedSample.topic").value("post.published"))
                .andExpect(jsonPath("$.data.outbox.diagnostics.recommendedAction").value("Open /api/v1/ops/outbox?status=2, confirm Kafka readiness, then retry failed test messages first."))
                .andExpect(jsonPath("$.data.opsWindow.windowMinutes").value(15))
                .andExpect(jsonPath("$.data.opsWindow.thresholdBreached").value(true))
                .andExpect(jsonPath("$.data.opsWindow.failedTotal").value(22))
                .andExpect(jsonPath("$.data.opsWindow.dueTotal").value(27))
                .andExpect(jsonPath("$.data.opsWindow.thresholds.dueTotalWarn").value(10))
                .andExpect(jsonPath("$.data.opsWindow.suggestedAction").value("Open the compensation queue, preview affected records, then execute single or batch retries with an audit reason."));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verify(notificationRetryService).status();
    }

    @Test
    void postSearchDiagnosticsExplainsSyntheticTestDataRecall() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        PostBriefDTO testDataPost = PostBriefDTO.builder()
                .id(701L)
                .postType(10)
                .title("CODEX-E2E postType 10 recall")
                .summary("synthetic test data")
                .createTime(LocalDateTime.now())
                .build();
        when(postFacade.getPost(701L, null)).thenReturn(PostDTO.builder().id(701L).title(testDataPost.getTitle()).build());
        when(postFacade.batchGetPosts(List.of(701L))).thenReturn(Map.of());
        when(postFacade.batchGetPosts(List.of(701L), true)).thenReturn(Map.of(701L, testDataPost));
        when(elasticsearch.enabled()).thenReturn(true);
        when(elasticsearch.available()).thenReturn(true);
        when(elasticsearch.postIndex()).thenReturn("post_idx");
        when(elasticsearch.getDocument("post_idx", "701")).thenReturn(Optional.empty());
        when(indexer.status()).thenReturn(Map.of("available", true, "indexReady", true));
        when(searchFacade.searchPosts(eq("701"), isNull(), isNull(), isNull(), eq("relevance"), isNull(), eq(5), eq(false)))
                .thenReturn(PageResult.empty());
        when(searchFacade.searchPosts(eq("701"), isNull(), isNull(), isNull(), eq("relevance"), isNull(), eq(5), eq(true)))
                .thenReturn(PageResult.of(List.of(testDataPost), null, false));

        mvc.perform(get("/api/v1/ops/search/post-diagnostics/701")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.post.publiclyVisible").value(false))
                .andExpect(jsonPath("$.data.post.visibleWithTestData").value(true))
                .andExpect(jsonPath("$.data.applicationSearch.idRecallDefault").value(false))
                .andExpect(jsonPath("$.data.applicationSearch.idRecallWithTestData").value(true))
                .andExpect(jsonPath("$.data.recommendation").value("ENABLE_TEST_DATA_MODE_FOR_CODEX_E2E_RECORDS"));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
    }

    @Test
    void kafkaLocalCheckIsReadOnlyAndExplainsMissingBroker() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(environment.getProperty("spring.kafka.bootstrap-servers", "localhost:9092")).thenReturn("127.0.0.1:1");
        when(environment.getProperty("offerlab.kafka.topic.post-published", "post.published")).thenReturn("post.published");
        when(environment.getProperty("offerlab.kafka.consumer.feed-group", "offerlab-feed-fanout")).thenReturn("offerlab-feed-fanout");

        mvc.perform(get("/api/v1/ops/kafka/local-check")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("read-only"))
                .andExpect(jsonPath("$.data.bootstrapServers").value("127.0.0.1:1"))
                .andExpect(jsonPath("$.data.topic").value("post.published"))
                .andExpect(jsonPath("$.data.consumerGroup").value("offerlab-feed-fanout"))
                .andExpect(jsonPath("$.data.tcpReachable").value(false))
                .andExpect(jsonPath("$.data.adminProbe.attempted").value(false))
                .andExpect(jsonPath("$.data.readyForOutboxReplay").value(false))
                .andExpect(jsonPath("$.data.blockedOperations[0]").value("mkdir"))
                .andExpect(jsonPath("$.data.safeGuide[0]").value("1. Run the read-only middleware check first: scripts/check-local-middleware.ps1"));

        verify(adminPermissionService).requireScope(7L, AdminPermissionService.ROLE_OPS);
        verifyNoInteractions(outboxMessageMapper);
    }

    @Test
    void permissionsExposeDomainModeratorAndModeratedDomains() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(88L);
        when(adminPermissionService.mode()).thenReturn("RBAC");
        when(domainModeratorService.listModeratedDomains(88L)).thenReturn(List.of(2, 5));

        mvc.perform(get("/api/v1/ops/me/permissions")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.uid").value(88))
                .andExpect(jsonPath("$.data.adminMode").value("RBAC"))
                .andExpect(jsonPath("$.data.admin").value(false))
                .andExpect(jsonPath("$.data.ops").value(false))
                .andExpect(jsonPath("$.data.contentModerator").value(false))
                .andExpect(jsonPath("$.data.domainModerator").value(true))
                .andExpect(jsonPath("$.data.moderatedDomains[0]").value(2))
                .andExpect(jsonPath("$.data.moderatedDomains[1]").value(5))
                .andExpect(jsonPath("$.data.questionOperator").value(false))
                .andExpect(jsonPath("$.data.localOpen").value(false));

        verify(domainModeratorService).listModeratedDomains(88L);
        verify(adminPermissionService, org.mockito.Mockito.times(2)).hasRole(88L, AdminPermissionService.ROLE_OPS);
        verify(adminPermissionService).hasRole(88L, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        verify(adminPermissionService).hasRole(88L, AdminPermissionService.ROLE_QUESTION_OPERATOR);
    }

    private static OutboxMessage outboxMessage() {
        return OutboxMessage.builder()
                .id(3001L)
                .aggregateType("post")
                .aggregateId(9001L)
                .topic("post.published")
                .msgStatus(OutboxMessageMapper.STATUS_FAILED)
                .retryCount(5)
                .nextRetryTime(LocalDateTime.parse("2026-06-08T23:30:00"))
                .build();
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
