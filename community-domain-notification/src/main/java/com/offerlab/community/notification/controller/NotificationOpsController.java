package com.offerlab.community.notification.controller;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.notification.application.NotificationRetryService;
import com.offerlab.community.notification.infrastructure.persistence.mapper.NotificationRetryTaskMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationRetryTaskPO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ops")
@RequiredArgsConstructor
public class NotificationOpsController {

    private final NotificationRetryService retryService;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;

    @GetMapping("/notification-retry-tasks/status")
    public Result<Map<String, Object>> retryStatus() {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        return Result.ok(retryService.status());
    }

    @GetMapping("/notification-retry-tasks")
    public Result<List<NotificationRetryTaskPO>> listRetryTasks(@RequestParam(required = false) Integer status,
                                                                @RequestParam(defaultValue = "20") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        return Result.ok(retryService.listRecent(normalizeStatus(status), limit));
    }

    @GetMapping("/notification-retry-tasks/{id}")
    public Result<NotificationRetryTaskPO> getRetryTask(@PathVariable Long id) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        NotificationRetryTaskPO task = retryService.findById(id);
        if (task == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return Result.ok(task);
    }

    @PostMapping("/notification-retry-tasks/{id}/replay")
    public Result<Map<String, Object>> replayRetryTask(@PathVariable Long id) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_OPS);
        NotificationRetryTaskPO task = retryService.findById(id);
        if (task == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (task.getTaskStatus() == null || task.getTaskStatus() != NotificationRetryTaskMapper.STATUS_FAILED) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        boolean replayed = retryService.replayFailed(id);
        adminAuditService.record(uid, "NOTIF_RETRY_REPLAY", "NOTIF_RETRY_TASK", id,
                task, Map.of("replayed", replayed), null);
        return Result.ok(Map.of("id", id, "replayed", replayed));
    }

    @PostMapping("/notification-retry-tasks/replay-batch")
    public Result<Map<String, Object>> replayRetryTasks(@RequestBody RetryTaskBatchRequest request) {
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
        int replayed = retryService.replayFailedBatch(ids);
        adminAuditService.record(uid, "NOTIF_RETRY_REPLAY_BATCH", "NOTIF_RETRY_TASK", null,
                ids, Map.of("replayed", replayed), null);
        return Result.ok(Map.of("requested", ids.size(), "replayed", replayed));
    }

    private static Integer normalizeStatus(Integer status) {
        if (status == null) {
            return null;
        }
        if (status == NotificationRetryTaskMapper.STATUS_PENDING
                || status == NotificationRetryTaskMapper.STATUS_DONE
                || status == NotificationRetryTaskMapper.STATUS_FAILED
                || status == NotificationRetryTaskMapper.STATUS_RUNNING) {
            return status;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    public record RetryTaskBatchRequest(List<Long> ids) {
    }
}
