package com.offerlab.community.notification.controller;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.common.utils.RiskConfirmation;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.ops.AdminOperationIdempotencyService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.notification.application.NotificationRetryService;
import com.offerlab.community.notification.infrastructure.persistence.mapper.NotificationRetryTaskMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationRetryTaskPO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ops")
@RequiredArgsConstructor
@Validated
public class NotificationOpsController {
    private static final int MAX_RETRY_BATCH_SIZE = 50;
    private static final int PREVIEW_EXPIRES_IN_SECONDS = 300;

    private final NotificationRetryService retryService;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;
    private final AdminOperationIdempotencyService idempotencyService;

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

    @GetMapping("/notification-retry-tasks/page")
    public Result<PageResult<NotificationRetryTaskPO>> pageRetryTasks(@RequestParam(required = false) Integer status,
                                                                      @RequestParam(defaultValue = "1") int page,
                                                                      @RequestParam(defaultValue = "20") int pageSize) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        return Result.ok(retryService.pageRecent(normalizeStatus(status), page, pageSize));
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
    public Result<Map<String, Object>> replayRetryTask(@PathVariable Long id,
                                                       @Valid @RequestBody(required = false) ActionRemarkRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_OPS);
        NotificationRetryTaskPO task = retryService.findById(id);
        if (task == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (task.getTaskStatus() == null || task.getTaskStatus() != NotificationRetryTaskMapper.STATUS_FAILED) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        String remark = RiskConfirmation.requireHigh(actionRemark(request));
        adminAuditService.requireWritable("NOTIF_RETRY_REPLAY", "NOTIF_RETRY_TASK", id);
        boolean replayed = retryService.replayFailed(id);
        adminAuditService.recordRequired(uid, "NOTIF_RETRY_REPLAY", "NOTIF_RETRY_TASK", id,
                task, Map.of("replayed", replayed), remark);
        return Result.ok(Map.of("id", id, "replayed", replayed));
    }

    @PostMapping("/notification-retry-tasks/replay-batch")
    public Result<Map<String, Object>> replayRetryTasks(@Valid @RequestBody RetryTaskBatchRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_OPS);
        List<Long> ids = request.ids().stream()
                .distinct()
                .toList();
        String remark = RiskConfirmation.requireCritical(request.remark(), request.confirmationPhrase());
        String idempotencyKey = idempotencyService.requireKey(request.idempotencyKey());
        idempotencyService.requirePreview(uid, "NOTIF_RETRY_REPLAY_BATCH", ids, request.previewNonce());
        adminAuditService.requireWritable("NOTIF_RETRY_REPLAY_BATCH", "NOTIF_RETRY_TASK", null);
        idempotencyService.requireFresh(uid, "NOTIF_RETRY_REPLAY_BATCH", ids, idempotencyKey);
        int replayed = retryService.replayFailedBatch(ids);
        adminAuditService.recordRequired(uid, "NOTIF_RETRY_REPLAY_BATCH", "NOTIF_RETRY_TASK", null,
                ids, Map.of("replayed", replayed, "idempotencyKey", idempotencyKey), remark);
        return Result.ok(Map.of("requested", ids.size(), "replayed", replayed, "idempotencyKey", idempotencyKey));
    }

    @PostMapping("/notification-retry-tasks/replay-batch/preview")
    public Result<Map<String, Object>> previewReplayRetryTasks(@Valid @RequestBody RetryTaskBatchRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_OPS);
        List<Long> ids = request.ids().stream()
                .distinct()
                .toList();
        return Result.ok(previewRetryBatch(uid, ids));
    }

    private Map<String, Object> previewRetryBatch(Long uid, List<Long> ids) {
        List<Map<String, Object>> items = ids.stream()
                .map(id -> {
                    NotificationRetryTaskPO task = retryService.findById(id);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", id);
                    if (task == null) {
                        item.put("eligible", false);
                        item.put("reason", "NOT_FOUND");
                        item.put("reasonText", "补偿任务不存在");
                        return item;
                    }
                    boolean eligible = task.getTaskStatus() != null && task.getTaskStatus() == NotificationRetryTaskMapper.STATUS_FAILED;
                    item.put("eligible", eligible);
                    item.put("reason", eligible ? "READY" : "STATUS_NOT_FAILED");
                    item.put("reasonText", eligible ? "失败任务，可重放" : "当前状态不是失败，不会被重放");
                    item.put("status", task.getTaskStatus());
                    item.put("statusText", statusName(task.getTaskStatus()));
                    item.put("objectLabel", "receiver:" + task.getReceiverUid());
                    item.put("targetId", task.getTargetId());
                    item.put("retryCount", task.getRetryCount());
                    return item;
                })
                .toList();
        long eligible = items.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("eligible")))
                .count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operation", "NOTIF_RETRY_REPLAY_BATCH");
        result.put("previewNonce", idempotencyService.issuePreview(uid, "NOTIF_RETRY_REPLAY_BATCH", ids));
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

    private static String statusName(Object status) {
        int value = status instanceof Number number ? number.intValue() : -1;
        return switch (value) {
            case NotificationRetryTaskMapper.STATUS_PENDING -> "pending";
            case NotificationRetryTaskMapper.STATUS_DONE -> "done";
            case NotificationRetryTaskMapper.STATUS_FAILED -> "failed";
            case NotificationRetryTaskMapper.STATUS_RUNNING -> "running";
            default -> "unknown";
        };
    }

    public record RetryTaskBatchRequest(
            @NotEmpty @Size(max = MAX_RETRY_BATCH_SIZE) List<@NotNull @Positive Long> ids,
            @Size(max = 500) String remark,
            @Size(max = 32) String confirmationPhrase,
            @Size(max = 80) String idempotencyKey,
            @Size(max = 80) String previewNonce) {
    }

    public record ActionRemarkRequest(@Size(max = 500) String remark,
                                      @Size(max = 500) String reason) {
    }

    private static String actionRemark(ActionRemarkRequest request) {
        if (request == null) {
            return null;
        }
        String remark = RiskConfirmation.cleanRemark(request.remark());
        return remark == null ? RiskConfirmation.cleanRemark(request.reason()) : remark;
    }
}
