package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchCoordinationDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchCoordinationTaskDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchEventDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchEventPageDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchExtendDeadlineCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchReassignActiveTasksCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchRiskNoteCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchWithdrawOpenTasksCmd;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchCoordinationRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchCoordinationTaskRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchEventRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchMapper;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchTaskStatusRow;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.api.ContentMaintenanceBatchTaskCoordinationResult;
import com.offerlab.community.post.api.ContentMaintenanceTaskCommandFacade;
import com.offerlab.community.post.application.DomainModeratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChannelQualityReviewBatchCoordinationService {

    private static final String SOURCE_TYPE = "CHANNEL_HEALTH";
    private static final String MIGRATION =
            "db/migration/20260807_channel_quality_review_batch_coordination.sql";
    private static final int MAX_TASKS = 20;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 20;
    private static final Set<Integer> EXTEND_DAYS = Set.of(1, 3, 7, 14, 30);
    private static final Set<String> RISK_CODES =
            Set.of("BLOCKER", "CAPACITY_RISK", "REVIEW_DELAY", "OVERDUE_ESCALATION");
    private static final Set<String> WITHDRAW_REASON_CODES =
            Set.of("SCOPE_INVALID", "DUPLICATE_SCOPE", "PRIORITY_REPLACED", "OTHER");
    private static final List<String> TASK_STATUSES =
            List.of("OPEN", "CLAIMED", "SUBMITTED", "COMPLETED", "CLOSED");

    private final ChannelQualityReviewBatchMapper mapper;
    private final ContentMaintenanceTaskCommandFacade taskCommandFacade;
    private final SnowflakeIdGenerator idGenerator;
    private final AdminAuditService adminAuditService;
    private final AdminPermissionService adminPermissionService;
    private final DomainModeratorService domainModeratorService;
    private final Clock analyticsClock;

    @Transactional(readOnly = true)
    public ChannelQualityReviewBatchCoordinationDTO coordination(Long batchId, Long operatorUid) {
        requireTables();
        long safeBatchId = requireId(batchId);
        requireOperator(operatorUid);
        ChannelQualityReviewBatchCoordinationRow batch = requireBatch(mapper.selectCoordinationById(safeBatchId));
        requireModerate(operatorUid, batch.getDomain());
        return readCoordination(batch);
    }

    @Transactional
    public ChannelQualityReviewBatchCoordinationDTO extendDeadline(
            Long batchId,
            ChannelQualityReviewBatchExtendDeadlineCmd cmd,
            Long operatorUid) {
        requireTables();
        if (cmd == null) throw new BizException(ErrorCode.PARAM_ERROR);
        long safeBatchId = requireId(batchId);
        requireOperator(operatorUid);
        ChannelQualityReviewBatchCoordinationRow batch = requireBatch(mapper.lockCoordinationById(safeBatchId));
        requireModerate(operatorUid, batch.getDomain());
        requireExpectedVersion(cmd.getExpectedCoordinationVersion(), batch);
        int active = activeTaskCount(batch);
        if (active <= 0 || batch.getEffectiveDueAt() == null) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        int days = enumInt(cmd.getExtendByDays(), EXTEND_DAYS);
        String note = required(cmd.getNote(), 500);
        LocalDateTime base = batch.getEffectiveDueAt().isAfter(now()) ? batch.getEffectiveDueAt() : now();
        LocalDateTime effectiveDueAt = base.plusDays(days);
        if (mapper.extendDeadline(safeBatchId, batch.getCoordinationVersion(), effectiveDueAt) != 1) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        ContentMaintenanceBatchTaskCoordinationResult result =
                taskCommandFacade.extendBatchActiveTaskDueAt(
                        safeBatchId, batch.getDomain(), effectiveDueAt, operatorUid);
        if (result.affectedTaskCount() != active) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        insertEvent(safeBatchId, operatorUid, "DEADLINE_EXTENDED", batch.getEffectiveDueAt(), effectiveDueAt,
                null, null, null, null, result.affectedTaskCount(), note,
                batch.getCoordinationVersion() + 1);
        audit(operatorUid, safeBatchId, "CHANNEL_QUALITY_REVIEW_BATCH_DEADLINE_EXTEND",
                Map.of("affectedTaskCount", result.affectedTaskCount(), "extendByDays", days));
        return readCoordination(requireBatch(mapper.selectCoordinationById(safeBatchId)));
    }

    @Transactional
    public ChannelQualityReviewBatchCoordinationDTO reassignActiveTasks(
            Long batchId,
            ChannelQualityReviewBatchReassignActiveTasksCmd cmd,
            Long operatorUid) {
        requireTables();
        if (cmd == null) throw new BizException(ErrorCode.PARAM_ERROR);
        long safeBatchId = requireId(batchId);
        requireOperator(operatorUid);
        ChannelQualityReviewBatchCoordinationRow batch = requireBatch(mapper.lockCoordinationById(safeBatchId));
        requireModerate(operatorUid, batch.getDomain());
        requireExpectedVersion(cmd.getExpectedCoordinationVersion(), batch);
        String note = required(cmd.getNote(), 500);
        long replacementUid = requireId(cmd.getReplacementUid());
        if (reassignableTaskCount(batch) <= 0) throw new BizException(ErrorCode.INVALID_STATUS);
        if (mapper.advanceCoordinationVersion(safeBatchId, batch.getCoordinationVersion()) != 1) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        ContentMaintenanceBatchTaskCoordinationResult result =
                taskCommandFacade.reassignBatchActiveTasks(
                        safeBatchId, batch.getDomain(), replacementUid, operatorUid);
        insertEvent(safeBatchId, operatorUid, "ACTIVE_TASKS_REASSIGNED", null, null,
                result.previousAssigneeUid(), replacementUid, null, null,
                result.affectedTaskCount(), note,
                batch.getCoordinationVersion() + 1);
        audit(operatorUid, safeBatchId, "CHANNEL_QUALITY_REVIEW_BATCH_REASSIGN",
                Map.of("affectedTaskCount", result.affectedTaskCount()));
        return readCoordination(requireBatch(mapper.selectCoordinationById(safeBatchId)));
    }

    @Transactional
    public ChannelQualityReviewBatchCoordinationDTO addRiskNote(
            Long batchId,
            ChannelQualityReviewBatchRiskNoteCmd cmd,
            Long operatorUid) {
        requireTables();
        if (cmd == null) throw new BizException(ErrorCode.PARAM_ERROR);
        long safeBatchId = requireId(batchId);
        requireOperator(operatorUid);
        ChannelQualityReviewBatchCoordinationRow batch = requireBatch(mapper.lockCoordinationById(safeBatchId));
        requireModerate(operatorUid, batch.getDomain());
        requireExpectedVersion(cmd.getExpectedCoordinationVersion(), batch);
        String riskCode = enumValue(cmd.getRiskCode(), RISK_CODES);
        String note = required(cmd.getNote(), 500);
        if (mapper.advanceCoordinationVersion(safeBatchId, batch.getCoordinationVersion()) != 1) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        insertEvent(safeBatchId, operatorUid, "RISK_NOTE_ADDED", null, null, null, null,
                riskCode, null, 0, note, batch.getCoordinationVersion() + 1);
        audit(operatorUid, safeBatchId, "CHANNEL_QUALITY_REVIEW_BATCH_RISK_NOTE",
                Map.of("riskCode", riskCode));
        return readCoordination(requireBatch(mapper.selectCoordinationById(safeBatchId)));
    }

    @Transactional
    public ChannelQualityReviewBatchCoordinationDTO withdrawOpenTasks(
            Long batchId,
            ChannelQualityReviewBatchWithdrawOpenTasksCmd cmd,
            Long operatorUid) {
        requireTables();
        if (cmd == null) throw new BizException(ErrorCode.PARAM_ERROR);
        long safeBatchId = requireId(batchId);
        requireOperator(operatorUid);
        ChannelQualityReviewBatchCoordinationRow batch = requireBatch(mapper.lockCoordinationById(safeBatchId));
        requireModerate(operatorUid, batch.getDomain());
        requireExpectedVersion(cmd.getExpectedCoordinationVersion(), batch);
        String reasonCode = enumValue(cmd.getReasonCode(), WITHDRAW_REASON_CODES);
        String note = required(cmd.getNote(), 500);
        if (mapper.advanceCoordinationVersion(safeBatchId, batch.getCoordinationVersion()) != 1) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        ContentMaintenanceBatchTaskCoordinationResult result =
                taskCommandFacade.withdrawBatchOpenTasks(
                        safeBatchId, batch.getDomain(), cmd.getExpectedOpenTaskCount(),
                        cmd.getExpectedActiveTaskCount(), operatorUid, note);
        insertEvent(safeBatchId, operatorUid, "OPEN_TASKS_WITHDRAWN", null, null, null, null,
                null, reasonCode, result.affectedTaskCount(), note, batch.getCoordinationVersion() + 1);
        audit(operatorUid, safeBatchId, "CHANNEL_QUALITY_REVIEW_BATCH_WITHDRAW_OPEN_TASKS",
                Map.of("affectedTaskCount", result.affectedTaskCount(), "reasonCode", reasonCode));
        return readCoordination(requireBatch(mapper.selectCoordinationById(safeBatchId)));
    }

    @Transactional(readOnly = true)
    public ChannelQualityReviewBatchEventPageDTO events(
            Long batchId, Long cursor, Integer size, Long operatorUid) {
        requireTables();
        long safeBatchId = requireId(batchId);
        requireOperator(operatorUid);
        ChannelQualityReviewBatchCoordinationRow batch = requireBatch(mapper.selectCoordinationById(safeBatchId));
        requireModerate(operatorUid, batch.getDomain());
        int pageSize = pageSize(size);
        long safeCursor = cursor == null ? 0L : requireNonNegative(cursor);
        List<ChannelQualityReviewBatchEventRow> rows =
                mapper.listCoordinationEvents(safeBatchId, safeCursor, pageSize + 1);
        if (rows == null || rows.size() > pageSize + 1) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        List<ChannelQualityReviewBatchEventRow> visible = rows.stream().limit(pageSize).toList();
        long previous = Long.MAX_VALUE;
        for (ChannelQualityReviewBatchEventRow row : visible) {
            if (!validEvent(row, safeBatchId) || row.getId() >= previous) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            previous = row.getId();
        }
        Long nextCursor = rows.size() > pageSize && !visible.isEmpty()
                ? visible.get(visible.size() - 1).getId() : null;
        return ChannelQualityReviewBatchEventPageDTO.builder()
                .nextCursor(nextCursor)
                .items(visible.stream().map(ChannelQualityReviewBatchCoordinationService::toEvent).toList())
                .build();
    }

    private ChannelQualityReviewBatchCoordinationDTO readCoordination(
            ChannelQualityReviewBatchCoordinationRow batch) {
        Map<String, Integer> counts = taskCounts(batch.getId(), batch.getCandidateCount());
        List<ChannelQualityReviewBatchCoordinationTaskRow> taskRows =
                mapper.listCoordinationTasks(batch.getId(), MAX_TASKS + 1);
        if (taskRows == null || taskRows.size() > MAX_TASKS || taskRows.size() != batch.getCandidateCount()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        int open = counts.get("OPEN");
        int active = open + counts.get("CLAIMED") + counts.get("SUBMITTED");
        int reassignable = open + counts.get("CLAIMED");
        return ChannelQualityReviewBatchCoordinationDTO.builder()
                .batchId(batch.getId())
                .domain(batch.getDomain())
                .name(batch.getName())
                .dispatchAssigneeUid(batch.getAssigneeUid())
                .dueAt(toInstant(batch.getDueAt()))
                .effectiveDueAt(toInstant(batch.getEffectiveDueAt()))
                .coordinationVersion(batch.getCoordinationVersion())
                .progressState(progressState(counts))
                .dueState(dueState(batch.getEffectiveDueAt(), active))
                .openTaskCount(open)
                .activeTaskCount(active)
                .reassignableTaskCount(reassignable)
                .canExtendDueAt(active > 0 && batch.getEffectiveDueAt() != null)
                .canBulkReassign(reassignable > 0)
                .canAddRiskNote(true)
                .canWithdrawOpenTasks(open > 0)
                .taskStatusCounts(counts)
                .tasks(taskRows.stream()
                        .map(ChannelQualityReviewBatchCoordinationService::toCoordinationTask)
                        .toList())
                .build();
    }

    private Map<String, Integer> taskCounts(long batchId, int expectedTotal) {
        List<ChannelQualityReviewBatchTaskStatusRow> rows = mapper.listTaskStatusCounts(List.of(batchId));
        if (rows == null) throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        Map<String, Integer> counts = new LinkedHashMap<>();
        TASK_STATUSES.forEach(status -> counts.put(status, 0));
        for (ChannelQualityReviewBatchTaskStatusRow row : rows) {
            if (row == null || row.getBatchId() == null || row.getTaskCount() == null
                    || !batchIdEquals(batchId, row.getBatchId())
                    || !counts.containsKey(row.getStatus()) || row.getTaskCount() < 0) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            counts.put(row.getStatus(), Math.toIntExact(row.getTaskCount()));
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (total != expectedTotal) throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        return Map.copyOf(counts);
    }

    private int activeTaskCount(ChannelQualityReviewBatchCoordinationRow batch) {
        Map<String, Integer> counts = taskCounts(batch.getId(), batch.getCandidateCount());
        return counts.get("OPEN") + counts.get("CLAIMED") + counts.get("SUBMITTED");
    }

    private int reassignableTaskCount(ChannelQualityReviewBatchCoordinationRow batch) {
        Map<String, Integer> counts = taskCounts(batch.getId(), batch.getCandidateCount());
        return counts.get("OPEN") + counts.get("CLAIMED");
    }

    private void insertEvent(long batchId, Long operatorUid, String eventType,
                             LocalDateTime previousDueAt, LocalDateTime effectiveDueAt,
                             Long previousAssigneeUid, Long replacementAssigneeUid,
                             String riskCode, String withdrawReasonCode,
                             int affectedTaskCount, String note, int coordinationVersion) {
        if (mapper.insertCoordinationEvent(idGenerator.nextId(), batchId, operatorUid, eventType, previousDueAt,
                effectiveDueAt, previousAssigneeUid, replacementAssigneeUid, riskCode,
                withdrawReasonCode, affectedTaskCount, note, coordinationVersion) != 1) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private void audit(Long operatorUid, long batchId, String action, Map<String, Object> after) {
        adminAuditService.recordRequired(operatorUid, action, "CHANNEL_QUALITY_REVIEW_BATCH",
                batchId, Map.of(), after, "channel quality review batch coordination updated");
    }

    private static ChannelQualityReviewBatchCoordinationTaskDTO toCoordinationTask(
            ChannelQualityReviewBatchCoordinationTaskRow row) {
        if (row == null || row.getTaskId() == null || row.getTaskId() <= 0
                || !TASK_STATUSES.contains(row.getStatus()) || row.getAssigneeUid() == null
                || row.getAssigneeUid() <= 0 || row.getUpdateTime() == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        String phase = switch (row.getStatus()) {
            case "OPEN" -> "OPEN";
            case "CLAIMED" -> "REJECTED".equals(row.getLatestAttemptDecision()) ? "REWORK" : "IN_PROGRESS";
            case "SUBMITTED" -> "REVIEW_PENDING";
            case "COMPLETED" -> "VERIFIED_DELIVERY";
            case "CLOSED" -> "CLOSED";
            default -> throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        };
        return ChannelQualityReviewBatchCoordinationTaskDTO.builder()
                .taskId(row.getTaskId())
                .title(required(row.getTitle(), 160))
                .status(row.getStatus())
                .maintenancePhase(phase)
                .assigneeUid(row.getAssigneeUid())
                .dueAt(toInstant(row.getDueAt()))
                .updateTime(toInstant(row.getUpdateTime()))
                .canReassign("OPEN".equals(row.getStatus()) || "CLAIMED".equals(row.getStatus()))
                .canWithdraw("OPEN".equals(row.getStatus()))
                .build();
    }

    private static ChannelQualityReviewBatchEventDTO toEvent(ChannelQualityReviewBatchEventRow row) {
        return ChannelQualityReviewBatchEventDTO.builder()
                .id(row.getId())
                .eventType(row.getEventType())
                .previousDueAt(toInstant(row.getPreviousDueAt()))
                .effectiveDueAt(toInstant(row.getEffectiveDueAt()))
                .previousAssigneeUid(row.getPreviousAssigneeUid())
                .replacementAssigneeUid(row.getReplacementAssigneeUid())
                .riskCode(row.getRiskCode())
                .withdrawReasonCode(row.getWithdrawReasonCode())
                .affectedTaskCount(row.getAffectedTaskCount())
                .note(row.getNote())
                .coordinationVersion(row.getCoordinationVersion())
                .createTime(toInstant(row.getCreateTime()))
                .build();
    }

    private static boolean validEvent(ChannelQualityReviewBatchEventRow row, long batchId) {
        if (row == null || row.getId() == null || row.getId() <= 0
                || row.getBatchId() == null || !batchIdEquals(batchId, row.getBatchId())
                || row.getAffectedTaskCount() == null || row.getAffectedTaskCount() < 0
                || row.getCoordinationVersion() == null || row.getCoordinationVersion() < 1
                || row.getCreateTime() == null || !StringUtils.hasText(row.getNote())) {
            return false;
        }
        return switch (row.getEventType()) {
            case "DEADLINE_EXTENDED" -> row.getPreviousDueAt() != null
                    && row.getEffectiveDueAt() != null
                    && row.getEffectiveDueAt().isAfter(row.getPreviousDueAt())
                    && row.getAffectedTaskCount() > 0
                    && row.getPreviousAssigneeUid() == null
                    && row.getReplacementAssigneeUid() == null
                    && row.getRiskCode() == null
                    && row.getWithdrawReasonCode() == null;
            case "ACTIVE_TASKS_REASSIGNED" -> row.getPreviousDueAt() == null
                    && row.getEffectiveDueAt() == null
                    && row.getReplacementAssigneeUid() != null
                    && (row.getPreviousAssigneeUid() == null
                        || !row.getPreviousAssigneeUid().equals(row.getReplacementAssigneeUid()))
                    && row.getAffectedTaskCount() > 0
                    && row.getRiskCode() == null
                    && row.getWithdrawReasonCode() == null;
            case "RISK_NOTE_ADDED" -> row.getPreviousDueAt() == null
                    && row.getEffectiveDueAt() == null
                    && row.getPreviousAssigneeUid() == null
                    && row.getReplacementAssigneeUid() == null
                    && RISK_CODES.contains(row.getRiskCode())
                    && row.getWithdrawReasonCode() == null
                    && row.getAffectedTaskCount() == 0;
            case "OPEN_TASKS_WITHDRAWN" -> row.getPreviousDueAt() == null
                    && row.getEffectiveDueAt() == null
                    && row.getPreviousAssigneeUid() == null
                    && row.getReplacementAssigneeUid() == null
                    && row.getRiskCode() == null
                    && WITHDRAW_REASON_CODES.contains(row.getWithdrawReasonCode())
                    && row.getAffectedTaskCount() > 0;
            default -> false;
        };
    }

    private static String progressState(Map<String, Integer> counts) {
        if (counts.get("OPEN") > 0) return "ACTION_REQUIRED";
        if (counts.get("CLAIMED") > 0) return "IN_PROGRESS";
        if (counts.get("SUBMITTED") > 0) return "REVIEW_PENDING";
        if (counts.get("COMPLETED") > 0 && counts.get("CLOSED") > 0) return "PARTIALLY_CLOSED";
        if (counts.get("COMPLETED") > 0) return "COMPLETED";
        return "CLOSED";
    }

    private String dueState(LocalDateTime effectiveDueAt, int activeTaskCount) {
        if (effectiveDueAt == null || activeTaskCount <= 0) return "NOT_APPLICABLE";
        Instant deadline = toInstant(effectiveDueAt);
        Instant now = analyticsClock.instant();
        if (now.isAfter(deadline)) return "OVERDUE";
        return deadline.minusSeconds(48 * 60 * 60L).isAfter(now) ? "ON_TRACK" : "DUE_SOON";
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(analyticsClock.instant(), ZoneOffset.UTC);
    }

    private ChannelQualityReviewBatchCoordinationRow requireBatch(
            ChannelQualityReviewBatchCoordinationRow row) {
        if (row == null) throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        if (row.getId() == null || row.getId() <= 0 || row.getDomain() == null
                || row.getDomain() < 1 || row.getDomain() > 5 || !SOURCE_TYPE.equals(row.getSourceType())
                || !StringUtils.hasText(row.getName()) || row.getAssigneeUid() == null
                || row.getAssigneeUid() <= 0
                || row.getCoordinationVersion() == null || row.getCoordinationVersion() < 0
                || row.getCandidateCount() == null || row.getCandidateCount() < 1
                || row.getCandidateCount() > MAX_TASKS || row.getCreateTime() == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if ((row.getDueAt() == null) != (row.getEffectiveDueAt() == null)
                || (row.getDueAt() != null && row.getEffectiveDueAt().isBefore(row.getDueAt()))) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return row;
    }

    private void requireTables() {
        try {
            if (mapper.batchTableExists() > 0 && mapper.coordinationColumnsExist() == 2
                    && mapper.taskTableExists() > 0
                    && mapper.attemptTableExists() > 0 && mapper.coordinationEventTableExists() > 0
                    && mapper.coordinationEventColumnsExist() == 15
                    && mapper.coordinationEventConstraintsExist() == 4
                    && mapper.coordinationEventWithdrawnIndexExists() == 1) {
                return;
            }
        } catch (RuntimeException ignored) {
            // Fail closed when metadata cannot establish the V39 boundary.
        }
        throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                "Channel quality review batch coordination migration is required: " + MIGRATION);
    }

    private void requireModerate(Long uid, int domain) {
        if (adminPermissionService.isAdmin(uid)
                || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR)
                || adminPermissionService.isLocalOpenMode()) {
            return;
        }
        if (!domainModeratorService.canModerateDomain(uid, domain)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private static void requireExpectedVersion(Integer expectedVersion,
                                               ChannelQualityReviewBatchCoordinationRow batch) {
        if (expectedVersion == null || expectedVersion < 0) throw new BizException(ErrorCode.PARAM_ERROR);
        if (!expectedVersion.equals(batch.getCoordinationVersion())) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private static int enumInt(Integer value, Set<Integer> allowed) {
        if (value == null || !allowed.contains(value)) throw new BizException(ErrorCode.PARAM_ERROR);
        return value;
    }

    private static String enumValue(String value, Set<String> allowed) {
        if (!StringUtils.hasText(value)) throw new BizException(ErrorCode.PARAM_ERROR);
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new BizException(ErrorCode.PARAM_ERROR);
        return normalized;
    }

    private static String required(String value, int maxLength) {
        if (!StringUtils.hasText(value)) throw new BizException(ErrorCode.PARAM_ERROR);
        String normalized = value.trim();
        if (normalized.length() < 2 || normalized.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static int pageSize(Integer size) {
        int result = size == null ? DEFAULT_PAGE_SIZE : size;
        if (result < 1 || result > MAX_PAGE_SIZE) throw new BizException(ErrorCode.PARAM_ERROR);
        return result;
    }

    private static long requireId(Long value) {
        if (value == null || value <= 0) throw new BizException(ErrorCode.PARAM_ERROR);
        return value;
    }

    private static long requireNonNegative(Long value) {
        if (value == null || value < 0) throw new BizException(ErrorCode.PARAM_ERROR);
        return value;
    }

    private static void requireOperator(Long uid) {
        if (uid == null || uid <= 0) throw new BizException(ErrorCode.UNAUTHORIZED);
    }

    private static boolean batchIdEquals(long expected, Long actual) {
        return actual != null && actual == expected;
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
