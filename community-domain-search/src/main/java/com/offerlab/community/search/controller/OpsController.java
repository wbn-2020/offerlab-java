package com.offerlab.community.search.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.audit.AdminAuditLog;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.moderation.ModerationAdminService;
import com.offerlab.community.infra.moderation.ModerationKeywordHit;
import com.offerlab.community.infra.moderation.ModerationKeyword;
import com.offerlab.community.infra.moderation.ModerationKeywordAdminCmd;
import com.offerlab.community.infra.moderation.UserModerationState;
import com.offerlab.community.infra.moderation.UserModerationStateCmd;
import com.offerlab.community.infra.mq.outbox.OutboxMessage;
import com.offerlab.community.infra.mq.outbox.OutboxMessageMapper;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.AdminRoleMapper;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.search.api.dto.SearchAnalyticsDTO;
import com.offerlab.community.search.application.SearchIndexRetryService;
import com.offerlab.community.search.application.PostSearchIndexer;
import com.offerlab.community.search.application.SearchAnalyticsService;
import com.offerlab.community.search.infrastructure.persistence.mapper.SearchIndexRetryTaskMapper;
import com.offerlab.community.search.infrastructure.persistence.po.SearchIndexRetryTaskPO;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
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

@RestController
@RequestMapping("/api/v1/ops")
@RequiredArgsConstructor
public class OpsController {

