package com.offerlab.community.search.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.utils.RiskConfirmation;
import com.offerlab.community.infra.audit.AdminAuditLog;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.infra.moderation.ModerationAdminService;
import com.offerlab.community.infra.moderation.ModerationKeywordHit;
import com.offerlab.community.infra.moderation.ModerationKeyword;
import com.offerlab.community.infra.moderation.ModerationKeywordAdminCmd;
import com.offerlab.community.infra.moderation.UserModerationState;
import com.offerlab.community.infra.moderation.UserModerationStateCmd;
import com.offerlab.community.infra.mq.outbox.OutboxMessage;
import com.offerlab.community.infra.mq.outbox.OutboxMessageMapper;
import com.offerlab.community.infra.ops.AdminOperationIdempotencyService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.AdminRoleMapper;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.notification.application.NotificationRetryService;
import com.offerlab.community.post.api.PublicContentFilter;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.search.api.SearchFacade;
import com.offerlab.community.search.api.dto.SearchAnalyticsDTO;
import com.offerlab.community.search.application.SearchIndexRetryService;
import com.offerlab.community.search.application.PostSearchIndexer;
import com.offerlab.community.search.application.SearchAnalyticsService;
import com.offerlab.community.search.infrastructure.persistence.mapper.SearchIndexRetryTaskMapper;
import com.offerlab.community.search.infrastructure.persistence.po.SearchIndexRetryTaskPO;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/ops")
@RequiredArgsConstructor
@Validated
public class OpsController {
    private static final int MAX_RETRY_BATCH_SIZE = 50;
    private static final int PREVIEW_EXPIRES_IN_SECONDS = 300;

