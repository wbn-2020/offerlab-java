package com.offerlab.community.search.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.mq.outbox.OutboxMessage;
import com.offerlab.community.infra.mq.outbox.OutboxMessageMapper;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.AdminRoleMapper;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.search.application.PostSearchIndexer;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Long uid = UserContext.require();
        adminPermissionService.requireAdmin(uid);

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

    @GetMapping("/outbox")
    public Result<List<OutboxMessage>> listOutbox(@RequestParam(required = false) Integer status,
                                                  @RequestParam(defaultValue = "20") int limit) {
        adminPermissionService.requireAdmin(UserContext.require());
        return Result.ok(outboxMessageMapper.listRecent(status, clamp(limit)));
    }

    @GetMapping("/outbox/{id}")
    public Result<OutboxMessage> getOutbox(@PathVariable Long id) {
        adminPermissionService.requireAdmin(UserContext.require());
        OutboxMessage message = outboxMessageMapper.findById(id);
        if (message == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return Result.ok(message);
    }

    @PostMapping("/outbox/{id}/retry")
    public Result<Map<String, Object>> retryOutbox(@PathVariable Long id) {
        adminPermissionService.requireAdmin(UserContext.require());
        OutboxMessage message = outboxMessageMapper.findById(id);
        if (message == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (message.getMsgStatus() == null || message.getMsgStatus() != 2) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        int updated = outboxMessageMapper.markFailedForRetry(id);
        return Result.ok(Map.of("id", id, "retried", updated > 0));
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
        int updated = adminRoleMapper.upsertAdmin(targetUid, cleanRemark(request.remark()), operatorUid);
        return Result.ok(Map.of("uid", targetUid, "enabled", true, "updated", updated > 0));
    }

    @PostMapping("/admins/{uid}/status")
    public Result<Map<String, Object>> updateAdminStatus(@PathVariable Long uid,
                                                        @RequestBody AdminStatusRequest request) {
        Long operatorUid = UserContext.require();
        adminPermissionService.requireAdmin(operatorUid);
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int enabled = Boolean.TRUE.equals(request.enabled()) ? 1 : 0;
        if (enabled == 0 && Objects.equals(uid, operatorUid) && adminRoleMapper.countEnabledAdmins() <= 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        int updated = adminRoleMapper.updateAdminStatus(uid, enabled, cleanRemark(request.remark()), operatorUid);
        if (updated == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return Result.ok(Map.of("uid", uid, "enabled", enabled == 1, "updated", true));
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

    public record AdminRequest(Long uid, String remark) {
    }

    public record AdminStatusRequest(Boolean enabled, String remark) {
    }
}
