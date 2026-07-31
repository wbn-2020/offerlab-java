package com.offerlab.community.incentive.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.incentive.api.IncentiveDtos.AppealReviewCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.IncentiveAppealCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.IncentiveAppealDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RiskFindingDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RiskFindingActionCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.RiskScanCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.RiskScanResultDTO;
import com.offerlab.community.incentive.domain.IncentiveTypes;
import com.offerlab.community.incentive.infrastructure.IncentiveMapper;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.FreezePO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.IncentiveAppealPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.LedgerPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RiskFindingPO;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class IncentiveGovernanceService {
    private static final Set<String> APPEAL_TARGET_TYPES = Set.of("LEDGER", "REVERSAL", "FREEZE");
    private static final Set<String> RISK_SCAN_TYPES = Set.of(
            "HOURLY_REWARD_SPIKE", "DUPLICATE_REFERENCE", "TOP_USER_CONCENTRATION",
            "DOMAIN_REWARD_CONTRIBUTION_RATIO", "RECIPROCITY");

    private final IncentiveMapper mapper;
    private final AccountLedgerService accountLedgerService;
    private final SnowflakeIdGenerator idGenerator;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public IncentiveAppealDTO submitAppeal(IncentiveAppealCmd cmd, Long userId) {
        requireUser(userId);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String targetType = IncentiveTypes.upper(cmd.getTargetType());
        if (!APPEAL_TARGET_TYPES.contains(targetType)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "unsupported incentive appeal target");
        }
        Long targetId = cmd.getTargetId();
        Long relatedLedgerId;
        Long relatedRecoveryDebtId = null;
        Long originalOperatorUid;
        if ("FREEZE".equals(targetType)) {
            FreezePO freeze = mapper.lockFreeze(targetId);
            if (freeze == null || !userId.equals(freeze.getUserId())) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
            if (!"ACTIVE".equals(freeze.getFreezeStatus())) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "freeze is no longer appealable");
            }
            relatedLedgerId = freeze.getFreezeEntryId();
            originalOperatorUid = freeze.getOperatorUid();
        } else {
            LedgerPO ledger = mapper.selectLedgerById(targetId);
            if (ledger == null || !userId.equals(ledger.getUserId())) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
            if ("REVERSAL".equals(targetType) && !"REVERSAL".equals(ledger.getEntryType())) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "target is not a reversal entry");
            }
            if ("LEDGER".equals(targetType) && !"GOVERNANCE_DEBIT".equals(ledger.getEntryType())) {
                throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                        "ordinary spending is not appealable; use a freeze, reversal or governance debit");
            }
            var recoveryDebt = "REVERSAL".equals(targetType) && ledger.getReversedEntryId() != null
                    ? mapper.selectRecoveryDebtByOriginal(ledger.getReversedEntryId()) : null;
            boolean hasRecoveryDebt = recoveryDebt != null;
            relatedRecoveryDebtId = recoveryDebt == null ? null : recoveryDebt.getId();
            if (!hasRecoveryDebt
                    && ledger.getDeltaTotal() >= 0
                    && ledger.getDeltaAvailable() >= 0
                    && ledger.getDeltaFrozen() >= 0) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "ledger entry has no appealable debit");
            }
            relatedLedgerId = ledger.getId();
            originalOperatorUid = ledger.getOperatorUid();
        }
        IncentiveAppealPO po = new IncentiveAppealPO();
        po.setId(idGenerator.nextId());
        po.setAppellantUid(userId);
        po.setTargetType(targetType);
        po.setTargetId(targetId);
        po.setRelatedLedgerId(relatedLedgerId);
        po.setRelatedRecoveryDebtId(relatedRecoveryDebtId);
        po.setOriginalOperatorUid(originalOperatorUid);
        po.setAppealReason(IncentiveTypes.requireText(cmd.getReason(), 1000, "reason"));
        po.setAppealStatus("SUBMITTED");
        try {
            mapper.insertIncentiveAppeal(po);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                    "an appeal already exists for this incentive action");
        }
        return toAppealDto(mapper.selectIncentiveAppeal(po.getId()));
    }

    public PageResult<IncentiveAppealDTO> userAppeals(Long userId, Integer page, Integer size) {
        requireUser(userId);
        int safePage = safePage(page);
        int safeSize = IncentiveTypes.safeLimit(size);
        List<IncentiveAppealDTO> items = mapper.selectUserIncentiveAppeals(
                userId, (safePage - 1) * safeSize, safeSize).stream().map(this::toAppealDto).toList();
        return page(items, mapper.countUserIncentiveAppeals(userId), safePage, safeSize);
    }

    public PageResult<IncentiveAppealDTO> appealQueue(String status, Integer page, Integer size, Long operatorUid) {
        requireAdmin(operatorUid);
        String normalized = normalizeAppealStatus(status, true);
        int safePage = safePage(page);
        int safeSize = IncentiveTypes.safeLimit(size);
        List<IncentiveAppealDTO> items = mapper.selectIncentiveAppealQueue(
                normalized, (safePage - 1) * safeSize, safeSize).stream().map(this::toAppealDto).toList();
        return page(items, mapper.countIncentiveAppealQueue(normalized), safePage, safeSize);
    }

    @Transactional
    public IncentiveAppealDTO reviewAppeal(Long appealId, AppealReviewCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        if (cmd == null || cmd.getApproved() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        IncentiveAppealPO appeal = mapper.lockIncentiveAppeal(appealId);
        if (appeal == null || !"SUBMITTED".equals(appeal.getAppealStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        if (appeal.getOriginalOperatorUid() != null && appeal.getOriginalOperatorUid().equals(operatorUid)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "the original operator cannot review this appeal");
        }
        if (operatorUid.equals(appeal.getAppellantUid())) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "the appellant cannot review their own appeal");
        }
        String reason = IncentiveTypes.requireReason(cmd.getReason());
        Long restoreEntryId = null;
        String status = Boolean.TRUE.equals(cmd.getApproved()) ? "APPROVED" : "REJECTED";
        if ("APPROVED".equals(status)) {
            restoreEntryId = accountLedgerService.restoreAppeal(
                    appeal.getTargetType(), appeal.getTargetId(), appeal.getId(), reason, operatorUid).getId();
        }
        if (mapper.reviewIncentiveAppeal(appealId, status, operatorUid, reason, restoreEntryId) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        IncentiveAppealPO after = mapper.selectIncentiveAppeal(appealId);
        adminAuditService.recordRequired(operatorUid, "INCENTIVE_APPEAL_REVIEW",
                "INCENTIVE_APPEAL", appealId, appeal, after, reason);
        return toAppealDto(after);
    }

    @Transactional
    public RiskScanResultDTO scanRisk(RiskScanCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String scanType = IncentiveTypes.upper(cmd.getScanType());
        if (!RISK_SCAN_TYPES.contains(scanType)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "unsupported risk scan type");
        }
        int limit = Math.min(cmd.getLimit() == null ? 100 : Math.max(1, cmd.getLimit()), 500);
        String reason = IncentiveTypes.requireReason(cmd.getReason());
        mapper.insertRiskScanCursorIfAbsent(scanType);
        Map<String, Object> cursor = mapper.lockRiskScanCursor(scanType);
        long cursorStart = number(cursor.get("cursorValue"));
        long cycleNo = Math.max(1, number(cursor.get("cycleNo")));
        long runId = idGenerator.nextId();
        mapper.insertRiskScanRun(runId, scanType, cycleNo, cursorStart, operatorUid, reason);

        List<Map<String, Object>> rows = riskRows(scanType, cursorStart, Math.min(limit + 1, 501));
        boolean truncated = rows.size() > limit;
        if (truncated) {
            rows = rows.subList(0, limit);
        }
        int findings = createFindings(scanType, cycleNo, runId, rows);
        long cursorEnd = cursorStart;
        if (!rows.isEmpty()) {
            Map<String, Object> last = rows.get(rows.size() - 1);
            cursorEnd = number(last.getOrDefault("subjectId", last.get("cursorId")));
        }
        boolean cursorScan = Set.of("HOURLY_REWARD_SPIKE", "DUPLICATE_REFERENCE").contains(scanType);
        boolean coverageComplete = !truncated && (!cursorScan || rows.size() < limit);
        long nextCursor = coverageComplete ? 0 : cursorEnd;
        mapper.finishRiskScanRun(runId, cursorEnd, nextCursor, coverageComplete ? 1 : 0,
                rows.size(), findings);
        mapper.advanceRiskScanCursor(scanType, nextCursor, coverageComplete ? 1 : 0);
        RiskScanResultDTO result = RiskScanResultDTO.builder()
                .runId(runId).scanType(scanType).cycleNo(cycleNo)
                .cursorStart(cursorStart).cursorEnd(cursorEnd).nextCursor(nextCursor)
                .coverageComplete(coverageComplete).scannedCount(rows.size()).findingCount(findings)
                .build();
        adminAuditService.recordRequired(operatorUid, "INCENTIVE_RISK_SCAN",
                "INCENTIVE_RISK_SCAN", runId, null, result, reason);
        return result;
    }

    public PageResult<RiskFindingDTO> findings(String status, Integer page, Integer size, Long operatorUid) {
        requireAdmin(operatorUid);
        String normalized = normalizeFindingStatus(status, true);
        int safePage = safePage(page);
        int safeSize = IncentiveTypes.safeLimit(size);
        List<RiskFindingDTO> items = mapper.selectRiskFindings(
                normalized, (safePage - 1) * safeSize, safeSize).stream().map(this::toFindingDto).toList();
        return page(items, mapper.countRiskFindings(normalized), safePage, safeSize);
    }

    @Transactional
    public RiskFindingDTO resolveFinding(Long findingId, RiskFindingActionCmd cmd,
                                         String targetStatus, Long operatorUid) {
        requireAdmin(operatorUid);
        String normalized = IncentiveTypes.upper(targetStatus);
        if (!Set.of("RESOLVED", "IGNORED").contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String reason = IncentiveTypes.requireReason(cmd == null ? null : cmd.getReason());
        RiskFindingPO before = mapper.lockRiskFinding(findingId);
        if (before == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (mapper.resolveRiskFinding(findingId, normalized, operatorUid, reason) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        RiskFindingPO after = mapper.lockRiskFinding(findingId);
        adminAuditService.recordRequired(operatorUid, "INCENTIVE_RISK_FINDING_" + normalized,
                "INCENTIVE_RISK_FINDING", findingId, before, after, reason);
        return toFindingDto(after);
    }

    private List<Map<String, Object>> riskRows(String scanType, long cursor, int limit) {
        return switch (scanType) {
            case "HOURLY_REWARD_SPIKE" -> mapper.selectHourlyRewardRiskCandidates(cursor, limit);
            case "DUPLICATE_REFERENCE" -> mapper.selectDuplicateReferenceRiskCandidates(cursor, limit);
            case "TOP_USER_CONCENTRATION" -> {
                Map<String, Object> snapshot = mapper.selectTopUserConcentrationSnapshot();
                yield snapshot == null ? List.of() : List.of(snapshot);
            }
            case "DOMAIN_REWARD_CONTRIBUTION_RATIO" ->
                    mapper.selectDomainRewardContributionSnapshots(limit);
            case "RECIPROCITY" -> List.of(Map.of(
                    "subjectId", 0L,
                    "explanation", "Current trusted reward events do not retain both sides of a social relationship."));
            default -> List.of();
        };
    }

    private int createFindings(String scanType, long cycleNo, long runId, List<Map<String, Object>> rows) {
        int findings = 0;
        for (Map<String, Object> row : rows) {
            long metric;
            long threshold;
            boolean hit;
            String subjectType;
            String subjectId;
            String domain = string(row.get("domainCode"));
            String evaluation = "HIT";
            String severity = "MEDIUM";
            switch (scanType) {
                case "HOURLY_REWARD_SPIKE" -> {
                    metric = number(row.get("metricValue"));
                    threshold = 500;
                    hit = metric >= threshold || number(row.get("sampleCount")) >= 20;
                    subjectType = "USER";
                    subjectId = string(row.get("subjectId"));
                }
                case "DUPLICATE_REFERENCE" -> {
                    metric = number(row.get("metricValue"));
                    threshold = 2;
                    hit = metric >= threshold;
                    subjectType = "REFERENCE";
                    subjectId = string(row.get("referenceType")) + ":" + sha256(
                            string(row.get("referenceId")) + "|" + string(row.get("recipientUid"))
                                    + "|" + string(row.get("ruleCode"))
                                    + "|" + string(row.get("ruleVersion")));
                }
                case "TOP_USER_CONCENTRATION" -> {
                    long total = number(row.get("totalAwarded"));
                    long top = number(row.get("topAwarded"));
                    metric = total <= 0 ? 0 : top * 10_000 / total;
                    threshold = 4_000;
                    hit = total >= 100 && metric >= threshold;
                    subjectType = "USER";
                    subjectId = string(row.get("topUserId"));
                }
                case "DOMAIN_REWARD_CONTRIBUTION_RATIO" -> {
                    long contributions = number(row.get("contributionCount"));
                    metric = contributions <= 0 ? 0 : number(row.get("metricValue")) / contributions;
                    threshold = 100;
                    hit = contributions > 0 && metric > threshold;
                    subjectType = "DOMAIN";
                    subjectId = domain;
                }
                case "RECIPROCITY" -> {
                    metric = 0;
                    threshold = 0;
                    hit = true;
                    subjectType = "SYSTEM";
                    subjectId = "RECIPROCITY_DATA_GAP";
                    evaluation = "NOT_EVALUATED";
                    severity = "INFO";
                }
                default -> throw new IllegalStateException(scanType);
            }
            if (!hit) {
                continue;
            }
            RiskFindingPO po = new RiskFindingPO();
            po.setId(idGenerator.nextId());
            po.setStableKey(findingStableKey(scanType, cycleNo, subjectId));
            po.setFindingType(scanType);
            po.setSubjectType(subjectType);
            po.setSubjectId(subjectId);
            po.setDomainCode(domain);
            po.setSeverity(severity);
            po.setEvaluationStatus(evaluation);
            po.setFindingStatus("NOT_EVALUATED".equals(evaluation) ? "NOT_EVALUATED" : "OPEN");
            po.setMetricValue(metric);
            po.setThresholdValue(threshold);
            po.setEvidenceJson(json(row));
            po.setScanRunId(runId);
            findings += mapper.insertRiskFinding(po);
        }
        return findings;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("risk evidence serialization failed", e);
        }
    }

    private IncentiveAppealDTO toAppealDto(IncentiveAppealPO po) {
        return IncentiveAppealDTO.builder()
                .id(po.getId()).appellantUid(po.getAppellantUid()).targetType(po.getTargetType())
                .targetId(po.getTargetId()).relatedLedgerId(po.getRelatedLedgerId())
                .relatedRecoveryDebtId(po.getRelatedRecoveryDebtId())
                .status(po.getAppealStatus()).appealReason(po.getAppealReason())
                .reviewerUid(po.getReviewerUid()).reviewReason(po.getReviewReason())
                .restoreEntryId(po.getRestoreEntryId()).createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime()).build();
    }

    private RiskFindingDTO toFindingDto(RiskFindingPO po) {
        return RiskFindingDTO.builder()
                .id(po.getId()).findingType(po.getFindingType()).subjectType(po.getSubjectType())
                .subjectId(po.getSubjectId()).domainCode(po.getDomainCode()).severity(po.getSeverity())
                .evaluationStatus(po.getEvaluationStatus()).findingStatus(po.getFindingStatus())
                .metricValue(po.getMetricValue()).thresholdValue(po.getThresholdValue())
                .evidenceJson(po.getEvidenceJson()).scanRunId(po.getScanRunId())
                .resolvedBy(po.getResolvedBy()).resolutionReason(po.getResolutionReason())
                .createTime(po.getCreateTime()).updateTime(po.getUpdateTime()).build();
    }

    private void requireAdmin(Long uid) {
        adminPermissionService.requireAdmin(uid);
    }

    private static void requireUser(Long uid) {
        if (uid == null || uid <= 0) throw new BizException(ErrorCode.UNAUTHORIZED);
    }

    private static String normalizeAppealStatus(String status, boolean nullable) {
        if (status == null && nullable) return null;
        String normalized = IncentiveTypes.upper(status);
        if (!Set.of("SUBMITTED", "APPROVED", "REJECTED").contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static int safePage(Integer page) {
        return page == null || page <= 0 ? 1 : Math.min(page, 1000);
    }

    private static String normalizeFindingStatus(String status, boolean nullable) {
        if (status == null && nullable) return null;
        String normalized = IncentiveTypes.upper(status);
        if (!Set.of("OPEN", "NOT_EVALUATED", "RESOLVED", "IGNORED").contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String findingStableKey(String scanType, long cycleNo, String subjectId) {
        String raw = scanType + ":" + cycleNo + ":" + subjectId;
        if (raw.length() <= 160) {
            return raw;
        }
        try {
            return scanType + ":" + cycleNo + ":" + sha256(raw);
        } catch (RuntimeException e) {
            throw e;
        }
    }

    private static String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static <T> PageResult<T> page(List<T> items, long total, int page, int size) {
        boolean hasMore = (long) page * size < total;
        return PageResult.<T>builder().items(items).total(total).hasMore(hasMore)
                .nextCursor(hasMore ? String.valueOf(page + 1) : null).build();
    }
}