    private final PostSearchIndexer indexer;
    private final SearchIndexRetryService searchIndexRetryService;
    private final SearchAnalyticsService searchAnalyticsService;
    private final NotificationRetryService notificationRetryService;
    private final SearchFacade searchFacade;
    private final PostFacade postFacade;
    private final ElasticsearchHttpClient elasticsearch;
    private final OutboxMessageMapper outboxMessageMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;
    private final DomainModeratorService domainModeratorService;
    private final ModerationAdminService moderationAdminService;
    private final MigrationCheckService migrationCheckService;
    private final UserFacade userFacade;
    private final AdminOperationIdempotencyService idempotencyService;
    private final Environment environment;

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_OPS);

        Map<String, Object> outbox = new LinkedHashMap<>();
        Map<String, Long> outboxByStatus = outboxCounts();
        long outboxDuePending = outboxMessageMapper.countDuePending();
        long outboxFailed = outboxByStatus.getOrDefault("failed", 0L);
        boolean outboxAttentionRequired = outboxFailed > 0 || outboxDuePending > 0;
        outbox.put("status", outboxAttentionRequired ? "DEGRADED" : "UP");
        outbox.put("byStatus", outboxByStatus);
        outbox.put("duePending", outboxDuePending);
        outbox.put("attentionRequired", outboxAttentionRequired);
        if (outboxAttentionRequired) {
            outbox.put("message", "Outbox has failed messages or due pending messages");
            outbox.put("action", "Review /api/v1/ops/outbox?status=2 and retry failed messages after Kafka is healthy.");
            outbox.put("diagnostics", outboxDiagnostics(outboxFailed, outboxDuePending));
        }

        Map<String, Object> searchIndexRetry = searchIndexRetryService.status();
        Map<String, Object> notificationRetry = notificationRetryService.status();
        Map<String, Object> opsWindow = opsWindow(outboxByStatus, outboxDuePending, searchIndexRetry, notificationRetry);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("adminWhitelistEnabled", adminPermissionService.whitelistEnabled());
        data.put("adminRoleEnabled", adminPermissionService.roleTableEnabled());
        data.put("adminMode", adminPermissionService.mode());
        data.put("search", indexer.status());
        data.put("searchIndexRetry", searchIndexRetry);
        data.put("notificationRetry", notificationRetry);
        data.put("outbox", outbox);
        data.put("opsWindow", opsWindow);
        return Result.ok(data);
    }

    @GetMapping("/kafka/local-check")
    public Result<Map<String, Object>> kafkaLocalCheck() {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        return Result.ok(kafkaLocalCheckData());
    }

    private Map<String, Object> kafkaLocalCheckData() {
        String bootstrapServers = environment.getProperty("spring.kafka.bootstrap-servers", "localhost:9092");
        String topic = environment.getProperty("offerlab.kafka.topic.post-published", "post.published");
        String consumerGroup = environment.getProperty("offerlab.kafka.consumer.feed-group", "offerlab-feed-fanout");
        Path logDir = Path.of("C:/codeware/kafka-data/offerlab-kraft-combined-logs");
        Path metadataDir = Path.of("C:/codeware/kafka-data/offerlab-metadata");
        Path projectConfig = Path.of("C:/project/offerlab-java/scripts/kafka/offerlab-server-local.properties");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", "read-only");
        data.put("bootstrapServers", bootstrapServers);
        data.put("topic", topic);
        data.put("consumerGroup", consumerGroup);
        data.put("configPath", projectConfig.toString());
        data.put("configExists", Files.isRegularFile(projectConfig));
        data.put("storage", Map.of(
                "logDir", pathStatus(logDir),
                "metadataDir", pathStatus(metadataDir)
        ));

        HostPort endpoint = firstEndpoint(bootstrapServers);
        boolean tcpReachable = endpoint != null && tcpReachable(endpoint.host(), endpoint.port(), Duration.ofMillis(650));
        data.put("tcpReachable", tcpReachable);
        data.put("endpoint", endpoint == null ? null : endpoint.host() + ":" + endpoint.port());

        Map<String, Object> admin = kafkaAdminProbe(bootstrapServers, topic, consumerGroup, tcpReachable);
        data.put("adminProbe", admin);
        data.put("readyForOutboxReplay", tcpReachable
                && Boolean.TRUE.equals(admin.get("topicExists"))
                && Boolean.TRUE.equals(admin.get("consumerGroupSeen")));
        data.put("safeGuide", List.of(
                "1. Run the read-only middleware check first: scripts/check-local-middleware.ps1",
                "2. If storage directories are missing, create and format them manually only after reviewing the exact paths.",
                "3. Start Kafka with scripts/start-local-kafka.ps1 after storage is already formatted.",
                "4. Confirm topic and consumer group are visible before retrying Outbox messages."
        ));
        data.put("blockedOperations", List.of("mkdir", "kafka-storage format", "kafka-server-start", "delete", "attribute changes"));
        return data;
    }

    @GetMapping("/me/permissions")
    public Result<Map<String, Object>> myPermissions() {
        Long uid = UserContext.require();
        boolean localOpen = adminPermissionService.isLocalOpenMode();
        boolean admin = adminPermissionService.isAdmin(uid) || localOpen;
        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("uid", uid);
        permissions.put("adminMode", adminPermissionService.mode());
        permissions.put("admin", admin);
        permissions.put("ops", admin || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_OPS));
        permissions.put("contentModerator", admin || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR));
        List<Integer> moderatedDomains = domainModeratorService.listModeratedDomains(uid);
        permissions.put("domainModerator", !moderatedDomains.isEmpty());
        permissions.put("moderatedDomains", moderatedDomains);
        permissions.put("questionOperator", admin || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR));
        permissions.put("localOpen", localOpen);
        return Result.ok(permissions);
    }

    @GetMapping("/outbox")
    public Result<List<OutboxMessage>> listOutbox(@RequestParam(required = false) Integer status,
                                                  @RequestParam(defaultValue = "20") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        return Result.ok(outboxMessageMapper.listRecent(status, clamp(limit)));
    }

    @GetMapping("/outbox/page")
    public Result<PageResult<OutboxMessage>> pageOutbox(@RequestParam(required = false) Integer status,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int pageSize) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        int safePageSize = clamp(pageSize);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safePageSize;
        long total = outboxMessageMapper.countPage(status);
        List<OutboxMessage> items = total <= offset
                ? List.of()
                : outboxMessageMapper.pageRecent(status, safePageSize, offset);
        return Result.ok(PageResult.<OutboxMessage>builder()
                .items(items)
                .hasMore(offset + items.size() < total)
                .total(total)
                .build());
    }

    @GetMapping("/outbox/{id}")
    public Result<OutboxMessage> getOutbox(@PathVariable Long id) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        OutboxMessage message = outboxMessageMapper.findById(id);
        if (message == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return Result.ok(message);
    }

    @PostMapping("/outbox/{id}/retry")
    public Result<Map<String, Object>> retryOutbox(@PathVariable Long id,
                                                   @Valid @RequestBody(required = false) ActionRemarkRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_OPS);
        OutboxMessage message = outboxMessageMapper.findById(id);
        if (message == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (message.getMsgStatus() == null || message.getMsgStatus() != 2) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        ensureOutboxReplayReady();
        List<Long> ids = List.of(id);
        String remark = RiskConfirmation.requireCritical(actionRemark(request, null),
                request == null ? null : request.confirmationPhrase());
        String idempotencyKey = idempotencyService.requireKey(request == null ? null : request.idempotencyKey());
        idempotencyService.requirePreview(uid, "OUTBOX_RETRY_BATCH", ids, request == null ? null : request.previewNonce());
        adminAuditService.requireWritable("OUTBOX_RETRY", "OUTBOX", id);
        idempotencyService.requireFresh(uid, "OUTBOX_RETRY_BATCH", ids, idempotencyKey);
        int updated = outboxMessageMapper.markFailedForRetry(id);
        adminAuditService.recordRequired(uid, "OUTBOX_RETRY", "OUTBOX", id, message,
                Map.of("retried", updated > 0, "idempotencyKey", idempotencyKey), remark);
        return Result.ok(Map.of("id", id, "retried", updated > 0, "idempotencyKey", idempotencyKey));
    }

    @PostMapping("/outbox/retry-batch")
    public Result<Map<String, Object>> retryOutboxBatch(@Valid @RequestBody OutboxRetryBatchRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_OPS);
        List<Long> ids = request.ids().stream()
                .distinct()
                .toList();
        String remark = RiskConfirmation.requireCritical(request.remark(), request.confirmationPhrase());
        String idempotencyKey = idempotencyService.requireKey(request.idempotencyKey());
        idempotencyService.requirePreview(uid, "OUTBOX_RETRY_BATCH", ids, request.previewNonce());
        ensureOutboxReplayReady();
        adminAuditService.requireWritable("OUTBOX_RETRY_BATCH", "OUTBOX", null);
        idempotencyService.requireFresh(uid, "OUTBOX_RETRY_BATCH", ids, idempotencyKey);
        int updated = outboxMessageMapper.markFailedForRetryBatch(ids);
        adminAuditService.recordRequired(uid, "OUTBOX_RETRY_BATCH", "OUTBOX", null, ids,
                Map.of("retried", updated, "idempotencyKey", idempotencyKey), remark);
        return Result.ok(Map.of("requested", ids.size(), "retried", updated, "idempotencyKey", idempotencyKey));
    }

    @PostMapping("/outbox/retry-batch/preview")
    public Result<Map<String, Object>> previewOutboxRetryBatch(@Valid @RequestBody OutboxRetryBatchRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_OPS);
        List<Long> ids = request.ids().stream()
                .distinct()
                .toList();
        return Result.ok(previewOutboxRetry(uid, ids));
    }

    @GetMapping("/admins")
    public Result<List<Map<String, Object>>> listAdmins(@RequestParam(defaultValue = "50") int limit) {
        adminPermissionService.requireAdmin(UserContext.require());
        return Result.ok(adminRoleMapper.listAdmins(clamp(limit)));
    }

    @PostMapping("/admins")
    public Result<Map<String, Object>> addAdmin(@Valid @RequestBody AdminRequest request) {
        Long operatorUid = UserContext.require();
        adminPermissionService.requireAdmin(operatorUid);
        Long targetUid = request.uid();
        String roleCode = normalizeRoleCode(request.roleCode());
        String remark = cleanRemark(request.remark());
        String auditRemark = RiskConfirmation.requireCritical(request.auditRemark(), request.confirmationPhrase());
        adminAuditService.requireWritable("ADMIN_ROLE_UPSERT", "ADMIN_ROLE", targetUid + ":" + roleCode);
        int updated = adminRoleMapper.upsertAdmin(targetUid, roleCode, remark, operatorUid);
        adminAuditService.recordRequired(operatorUid, "ADMIN_ROLE_UPSERT", "ADMIN_ROLE", targetUid + ":" + roleCode, null,
                Map.of("uid", targetUid, "roleCode", roleCode, "enabled", true),
                auditRemark);
        return Result.ok(Map.of("uid", targetUid, "roleCode", roleCode, "enabled", true, "updated", updated > 0));
    }

    @PostMapping("/admins/{uid}/status")
    public Result<Map<String, Object>> updateAdminStatus(@PathVariable @Positive Long uid,
                                                        @Valid @RequestBody AdminStatusRequest request) {
        Long operatorUid = UserContext.require();
        adminPermissionService.requireAdmin(operatorUid);
        String roleCode = normalizeRoleCode(request.roleCode());
        int enabled = Boolean.TRUE.equals(request.enabled()) ? 1 : 0;
        if (enabled == 0 && AdminPermissionService.ROLE_ADMIN.equals(roleCode)
                && Objects.equals(uid, operatorUid) && adminRoleMapper.countEnabledAdmins() <= 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        String auditRemark = RiskConfirmation.requireCritical(request.auditRemark(), request.confirmationPhrase());
        adminAuditService.requireWritable("ADMIN_ROLE_STATUS", "ADMIN_ROLE", uid + ":" + roleCode);
        int updated = adminRoleMapper.updateAdminStatus(uid, roleCode, enabled, cleanRemark(request.remark()), operatorUid);
        if (updated == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        adminAuditService.recordRequired(operatorUid, "ADMIN_ROLE_STATUS", "ADMIN_ROLE", uid + ":" + roleCode, null,
                Map.of("uid", uid, "roleCode", roleCode, "enabled", enabled == 1),
                auditRemark);
        return Result.ok(Map.of("uid", uid, "roleCode", roleCode, "enabled", enabled == 1, "updated", true));
    }

    @GetMapping("/audit-logs")
    public Result<List<AdminAuditLog>> listAuditLogs(@RequestParam(required = false) String action,
                                                     @RequestParam(required = false) String resourceType,
                                                     @RequestParam(defaultValue = "50") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        return Result.ok(adminAuditService.listRecent(action, resourceType, clamp(limit)));
    }

    @GetMapping("/audit-logs/page")
    public Result<PageResult<AdminAuditLog>> pageAuditLogs(@RequestParam(required = false) String action,
                                                           @RequestParam(required = false) String resourceType,
                                                           @RequestParam(required = false) Long operatorUid,
                                                           @RequestParam(required = false) String startDate,
                                                           @RequestParam(required = false) String endDate,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int pageSize) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        return Result.ok(adminAuditService.page(action, resourceType, normalizeUid(operatorUid),
                parseStartDate(startDate), parseEndDate(endDate), page, pageSize));
    }

    @GetMapping("/migration/status")
    public Result<Map<String, Object>> migrationStatus() {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        return Result.ok(migrationCheckService.governanceStatus());
    }

    @GetMapping("/search/analytics")
    public Result<SearchAnalyticsDTO> searchAnalytics(@RequestParam(defaultValue = "30") int days,
                                                      @RequestParam(defaultValue = "10") int limit,
                                                      @RequestParam(defaultValue = "false") boolean includeTestData) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        return Result.ok(searchAnalyticsService.summary(days, limit, includeTestData));
    }

    @GetMapping("/search/post-diagnostics/{postId}")
    public Result<Map<String, Object>> postSearchDiagnostics(@PathVariable @Positive Long postId) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);

        PostDTO post = postFacade.getPost(postId, null);
        PostBriefDTO publicBrief = postFacade.batchGetPosts(List.of(postId)).get(postId);
        PostBriefDTO testDataBrief = postFacade.batchGetPosts(List.of(postId), true).get(postId);
        PostBriefDTO diagnosticBrief = testDataBrief != null ? testDataBrief : publicBrief;
        boolean synthetic = diagnosticBrief != null && PublicContentFilter.isSyntheticPost(diagnosticBrief);

        Map<String, Object> postInfo = new LinkedHashMap<>();
        postInfo.put("id", postId);
        postInfo.put("found", post != null || diagnosticBrief != null);
        postInfo.put("publiclyVisible", publicBrief != null);
        postInfo.put("visibleWithTestData", testDataBrief != null);
        postInfo.put("synthetic", synthetic);
        postInfo.put("postType", diagnosticBrief == null ? null : diagnosticBrief.getPostType());
        postInfo.put("title", diagnosticBrief == null ? null : diagnosticBrief.getTitle());
        postInfo.put("createTime", diagnosticBrief == null || diagnosticBrief.getCreateTime() == null
                ? null : diagnosticBrief.getCreateTime().toString());

        Map<String, Object> esInfo = new LinkedHashMap<>();
        esInfo.put("enabled", elasticsearch.enabled());
        esInfo.put("available", elasticsearch.available());
        esInfo.put("indexName", elasticsearch.postIndex());
        esInfo.put("indexStatus", indexer.status());
        JsonNode document = elasticsearch.getDocument(elasticsearch.postIndex(), String.valueOf(postId)).orElse(null);
        boolean documentFound = document != null && document.path("found").asBoolean(false);
        esInfo.put("documentFound", documentFound);
        esInfo.put("document", summarizeDocument(document));

        PageResult<PostBriefDTO> defaultRecall = searchFacade.searchPosts(String.valueOf(postId), null, null,
                null, "relevance", null, 5, false);
        PageResult<PostBriefDTO> testDataRecall = searchFacade.searchPosts(String.valueOf(postId), null, null,
                null, "relevance", null, 5, true);
        Map<String, Object> appSearch = new LinkedHashMap<>();
        appSearch.put("idRecallDefault", containsPost(defaultRecall, postId));
        appSearch.put("idRecallWithTestData", containsPost(testDataRecall, postId));
        appSearch.put("defaultMeta", summarizePage(defaultRecall));
        appSearch.put("testDataMeta", summarizePage(testDataRecall));

        SearchIndexRetryTaskPO retryTask = searchIndexRetryService.findLatestByPostId(postId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("post", postInfo);
        data.put("elasticsearch", esInfo);
        data.put("applicationSearch", appSearch);
        data.put("latestRetryTask", summarizeRetryTask(retryTask));
        data.put("recommendation", diagnosticRecommendation(postInfo, esInfo, appSearch, retryTask));
        return Result.ok(data);
    }

    @GetMapping("/search-index-retry-tasks/status")
    public Result<Map<String, Object>> searchIndexRetryStatus() {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        return Result.ok(searchIndexRetryService.status());
    }

    @GetMapping("/search-index-retry-tasks")
    public Result<List<SearchIndexRetryTaskPO>> listSearchIndexRetryTasks(@RequestParam(required = false) Integer status,
                                                                          @RequestParam(defaultValue = "20") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        return Result.ok(searchIndexRetryService.listRecent(normalizeSearchRetryStatus(status), limit));
    }

    @GetMapping("/search-index-retry-tasks/page")
    public Result<PageResult<SearchIndexRetryTaskPO>> pageSearchIndexRetryTasks(@RequestParam(required = false) Integer status,
                                                                                @RequestParam(defaultValue = "1") int page,
                                                                                @RequestParam(defaultValue = "20") int pageSize) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        return Result.ok(searchIndexRetryService.pageRecent(normalizeSearchRetryStatus(status), page, pageSize));
    }

    @GetMapping("/search-index-retry-tasks/{id}")
    public Result<SearchIndexRetryTaskPO> getSearchIndexRetryTask(@PathVariable Long id) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        SearchIndexRetryTaskPO task = searchIndexRetryService.findById(id);
        if (task == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return Result.ok(task);
    }

    @PostMapping("/search-index-retry-tasks/{id}/replay")
    public Result<Map<String, Object>> replaySearchIndexRetryTask(@PathVariable Long id,
                                                                  @Valid @RequestBody(required = false) ActionRemarkRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_OPS);
        SearchIndexRetryTaskPO task = searchIndexRetryService.findById(id);
        if (task == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (task.getTaskStatus() == null || task.getTaskStatus() != SearchIndexRetryTaskMapper.STATUS_FAILED) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        List<Long> ids = List.of(id);
        String remark = RiskConfirmation.requireCritical(actionRemark(request, null),
                request == null ? null : request.confirmationPhrase());
        String idempotencyKey = idempotencyService.requireKey(request == null ? null : request.idempotencyKey());
        idempotencyService.requirePreview(uid, "SEARCH_INDEX_RETRY_REPLAY_BATCH", ids,
                request == null ? null : request.previewNonce());
        adminAuditService.requireWritable("SEARCH_INDEX_RETRY_REPLAY", "SEARCH_INDEX_RETRY_TASK", id);
        idempotencyService.requireFresh(uid, "SEARCH_INDEX_RETRY_REPLAY_BATCH", ids, idempotencyKey);
        boolean replayed = searchIndexRetryService.replayFailed(id);
        adminAuditService.recordRequired(uid, "SEARCH_INDEX_RETRY_REPLAY", "SEARCH_INDEX_RETRY_TASK", id,
                task, Map.of("replayed", replayed, "idempotencyKey", idempotencyKey), remark);
        return Result.ok(Map.of("id", id, "replayed", replayed, "idempotencyKey", idempotencyKey));
    }

    @PostMapping("/search-index-retry-tasks/replay-batch")
    public Result<Map<String, Object>> replaySearchIndexRetryTasks(@Valid @RequestBody SearchIndexRetryBatchRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_OPS);
        List<Long> ids = request.ids().stream()
                .distinct()
                .toList();
        String remark = RiskConfirmation.requireCritical(request.remark(), request.confirmationPhrase());
        String idempotencyKey = idempotencyService.requireKey(request.idempotencyKey());
        idempotencyService.requirePreview(uid, "SEARCH_INDEX_RETRY_REPLAY_BATCH", ids, request.previewNonce());
        adminAuditService.requireWritable("SEARCH_INDEX_RETRY_REPLAY_BATCH", "SEARCH_INDEX_RETRY_TASK", null);
        idempotencyService.requireFresh(uid, "SEARCH_INDEX_RETRY_REPLAY_BATCH", ids, idempotencyKey);
        int replayed = searchIndexRetryService.replayFailedBatch(ids);
        adminAuditService.recordRequired(uid, "SEARCH_INDEX_RETRY_REPLAY_BATCH", "SEARCH_INDEX_RETRY_TASK", null,
                ids, Map.of("replayed", replayed, "idempotencyKey", idempotencyKey), remark);
        return Result.ok(Map.of("requested", ids.size(), "replayed", replayed, "idempotencyKey", idempotencyKey));
    }

    @PostMapping("/search-index-retry-tasks/replay-batch/preview")
    public Result<Map<String, Object>> previewSearchIndexRetryBatch(@Valid @RequestBody SearchIndexRetryBatchRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_OPS);
        List<Long> ids = request.ids().stream()
                .distinct()
                .toList();
        return Result.ok(previewSearchIndexRetry(uid, ids));
    }

    @GetMapping("/moderation/keywords")
    public Result<List<ModerationKeyword>> listModerationKeywords(@RequestParam(required = false) String keyword,
                                                                  @RequestParam(required = false) String scope,
                                                                  @RequestParam(defaultValue = "50") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(moderationAdminService.listKeywords(keyword, scope, clamp(limit)));
    }

    @GetMapping("/moderation/hits")
    public Result<List<ModerationKeywordHit>> listModerationKeywordHits(@RequestParam(required = false) String scope,
                                                                        @RequestParam(required = false) String action,
                                                                        @RequestParam(required = false) Long uid,
                                                                        @RequestParam(required = false) String keyword,
                                                                        @RequestParam(defaultValue = "50") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(moderationAdminService.listKeywordHits(scope, action, uid, keyword, clamp(limit)));
    }

    @PostMapping("/moderation/keywords")
    public Result<ModerationKeyword> createModerationKeyword(@RequestBody ModerationKeywordAdminCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        adminAuditService.requireWritable("MODERATION_KEYWORD_CREATE", "MODERATION_KEYWORD", null);
        ModerationKeyword keyword = moderationAdminService.saveKeyword(null, cmd, uid);
        adminAuditService.recordRequired(uid, "MODERATION_KEYWORD_CREATE", "MODERATION_KEYWORD", keyword.getId(), null,
                keyword, auditRemark(cmd == null ? null : cmd.getAuditRemark(), cmd == null ? null : cmd.getRemark()));
        return Result.ok(keyword);
    }

    @PostMapping("/moderation/keywords/{id}")
    public Result<ModerationKeyword> updateModerationKeyword(@PathVariable Long id,
                                                             @RequestBody ModerationKeywordAdminCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        adminAuditService.requireWritable("MODERATION_KEYWORD_UPDATE", "MODERATION_KEYWORD", id);
        ModerationKeyword keyword = moderationAdminService.saveKeyword(id, cmd, uid);
        adminAuditService.recordRequired(uid, "MODERATION_KEYWORD_UPDATE", "MODERATION_KEYWORD", id, null,
                keyword, auditRemark(cmd == null ? null : cmd.getAuditRemark(), cmd == null ? null : cmd.getRemark()));
        return Result.ok(keyword);
    }

    @PostMapping("/moderation/keywords/{id}/status")
    public Result<Map<String, Object>> updateModerationKeywordStatus(@PathVariable Long id,
                                                                     @RequestParam int enabled,
                                                                     @Valid @RequestBody(required = false) ActionRemarkRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        String remark = RiskConfirmation.requireHigh(actionRemark(request, null));
        adminAuditService.requireWritable("MODERATION_KEYWORD_STATUS", "MODERATION_KEYWORD", id);
        Map<String, Object> result = moderationAdminService.updateKeywordStatus(id, enabled, uid);
        adminAuditService.recordRequired(uid, "MODERATION_KEYWORD_STATUS", "MODERATION_KEYWORD", id, null, result, remark);
        return Result.ok(result);
    }

    @GetMapping("/moderation/users")
    public Result<List<UserModerationState>> listModerationUsers(@RequestParam(defaultValue = "50") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(enrichUserBriefs(moderationAdminService.listUserStates(clamp(limit))));
    }

    @PostMapping("/moderation/users")
    public Result<UserModerationState> saveModerationUser(@RequestBody UserModerationStateCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        adminAuditService.requireWritable("USER_MODERATION_STATE", "USER", cmd == null ? null : cmd.getUid());
        UserModerationState state = moderationAdminService.saveUserState(cmd, uid);
        enrichUserBrief(state);
        adminAuditService.recordRequired(uid, "USER_MODERATION_STATE", "USER", state.getUid(), null, state,
                auditRemark(cmd == null ? null : cmd.getAuditRemark(), cmd == null ? null : cmd.getReason()));
        return Result.ok(state);
    }

    @PostMapping("/moderation/users/{targetUid}/clear-mute")
    public Result<UserModerationState> clearModerationUserMute(@PathVariable Long targetUid,
                                                               @Valid @RequestBody(required = false) ActionRemarkRequest request) {
        Long operatorUid = UserContext.require();
        adminPermissionService.requireScope(operatorUid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        String remark = RiskConfirmation.requireHigh(actionRemark(request, null));
        adminAuditService.requireWritable("USER_MODERATION_CLEAR_MUTE", "USER", targetUid);
        UserModerationState state = moderationAdminService.clearUserMute(targetUid, operatorUid, remark);
        enrichUserBrief(state);
        adminAuditService.recordRequired(operatorUid, "USER_MODERATION_CLEAR_MUTE", "USER", targetUid, null, state, remark);
        return Result.ok(state);
    }

    @PostMapping("/moderation/users/{targetUid}/clear-ban")
    public Result<UserModerationState> clearModerationUserBan(@PathVariable Long targetUid,
                                                              @Valid @RequestBody(required = false) ActionRemarkRequest request) {
        Long operatorUid = UserContext.require();
        adminPermissionService.requireScope(operatorUid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        String remark = RiskConfirmation.requireHigh(actionRemark(request, null));
        adminAuditService.requireWritable("USER_MODERATION_CLEAR_BAN", "USER", targetUid);
        UserModerationState state = moderationAdminService.clearUserBan(targetUid, operatorUid, remark);
        enrichUserBrief(state);
        adminAuditService.recordRequired(operatorUid, "USER_MODERATION_CLEAR_BAN", "USER", targetUid, null, state, remark);
        return Result.ok(state);
    }

    private List<UserModerationState> enrichUserBriefs(List<UserModerationState> states) {
        if (states == null || states.isEmpty()) {
            return states;
        }
        List<Long> uids = states.stream()
                .filter(Objects::nonNull)
                .map(UserModerationState::getUid)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (uids.isEmpty()) {
            return states;
        }
        Map<Long, UserBriefDTO> briefs = userFacade.batchGetUserBriefs(uids);
        states.stream().filter(Objects::nonNull).forEach(state -> applyUserBrief(state, briefs.get(state.getUid())));
        return states;
    }

    private void enrichUserBrief(UserModerationState state) {
        if (state == null || state.getUid() == null) {
            return;
        }
        applyUserBrief(state, userFacade.getUserBrief(state.getUid()));
    }

    private void applyUserBrief(UserModerationState state, UserBriefDTO brief) {
        if (state == null || brief == null) {
            return;
        }
        state.setNickname(brief.getNickname());
        state.setAvatarUrl(brief.getAvatarUrl());
    }

    private Map<String, Long> outboxCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("pending", 0L);
        counts.put("sent", 0L);
        counts.put("failed", 0L);
        for (Map<String, Object> row : outboxMessageMapper.countByStatus()) {
            String key = statusName(row.get("status"));
            counts.put(key, asLong(row.get("count")));
        }
        return counts;
    }

    private Map<String, Object> outboxDiagnostics(long failed, long duePending) {
        OutboxMessage failedSample = failed > 0 ? firstOutbox(OutboxMessageMapper.STATUS_FAILED) : null;
        OutboxMessage pendingSample = duePending > 0 ? firstOutbox(OutboxMessageMapper.STATUS_PENDING) : null;
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("failedSample", summarizeOutbox(failedSample));
        diagnostics.put("pendingSample", summarizeOutbox(pendingSample));
        diagnostics.put("recommendedAction", "Open /api/v1/ops/outbox?status=2, confirm Kafka readiness, then retry failed test messages first.");
        return diagnostics;
    }

    private Map<String, Object> opsWindow(Map<String, Long> outboxByStatus,
                                          long outboxDuePending,
                                          Map<String, Object> searchIndexRetry,
                                          Map<String, Object> notificationRetry) {
        long searchFailed = statusCount(searchIndexRetry, "failed");
        long searchPending = statusCount(searchIndexRetry, "pending");
        long searchDue = asLong(searchIndexRetry.get("duePending"));
        long notificationFailed = statusCount(notificationRetry, "failed");
        long notificationPending = statusCount(notificationRetry, "pending");
        long notificationDue = asLong(notificationRetry.get("duePending"));
        long outboxFailed = outboxByStatus.getOrDefault("failed", 0L);
        long outboxPending = outboxByStatus.getOrDefault("pending", 0L);
        long failedTotal = outboxFailed + searchFailed + notificationFailed;
        long dueTotal = outboxDuePending + searchDue + notificationDue;
        boolean thresholdBreached = failedTotal > 0 || dueTotal >= 10L || outboxPending >= 50L || searchPending >= 50L || notificationPending >= 50L;

        Map<String, Object> thresholds = new LinkedHashMap<>();
        thresholds.put("failedTotalWarn", 1L);
        thresholds.put("dueTotalWarn", 10L);
        thresholds.put("pendingQueueWarn", 50L);

        Map<String, Object> window = new LinkedHashMap<>();
        window.put("windowMinutes", 15);
        window.put("thresholdBreached", thresholdBreached);
        window.put("failedTotal", failedTotal);
        window.put("dueTotal", dueTotal);
        window.put("pendingTotal", outboxPending + searchPending + notificationPending);
        window.put("thresholds", thresholds);
        window.put("impact", thresholdBreached
                ? "Async consistency, search freshness, notification delivery, and AI knowledge retention may require operator attention."
                : "No queue threshold is breached in the current duty window.");
        window.put("suggestedAction", thresholdBreached
                ? "Open the compensation queue, preview affected records, then execute single or batch retries with an audit reason."
                : "Keep watching the 15-minute duty window and copy the diagnostic package if another alert appears.");
        return window;
    }

    @SuppressWarnings("unchecked")
    private static long statusCount(Map<String, Object> status, String key) {
        Object byStatus = status.get("byStatus");
        if (!(byStatus instanceof Map<?, ?> map)) {
            return 0L;
        }
        return asLong(((Map<Object, Object>) map).get(key));
    }

    private OutboxMessage firstOutbox(Integer status) {
        try {
            List<OutboxMessage> rows = outboxMessageMapper.listRecent(status, 1);
            return rows == null || rows.isEmpty() ? null : rows.get(0);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Map<String, Object> summarizeOutbox(OutboxMessage message) {
        if (message == null) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", message.getId());
        data.put("aggregateType", message.getAggregateType());
        data.put("aggregateId", message.getAggregateId());
        data.put("topic", message.getTopic());
        data.put("retryCount", message.getRetryCount());
        data.put("nextRetryTime", message.getNextRetryTime() == null ? null : message.getNextRetryTime().toString());
        return data;
    }

    private Map<String, Object> previewOutboxRetry(Long uid, List<Long> ids) {
        if (!outboxReplayCheckRequired()) {
            List<Map<String, Object>> items = ids.stream()
                    .map(id -> {
                        OutboxMessage message = outboxMessageMapper.findById(id);
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", id);
                        if (message == null) {
                            item.put("eligible", false);
                            item.put("reason", "NOT_FOUND");
                            item.put("reasonText", "消息不存在");
                            return item;
                        }
                        boolean eligible = message.getMsgStatus() != null && message.getMsgStatus() == 2;
                        item.put("eligible", eligible);
                        item.put("reason", eligible ? "READY" : "STATUS_NOT_FAILED");
                        item.put("reasonText", eligible ? "失败消息，可重试" : "当前状态不是失败，不会被重试");
                        item.put("status", message.getMsgStatus());
                        item.put("statusText", statusName(message.getMsgStatus()));
                        item.put("objectLabel", message.getTopic() + ":" + message.getAggregateId());
                        item.put("retryCount", message.getRetryCount());
                        return item;
                    })
                    .toList();
            return previewResult(uid, "OUTBOX_RETRY_BATCH", ids, items);
        }
        Map<String, Object> kafka = kafkaLocalCheckData();
        if (!Boolean.TRUE.equals(kafka.get("readyForOutboxReplay"))) {
            List<Map<String, Object>> blockedItems = ids.stream()
                    .map(id -> {
                        Map<String, Object> kafkaSummary = new LinkedHashMap<>();
                        kafkaSummary.put("readyForOutboxReplay", false);
                        kafkaSummary.put("tcpReachable", kafka.get("tcpReachable"));
                        kafkaSummary.put("endpoint", kafka.get("endpoint"));
                        kafkaSummary.put("adminProbe", kafka.get("adminProbe"));
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", id);
                        item.put("eligible", false);
                        item.put("reason", "KAFKA_NOT_READY");
                        item.put("reasonText", "Kafka/topic/consumer group 未就绪，先按本地向导恢复后再重试 Outbox。");
                        item.put("kafka", kafkaSummary);
                        return item;
                    })
                    .toList();
            Map<String, Object> result = previewResult(uid, "OUTBOX_RETRY_BATCH", ids, blockedItems);
            result.put("blocked", true);
            result.put("blockedReason", "KAFKA_NOT_READY");
            result.put("kafkaLocalCheck", kafka);
            return result;
        }
        List<Map<String, Object>> items = ids.stream()
                .map(id -> {
                    OutboxMessage message = outboxMessageMapper.findById(id);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", id);
                    if (message == null) {
                        item.put("eligible", false);
                        item.put("reason", "NOT_FOUND");
                        item.put("reasonText", "消息不存在");
                        return item;
                    }
                    boolean eligible = message.getMsgStatus() != null && message.getMsgStatus() == 2;
                    item.put("eligible", eligible);
                    item.put("reason", eligible ? "READY" : "STATUS_NOT_FAILED");
                    item.put("reasonText", eligible ? "失败消息，可重试" : "当前状态不是失败，不会被重试");
                    item.put("status", message.getMsgStatus());
                    item.put("statusText", statusName(message.getMsgStatus()));
                    item.put("objectLabel", message.getTopic() + ":" + message.getAggregateId());
                    item.put("retryCount", message.getRetryCount());
                    return item;
                })
                .toList();
        return previewResult(uid, "OUTBOX_RETRY_BATCH", ids, items);
    }

    private void ensureOutboxReplayReady() {
        if (!outboxReplayCheckRequired()) {
            return;
        }
        Map<String, Object> kafka = kafkaLocalCheckData();
        if (!Boolean.TRUE.equals(kafka.get("readyForOutboxReplay"))) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private boolean outboxReplayCheckRequired() {
        String required = environment.getProperty("offerlab.kafka.outbox-replay.require-local-check", "true");
        String activeProfiles = environment.getProperty("spring.profiles.active", "");
        return !("false".equalsIgnoreCase(required) && activeProfiles != null && activeProfiles.contains("test"));
    }

    private Map<String, Object> previewSearchIndexRetry(Long uid, List<Long> ids) {
        List<Map<String, Object>> items = ids.stream()
                .map(id -> {
                    SearchIndexRetryTaskPO task = searchIndexRetryService.findById(id);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", id);
                    if (task == null) {
                        item.put("eligible", false);
                        item.put("reason", "NOT_FOUND");
                        item.put("reasonText", "补偿任务不存在");
                        return item;
                    }
                    boolean eligible = task.getTaskStatus() != null && task.getTaskStatus() == SearchIndexRetryTaskMapper.STATUS_FAILED;
                    item.put("eligible", eligible);
                    item.put("reason", eligible ? "READY" : "STATUS_NOT_FAILED");
                    item.put("reasonText", eligible ? "失败任务，可重放" : "当前状态不是失败，不会被重放");
                    item.put("status", task.getTaskStatus());
                    item.put("statusText", searchRetryStatusName(task.getTaskStatus()));
                    item.put("objectLabel", "post:" + task.getPostId());
                    item.put("operation", task.getOperation());
                    item.put("retryCount", task.getRetryCount());
                    return item;
                })
                .toList();
        return previewResult(uid, "SEARCH_INDEX_RETRY_REPLAY_BATCH", ids, items);
    }

    private Map<String, Object> previewResult(Long uid, String operation, List<Long> ids, List<Map<String, Object>> items) {
        long eligible = items.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("eligible")))
                .count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operation", operation);
        result.put("previewNonce", idempotencyService.issuePreview(uid, operation, ids));
        result.put("requested", ids.size());
        result.put("eligible", eligible);
        result.put("skipped", ids.size() - eligible);
        result.put("estimatedImpact", eligible);
        result.put("maxBatchSize", MAX_RETRY_BATCH_SIZE);
        result.put("previewExpiresInSeconds", PREVIEW_EXPIRES_IN_SECONDS);
        result.put("requiresAuditReason", true);
        result.put("confirmationPhrase", RiskConfirmation.CONFIRM_PHRASE);
        result.put("riskReason", previewRiskReason(eligible, ids.size() - eligible, items));
        result.put("items", items);
        return result;
    }

    private static boolean containsPost(PageResult<PostBriefDTO> page, Long postId) {
        return page != null && page.getItems() != null && page.getItems().stream()
                .anyMatch(post -> Objects.equals(post.getId(), postId));
    }

    private static Map<String, Object> summarizePage(PageResult<PostBriefDTO> page) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (page == null) {
            data.put("items", 0);
            return data;
        }
        data.put("items", page.getItems() == null ? 0 : page.getItems().size());
        data.put("source", page.getSource());
        data.put("degraded", page.getDegraded());
        data.put("fallbackReason", page.getFallbackReason());
        data.put("scanLimit", page.getScanLimit());
        data.put("diagnostics", page.getDiagnostics());
        return data;
    }

    private static Map<String, Object> summarizeDocument(JsonNode document) {
        if (document == null || !document.path("found").asBoolean(false)) {
            return null;
        }
        JsonNode source = document.path("_source");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", source.path("id").asText(null));
        data.put("postId", source.path("postId").isMissingNode() ? null : source.path("postId").asLong());
        data.put("type", source.path("type").isMissingNode() ? null : source.path("type").asInt());
        data.put("title", source.path("title").asText(null));
        data.put("status", source.path("status").asText(null));
        data.put("visibility", source.path("visibility").isMissingNode() ? null : source.path("visibility").asInt());
        data.put("createTime", source.path("createTime").isMissingNode() ? null : source.path("createTime").asLong());
        return data;
    }

    private static Map<String, Object> summarizeRetryTask(SearchIndexRetryTaskPO task) {
        if (task == null) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", task.getId());
        data.put("operation", task.getOperation());
        data.put("status", task.getTaskStatus());
        data.put("statusText", searchRetryStatusName(task.getTaskStatus()));
        data.put("retryCount", task.getRetryCount());
        data.put("nextRetryTime", task.getNextRetryTime() == null ? null : task.getNextRetryTime().toString());
        data.put("lastError", task.getLastError());
        data.put("updateTime", task.getUpdateTime() == null ? null : task.getUpdateTime().toString());
        return data;
    }

    private static String diagnosticRecommendation(Map<String, Object> postInfo,
                                                   Map<String, Object> esInfo,
                                                   Map<String, Object> appSearch,
                                                   SearchIndexRetryTaskPO retryTask) {
        if (!Boolean.TRUE.equals(postInfo.get("found"))) {
            return "POST_NOT_FOUND_OR_NOT_PUBLIC";
        }
        if (Boolean.TRUE.equals(postInfo.get("synthetic"))
                && !Boolean.TRUE.equals(appSearch.get("idRecallDefault"))
                && Boolean.TRUE.equals(appSearch.get("idRecallWithTestData"))) {
            return "ENABLE_TEST_DATA_MODE_FOR_CODEX_E2E_RECORDS";
        }
        if (!Boolean.TRUE.equals(esInfo.get("documentFound")) && retryTask != null) {
            return "CHECK_SEARCH_INDEX_RETRY_TASK";
        }
        if (!Boolean.TRUE.equals(esInfo.get("documentFound"))) {
            return "REINDEX_POST_AFTER_ELASTICSEARCH_READY";
        }
        if (!Boolean.TRUE.equals(appSearch.get("idRecallDefault"))) {
            return "CHECK_APPLICATION_FILTERS_TYPE_AND_VISIBILITY";
        }
        return "OK";
    }

    private static Map<String, Object> pathStatus(Path path) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("path", path.toString());
        status.put("exists", Files.exists(path));
        status.put("directory", Files.isDirectory(path));
        status.put("readable", Files.isReadable(path));
        status.put("writable", Files.isWritable(path));
        return status;
    }

    private static HostPort firstEndpoint(String bootstrapServers) {
        String value = clean(bootstrapServers);
        if (value == null) {
            return null;
        }
        for (String raw : value.split(",")) {
            String endpoint = raw == null ? "" : raw.trim();
            if (endpoint.isBlank()) {
                continue;
            }
            int split = endpoint.lastIndexOf(':');
            if (split <= 0 || split >= endpoint.length() - 1) {
                continue;
            }
            try {
                int port = Integer.parseInt(endpoint.substring(split + 1));
                if (port > 0 && port <= 65535) {
                    return new HostPort(endpoint.substring(0, split), port);
                }
            } catch (NumberFormatException ignored) {
                // Try the next bootstrap endpoint.
            }
        }
        return null;
    }

    private static boolean tcpReachable(String host, int port, Duration timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), Math.toIntExact(timeout.toMillis()));
            return true;
        } catch (RuntimeException | java.io.IOException e) {
            return false;
        }
    }

    private static Map<String, Object> kafkaAdminProbe(String bootstrapServers,
                                                       String topic,
                                                       String consumerGroup,
                                                       boolean tcpReachable) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("attempted", tcpReachable);
        data.put("topicExists", false);
        data.put("consumerGroupSeen", false);
        if (!tcpReachable) {
            data.put("message", "Kafka TCP endpoint is not reachable; AdminClient probe skipped.");
            return data;
        }
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "900");
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "1200");
        try (AdminClient admin = AdminClient.create(props)) {
            Set<String> topics = admin.listTopics().names().get(1300, TimeUnit.MILLISECONDS);
            data.put("topicExists", topics.contains(topic));
            data.put("topicCount", topics.size());
            List<String> groups = admin.listConsumerGroups().all().get(1300, TimeUnit.MILLISECONDS).stream()
                    .map(ConsumerGroupListing::groupId)
                    .toList();
            data.put("consumerGroupSeen", groups.contains(consumerGroup));
            data.put("consumerGroupCount", groups.size());
            data.put("message", "Kafka AdminClient read-only probe completed.");
        } catch (Exception e) {
            data.put("message", "Kafka AdminClient probe failed: " + e.getClass().getSimpleName());
            data.put("error", cleanRemark(e.getMessage()));
        }
        return data;
    }

    private record HostPort(String host, int port) {
    }

    private static String previewRiskReason(long eligible, long skipped, List<Map<String, Object>> items) {
        if (skipped == 0) {
            return "ALL_READY";
        }
        String reasons = items.stream()
                .filter(item -> !Boolean.TRUE.equals(item.get("eligible")))
                .map(item -> String.valueOf(item.getOrDefault("reason", "UNKNOWN")))
                .distinct()
                .limit(3)
                .reduce((left, right) -> left + "," + right)
                .orElse("UNKNOWN");
        return "PARTIAL_SKIPPED:" + reasons + ";READY=" + eligible + ";SKIPPED=" + skipped;
    }

    private static String statusName(Object status) {
        int value = status instanceof Number number ? number.intValue() : -1;
        return switch (value) {
            case 0 -> "pending";
            case 1 -> "sent";
            case 2 -> "failed";
            default -> "unknown";
        };
    }

    private static String searchRetryStatusName(Object status) {
        int value = status instanceof Number number ? number.intValue() : -1;
        return switch (value) {
            case SearchIndexRetryTaskMapper.STATUS_PENDING -> "pending";
            case SearchIndexRetryTaskMapper.STATUS_DONE -> "done";
            case SearchIndexRetryTaskMapper.STATUS_FAILED -> "failed";
            case SearchIndexRetryTaskMapper.STATUS_RUNNING -> "running";
            default -> "unknown";
        };
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private static int clamp(int limit) {
        if (limit < 1) {
            return 20;
        }
        return Math.min(limit, 100);
    }

    private static Long normalizeUid(Long uid) {
        return uid == null || uid <= 0 ? null : uid;
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static LocalDateTime parseStartDate(String value) {
        String clean = clean(value);
        if (clean == null) {
            return null;
        }
        try {
            return LocalDate.parse(clean).atStartOfDay();
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static LocalDateTime parseEndDate(String value) {
        String clean = clean(value);
        if (clean == null) {
            return null;
        }
        try {
            return LocalDate.parse(clean).plusDays(1).atStartOfDay().minusNanos(1);
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static String cleanRemark(String remark) {
        if (remark == null) {
            return "";
        }
        String value = remark.trim();
        if (value.length() > 200) {
            return value.substring(0, 200);
        }
        return value;
    }

    private static String cleanAuditRemark(String remark) {
        if (!StringUtils.hasText(remark)) {
            return null;
        }
        String value = remark.trim();
        if (value.length() > 500) {
            return value.substring(0, 500);
        }
        return value;
    }

    private static String auditRemark(String primary, String fallback) {
        String value = cleanAuditRemark(primary);
        return value == null ? cleanAuditRemark(fallback) : value;
    }

    private static String actionRemark(ActionRemarkRequest request, String fallback) {
        if (request == null) {
            return cleanAuditRemark(fallback);
        }
        return auditRemark(request.remark(), request.reason() == null ? fallback : request.reason());
    }

    private static Integer normalizeSearchRetryStatus(Integer status) {
        if (status == null) {
            return null;
        }
        if (status == SearchIndexRetryTaskMapper.STATUS_PENDING
                || status == SearchIndexRetryTaskMapper.STATUS_DONE
                || status == SearchIndexRetryTaskMapper.STATUS_FAILED
                || status == SearchIndexRetryTaskMapper.STATUS_RUNNING) {
            return status;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private static String normalizeRoleCode(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return AdminPermissionService.ROLE_ADMIN;
        }
        String normalized = roleCode.trim().toUpperCase();
        if (List.of(
                AdminPermissionService.ROLE_ADMIN,
                AdminPermissionService.ROLE_CONTENT_MODERATOR,
                AdminPermissionService.ROLE_QUESTION_OPERATOR,
                AdminPermissionService.ROLE_OPS
        ).contains(normalized)) {
            return normalized;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    public record AdminRequest(
            @NotNull @Positive Long uid,
            @Pattern(regexp = "ADMIN|CONTENT_MODERATOR|QUESTION_OPERATOR|OPS") String roleCode,
            @Size(max = 200) String remark,
            @Size(max = 500) String auditRemark,
            @Size(max = 32) String confirmationPhrase) {
    }

    public record AdminStatusRequest(
            @NotNull Boolean enabled,
            @Pattern(regexp = "ADMIN|CONTENT_MODERATOR|QUESTION_OPERATOR|OPS") String roleCode,
            @Size(max = 200) String remark,
            @Size(max = 500) String auditRemark,
            @Size(max = 32) String confirmationPhrase) {
    }

    public record OutboxRetryBatchRequest(
            @NotEmpty @Size(max = MAX_RETRY_BATCH_SIZE) List<@NotNull @Positive Long> ids,
            @Size(max = 500) String remark,
            @Size(max = 32) String confirmationPhrase,
            @Size(max = 80) String idempotencyKey,
            @Size(max = 80) String previewNonce) {
    }

    public record SearchIndexRetryBatchRequest(
            @NotEmpty @Size(max = MAX_RETRY_BATCH_SIZE) List<@NotNull @Positive Long> ids,
            @Size(max = 500) String remark,
            @Size(max = 32) String confirmationPhrase,
            @Size(max = 80) String idempotencyKey,
            @Size(max = 80) String previewNonce) {
    }

    public record ActionRemarkRequest(
            @Size(max = 500) String remark,
            @Size(max = 500) String reason,
            @Size(max = 32) String confirmationPhrase,
            @Size(max = 80) String idempotencyKey,
            @Size(max = 80) String previewNonce) {
    }
}
