package com.offerlab.community.search.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditLog;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.moderation.ModerationAdminService;
import com.offerlab.community.infra.moderation.ModerationKeyword;
import com.offerlab.community.infra.moderation.ModerationKeywordAdminCmd;
import com.offerlab.community.infra.moderation.UserModerationState;
import com.offerlab.community.infra.moderation.UserModerationStateCmd;
import com.offerlab.community.infra.mq.outbox.OutboxMessage;
import com.offerlab.community.infra.mq.outbox.OutboxMessageMapper;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.AdminRoleMapper;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.search.application.PostSearchIndexer;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/ops")
@RequiredArgsConstructor
public class OpsController {

    private final PostSearchIndexer indexer;
    private final OutboxMessageMapper outboxMessageMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;
    private final ModerationAdminService moderationAdminService;
    private final MigrationCheckService migrationCheckService;

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

    @GetMapping("/migration/status")
    public Result<Map<String, Object>> migrationStatus() {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        return Result.ok(migrationCheckService.governanceStatus());
    }

    @GetMapping("/moderation/keywords")
    public Result<List<ModerationKeyword>> listModerationKeywords(@RequestParam(required = false) String keyword,
                                                                  @RequestParam(required = false) String scope,
                                                                  @RequestParam(defaultValue = "50") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(moderationAdminService.listKeywords(keyword, scope, clamp(limit)));
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
        return Result.ok(moderationAdminService.listUserStates(clamp(limit)));
    }

    @PostMapping("/moderation/users")
    public Result<UserModerationState> saveModerationUser(@RequestBody UserModerationStateCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        UserModerationState state = moderationAdminService.saveUserState(cmd, uid);
        adminAuditService.record(uid, "USER_MODERATION_STATE", "USER", state.getUid(), null, state, cmd == null ? null : cmd.getReason());
        return Result.ok(state);
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
}
