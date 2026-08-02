package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.incentive.api.quota.BenefitCodes;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedExceptionDTO;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedReconcileCmd;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedReconcileResultDTO;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentAssistEnhancedOperationsMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentAssistEnhancedRequestMapper;
import com.offerlab.community.post.infrastructure.persistence.po.ContentAssistEnhancedExceptionRow;
import com.offerlab.community.post.infrastructure.persistence.po.ContentAssistEnhancedReconcileRequestPO;
import com.offerlab.community.post.infrastructure.persistence.po.ContentAssistEnhancedRequestPO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ContentAssistEnhancedOperationsService {
    private static final String AUDIT_ACTION = "AI_ASSIST_ENHANCED_RECONCILE";
    private static final String AUDIT_RESOURCE = "AI_ASSIST_ENHANCED";

    private final ContentAssistEnhancedOperationsMapper operationsMapper;
    private final ContentAssistEnhancedRequestMapper requestMapper;
    private final ContentAssistEnhancedService enhancedService;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;
    private final SnowflakeIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    @Value("${offerlab.ai.content-assist.enhanced-request-timeout-seconds:120}")
    private long requestTimeoutSeconds;

    @Value("${offerlab.ai.content-assist.enhanced-ops-reconcile-enabled:false}")
    private boolean reconcileEnabled;

    public List<ContentAssistEnhancedExceptionDTO> exceptions(Long operatorUid, int requestedLimit) {
        requireOperations(operatorUid);
        int limit = safeLimit(requestedLimit);
        long timeout = enhancedService.effectiveRecoveryTimeoutSeconds(requestTimeoutSeconds);
        return operationsMapper.selectExceptions(timeout, limit).stream()
                .map(this::toException)
                .toList();
    }

    @Transactional
    public ContentAssistEnhancedReconcileResultDTO reconcile(
            ContentAssistEnhancedReconcileCmd cmd,
            Long operatorUid) {
        requireOperations(operatorUid);
        if (!reconcileEnabled) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "AI_ASSIST_OPS_RECONCILE_DISABLED");
        }
        ReconcileCommand request = requireCommand(cmd);
        String fingerprint = fingerprint(request);
        ContentAssistEnhancedReconcileRequestPO existing = operationsMapper.selectByOperatorAndKey(
                operatorUid, request.idempotencyKey());
        if (existing != null) {
            return replay(existing, operatorUid, fingerprint);
        }

        String resourceId = operatorUid + ":" + ContentAssistSafety.sha256Hex(request.idempotencyKey()).substring(0, 24);
        adminAuditService.requireWritable(AUDIT_ACTION, AUDIT_RESOURCE, resourceId);
        ContentAssistEnhancedReconcileRequestPO reservation = new ContentAssistEnhancedReconcileRequestPO();
        reservation.setId(idGenerator.nextId());
        reservation.setOperatorUid(operatorUid);
        reservation.setIdempotencyKey(request.idempotencyKey());
        reservation.setRequestFingerprint(fingerprint);
        try {
            operationsMapper.insertRunning(reservation);
        } catch (DuplicateKeyException ex) {
            return replay(requireReservedReplay(operatorUid, request.idempotencyKey()), operatorUid, fingerprint);
        }

        ContentAssistEnhancedReconcileResultDTO result = performReconcile(request);
        adminAuditService.recordRequired(
                operatorUid,
                AUDIT_ACTION,
                AUDIT_RESOURCE,
                resourceId,
                Map.of(
                        "dryRun", request.dryRun(),
                        "limit", request.limit(),
                        "requestFingerprint", fingerprint
                ),
                Map.of(
                        "scanned", result.getScanned(),
                        "eligible", result.getEligible(),
                        "recovered", result.getRecovered(),
                        "skipped", result.getSkipped(),
                        "issueTypes", result.getIssues().stream().map(ContentAssistEnhancedExceptionDTO::getIssueType).distinct().toList()
                ),
                request.reason());
        if (operationsMapper.complete(reservation.getId(), serialize(result)) != 1) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "AI_ASSIST_RECONCILE_RESERVATION_UNAVAILABLE");
        }
        return result;
    }

    private ContentAssistEnhancedReconcileResultDTO performReconcile(ReconcileCommand command) {
        long timeout = enhancedService.effectiveRecoveryTimeoutSeconds(requestTimeoutSeconds);
        List<ContentAssistEnhancedExceptionRow> rows = operationsMapper.selectExceptions(timeout, command.limit());
        int eligible = 0;
        int recovered = 0;
        int skipped = 0;
        List<ContentAssistEnhancedExceptionDTO> issues = new ArrayList<>();
        for (ContentAssistEnhancedExceptionRow row : rows) {
            ContentAssistEnhancedExceptionDTO issue = toException(row);
            issues.add(issue);
            if (!Boolean.TRUE.equals(issue.getRecoverable())) {
                skipped++;
                continue;
            }
            ContentAssistEnhancedRequestPO request = requestMapper.selectByIdAndUid(
                    row.getRequestId(), row.getRequestUid(), BenefitCodes.CONTENT_ASSIST_ENHANCED);
            if (request == null || !Objects.equals(request.getUsageId(), row.getUsageId())) {
                skipped++;
                continue;
            }
            eligible++;
            if (command.dryRun()) {
                continue;
            }
            if (enhancedService.recoverStaleRequest(request, timeout)) {
                recovered++;
            } else {
                skipped++;
            }
        }
        return ContentAssistEnhancedReconcileResultDTO.builder()
                .dryRun(command.dryRun())
                .scanned(rows.size())
                .eligible(eligible)
                .recovered(recovered)
                .skipped(skipped)
                .replayed(false)
                .issues(List.copyOf(issues))
                .build();
    }

    private ContentAssistEnhancedReconcileResultDTO replay(
            ContentAssistEnhancedReconcileRequestPO record,
            Long operatorUid,
            String fingerprint) {
        if (record == null || !Objects.equals(record.getOperatorUid(), operatorUid)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(), "AI_ASSIST_RECONCILE_RESERVATION_UNAVAILABLE");
        }
        if (!Objects.equals(record.getRequestFingerprint(), fingerprint)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "AI_ASSIST_RECONCILE_IDEMPOTENCY_CONFLICT");
        }
        if (!"COMPLETED".equals(record.getRequestStatus()) || !StringUtils.hasText(record.getResultJson())) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "AI_ASSIST_RECONCILE_IN_PROGRESS");
        }
        try {
            ContentAssistEnhancedReconcileResultDTO result = objectMapper.readValue(
                    record.getResultJson(), ContentAssistEnhancedReconcileResultDTO.class);
            result.setReplayed(true);
            return result;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(), "AI_ASSIST_RECONCILE_RESULT_UNREADABLE");
        }
    }

    private ContentAssistEnhancedReconcileRequestPO requireReservedReplay(Long operatorUid, String idempotencyKey) {
        ContentAssistEnhancedReconcileRequestPO record = operationsMapper.lockByOperatorAndKey(operatorUid, idempotencyKey);
        if (record == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(), "AI_ASSIST_RECONCILE_RESERVATION_UNAVAILABLE");
        }
        return record;
    }

    private ContentAssistEnhancedExceptionDTO toException(ContentAssistEnhancedExceptionRow row) {
        String issueType = issueType(row.getRequestStatus(), row.getUsageStatus(), row.getRequestUid(),
                row.getUsageUid(), row.getRequestId(), row.getUsageSourceRef());
        return ContentAssistEnhancedExceptionDTO.builder()
                .requestId(row.getRequestId())
                .usageId(row.getUsageId())
                .issueType(issueType)
                .recoverable("STALE_RUNNING_RESERVED".equals(issueType))
                .requestStatus(row.getRequestStatus())
                .usageStatus(row.getUsageStatus())
                .fingerprintPrefix(prefix(row.getRequestFingerprint()))
                .errorCode(row.getErrorCode())
                .provider(row.getProvider())
                .promptTokens(row.getPromptTokens())
                .completionTokens(row.getCompletionTokens())
                .estimatedCostMicros(row.getEstimatedCostMicros())
                .ageSeconds(row.getAgeSeconds())
                .createTime(row.getCreateTime())
                .updateTime(row.getUpdateTime())
                .build();
    }

    private static String issueType(String requestStatus, String usageStatus, Long requestUid, Long usageUid,
                                    Long requestId, String usageSourceRef) {
        if (usageUid != null && !Objects.equals(requestUid, usageUid)) {
            return "USAGE_OWNER_MISMATCH";
        }
        if (usageSourceRef != null && requestId != null && !Objects.equals(usageSourceRef, String.valueOf(requestId))) {
            return "SOURCE_REFERENCE_MISMATCH";
        }
        if ("RUNNING".equals(requestStatus) && "RESERVED".equals(usageStatus)) {
            return "STALE_RUNNING_RESERVED";
        }
        if ("RUNNING".equals(requestStatus)) {
            return "RUNNING_USAGE_MISMATCH";
        }
        if ("SUCCEEDED".equals(requestStatus) && !"CONFIRMED".equals(usageStatus)) {
            return "TERMINAL_USAGE_MISMATCH";
        }
        if (("FALLBACK".equals(requestStatus) || "FAILED".equals(requestStatus))
                && usageStatus != null && !"RELEASED".equals(usageStatus)) {
            return "TERMINAL_USAGE_MISMATCH";
        }
        return "FULFILLMENT_MISMATCH";
    }

    private static ReconcileCommand requireCommand(ContentAssistEnhancedReconcileCmd command) {
        if (command == null || !StringUtils.hasText(command.getIdempotencyKey()) || !StringUtils.hasText(command.getReason())) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int limit = safeLimit(command.getLimit());
        String idempotencyKey = command.getIdempotencyKey().trim();
        String reason = command.getReason().trim();
        if (idempotencyKey.length() < 16 || idempotencyKey.length() > 96 || reason.length() > 500) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return new ReconcileCommand(command.isDryRun(), limit, idempotencyKey, reason);
    }

    private static int safeLimit(Integer requestedLimit) {
        int value = requestedLimit == null ? 20 : requestedLimit;
        return Math.max(1, Math.min(value, 100));
    }

    private static String fingerprint(ReconcileCommand command) {
        return ContentAssistSafety.sha256Hex(
                command.dryRun() + "|" + command.limit() + "|" + command.reason());
    }

    private static String prefix(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.substring(0, Math.min(12, value.length()));
    }

    private void requireOperations(Long operatorUid) {
        adminPermissionService.requireScope(operatorUid, AdminPermissionService.ROLE_OPS);
    }

    private String serialize(ContentAssistEnhancedReconcileResultDTO result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.DATABASE_ERROR.getCode(), "AI_ASSIST_RECONCILE_RESULT_UNREADABLE");
        }
    }

    private record ReconcileCommand(boolean dryRun, int limit, String idempotencyKey, String reason) {
    }
}