    private final PostSearchIndexer indexer;
    private final SearchIndexRetryService searchIndexRetryService;
    private final SearchAnalyticsService searchAnalyticsService;
    private final OutboxMessageMapper outboxMessageMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;
    private final ModerationAdminService moderationAdminService;
    private final MigrationCheckService migrationCheckService;
    private final UserFacade userFacade;

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_OPS);

        Map<String, Object> outbox = new LinkedHashMap<>();
        outbox.put("byStatus", outboxCounts());
        outbox.put("duePending", outboxMessageMapper.countDuePending());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("adminWhitelistEnabled", adminPermissionService.whitelistEnabled());
        data.put("adminRoleEnabled", adminPermissionService.roleTableEnabled());
        data.put("adminMode", adminPermissionService.mode());
        data.put("search", indexer.status());
        data.put("searchIndexRetry", searchIndexRetryService.status());
        data.put("outbox", outbox);
        return Result.ok(data);
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
    public Result<Map<String, Object>> retryOutbox(@PathVariable Long id) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_OPS);
        OutboxMessage message = outboxMessageMapper.findById(id);
        if (message == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (message.getMsgStatus() == null || message.getMsgStatus() != 2) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        int updated = outboxMessageMapper.markFailedForRetry(id);
        adminAuditService.record(uid, "OUTBOX_RETRY", "OUTBOX", id, message, Map.of("retried", updated > 0), null);
        return Result.ok(Map.of("id", id, "retried", updated > 0));
    }

    @PostMapping("/outbox/retry-batch")
    public Result<Map<String, Object>> retryOutboxBatch(@RequestBody OutboxRetryBatchRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_OPS);
        List<Long> ids = request == null || request.ids() == null ? List.of() : request.ids().stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(100)
                .toList();
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int updated = outboxMessageMapper.markFailedForRetryBatch(ids);
        adminAuditService.record(uid, "OUTBOX_RETRY_BATCH", "OUTBOX", null, ids, Map.of("retried", updated), null);
        return Result.ok(Map.of("requested", ids.size(), "retried", updated));
    }

    @GetMapping("/admins")
    public Result<List<Map<String, Object>>> listAdmins(@RequestParam(defaultValue = "50") int limit) {
        adminPermissionService.requireAdmin(UserContext.require());
        return Result.ok(adminRoleMapper.listAdmins(clamp(limit)));
    }

    @PostMapping("/admins")
    public Result<Map<String, Object>> addAdmin(@RequestBody AdminRequest request) {
        Long operatorUid = UserContext.require();
        adminPermissionService.requireAdmin(operatorUid);
        Long targetUid = requireTargetUid(request);
        String roleCode = normalizeRoleCode(request == null ? null : request.roleCode());
        String remark = cleanRemark(request == null ? null : request.remark());
        int updated = adminRoleMapper.upsertAdmin(targetUid, roleCode, remark, operatorUid);
        adminAuditService.record(operatorUid, "ADMIN_ROLE_UPSERT", "ADMIN_ROLE", targetUid + ":" + roleCode, null,
                Map.of("uid", targetUid, "roleCode", roleCode, "enabled", true), remark);
        return Result.ok(Map.of("uid", targetUid, "roleCode", roleCode, "enabled", true, "updated", updated > 0));
    }

    @PostMapping("/admins/{uid}/status")
    public Result<Map<String, Object>> updateAdminStatus(@PathVariable Long uid,
                                                        @RequestBody AdminStatusRequest request) {
        Long operatorUid = UserContext.require();
        adminPermissionService.requireAdmin(operatorUid);
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String roleCode = normalizeRoleCode(request == null ? null : request.roleCode());
        int enabled = request != null && Boolean.TRUE.equals(request.enabled()) ? 1 : 0;
        if (enabled == 0 && AdminPermissionService.ROLE_ADMIN.equals(roleCode)
                && Objects.equals(uid, operatorUid) && adminRoleMapper.countEnabledAdmins() <= 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        int updated = adminRoleMapper.updateAdminStatus(uid, roleCode, enabled, cleanRemark(request == null ? null : request.remark()), operatorUid);
        if (updated == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        adminAuditService.record(operatorUid, "ADMIN_ROLE_STATUS", "ADMIN_ROLE", uid + ":" + roleCode, null,
                Map.of("uid", uid, "roleCode", roleCode, "enabled", enabled == 1), request == null ? null : request.remark());
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
                                                      @RequestParam(defaultValue = "10") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        return Result.ok(searchAnalyticsService.summary(days, limit));
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
    public Result<Map<String, Object>> replaySearchIndexRetryTask(@PathVariable Long id) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_OPS);
        SearchIndexRetryTaskPO task = searchIndexRetryService.findById(id);
        if (task == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (task.getTaskStatus() == null || task.getTaskStatus() != SearchIndexRetryTaskMapper.STATUS_FAILED) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        boolean replayed = searchIndexRetryService.replayFailed(id);
        adminAuditService.record(uid, "SEARCH_INDEX_RETRY_REPLAY", "SEARCH_INDEX_RETRY_TASK", id,
                task, Map.of("replayed", replayed), null);
        return Result.ok(Map.of("id", id, "replayed", replayed));
    }

    @PostMapping("/search-index-retry-tasks/replay-batch")
    public Result<Map<String, Object>> replaySearchIndexRetryTasks(@RequestBody SearchIndexRetryBatchRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_OPS);
        List<Long> ids = request == null || request.ids() == null ? List.of() : request.ids().stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(100)
                .toList();
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int replayed = searchIndexRetryService.replayFailedBatch(ids);
        adminAuditService.record(uid, "SEARCH_INDEX_RETRY_REPLAY_BATCH", "SEARCH_INDEX_RETRY_TASK", null,
                ids, Map.of("replayed", replayed), null);
        return Result.ok(Map.of("requested", ids.size(), "replayed", replayed));
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
        ModerationKeyword keyword = moderationAdminService.saveKeyword(null, cmd, uid);
        adminAuditService.record(uid, "MODERATION_KEYWORD_CREATE", "MODERATION_KEYWORD", keyword.getId(), null, keyword, null);
        return Result.ok(keyword);
    }

    @PostMapping("/moderation/keywords/{id}")
    public Result<ModerationKeyword> updateModerationKeyword(@PathVariable Long id,
                                                             @RequestBody ModerationKeywordAdminCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        ModerationKeyword keyword = moderationAdminService.saveKeyword(id, cmd, uid);
        adminAuditService.record(uid, "MODERATION_KEYWORD_UPDATE", "MODERATION_KEYWORD", id, null, keyword, null);
        return Result.ok(keyword);
    }

    @PostMapping("/moderation/keywords/{id}/status")
    public Result<Map<String, Object>> updateModerationKeywordStatus(@PathVariable Long id,
                                                                     @RequestParam int enabled) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        Map<String, Object> result = moderationAdminService.updateKeywordStatus(id, enabled, uid);
        adminAuditService.record(uid, "MODERATION_KEYWORD_STATUS", "MODERATION_KEYWORD", id, null, result, null);
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
        UserModerationState state = moderationAdminService.saveUserState(cmd, uid);
        enrichUserBrief(state);
        adminAuditService.record(uid, "USER_MODERATION_STATE", "USER", state.getUid(), null, state, cmd == null ? null : cmd.getReason());
        return Result.ok(state);
    }

    @PostMapping("/moderation/users/{targetUid}/clear-mute")
    public Result<UserModerationState> clearModerationUserMute(@PathVariable Long targetUid) {
        Long operatorUid = UserContext.require();
        adminPermissionService.requireScope(operatorUid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        UserModerationState state = moderationAdminService.clearUserMute(targetUid, operatorUid);
        enrichUserBrief(state);
        adminAuditService.record(operatorUid, "USER_MODERATION_CLEAR_MUTE", "USER", targetUid, null, state, "解除禁言");
        return Result.ok(state);
    }

    @PostMapping("/moderation/users/{targetUid}/clear-ban")
    public Result<UserModerationState> clearModerationUserBan(@PathVariable Long targetUid) {
        Long operatorUid = UserContext.require();
        adminPermissionService.requireScope(operatorUid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        UserModerationState state = moderationAdminService.clearUserBan(targetUid, operatorUid);
        enrichUserBrief(state);
        adminAuditService.record(operatorUid, "USER_MODERATION_CLEAR_BAN", "USER", targetUid, null, state, "解除封禁");
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

    private static String statusName(Object status) {
        int value = status instanceof Number number ? number.intValue() : -1;
        return switch (value) {
            case 0 -> "pending";
            case 1 -> "sent";
            case 2 -> "failed";
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

    private static Long requireTargetUid(AdminRequest request) {
        if (request == null || request.uid() == null || request.uid() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return request.uid();
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

    public record AdminRequest(Long uid, String roleCode, String remark) {
    }

    public record AdminStatusRequest(Boolean enabled, String roleCode, String remark) {
    }

    public record OutboxRetryBatchRequest(List<Long> ids) {
    }

    public record SearchIndexRetryBatchRequest(List<Long> ids) {
    }
}
