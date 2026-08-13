package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseAcknowledgeCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseAssignOwnerCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseBatchSummaryDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseCloseCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseCreateCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseEventDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseEventPageDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCasePlanCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseProgressCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseQueueItemDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseQueuePageDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseSubmitResolutionCmd;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchCoordinationRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchMapper;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchTaskStatusRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseEventRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseMapper;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseQueueRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseRiskNoteEventRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseRow;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.application.DomainModeratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChannelQualityReviewRiskCaseService {

    private static final String SOURCE_TYPE = "CHANNEL_HEALTH";
    private static final String MIGRATION =
            "db/migration/20260807_channel_quality_review_risk_case_evidence_retrospective.sql";
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 20;
    private static final int MAX_BATCH_TASKS = 20;
    private static final Set<String> CASE_STATUSES =
            Set.of("OPEN", "ACKNOWLEDGED", "IN_PROGRESS", "RESOLVED", "CLOSED");
    private static final Set<String> RISK_CODES =
            Set.of("BLOCKER", "CAPACITY_RISK", "REVIEW_DELAY", "OVERDUE_ESCALATION");
    private static final Set<String> DUE_STATES =
            Set.of("NOT_APPLICABLE", "ON_TRACK", "DUE_SOON", "OVERDUE");
    private static final Set<String> PRIORITIES = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Set<String> QUEUE_MODES = Set.of("ALL", "UNHANDLED", "ACTIVE", "RESOLVED");
    private static final Set<String> QUEUE_SOURCES =
            Set.of("ACTIVE_CASE", "UNHANDLED_RISK_EVENT", "DUE_SOON", "OVERDUE");
    private static final Set<String> TASK_STATUSES =
            Set.of("OPEN", "CLAIMED", "SUBMITTED", "COMPLETED", "CLOSED");

    private final ChannelQualityReviewRiskCaseMapper riskCaseMapper;
    private final ChannelQualityReviewBatchMapper batchMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final AdminAuditService adminAuditService;
    private final AdminPermissionService adminPermissionService;
    private final DomainModeratorService domainModeratorService;
    private final ChannelQualityReviewRiskCaseGovernanceService governanceService;
    private final Clock analyticsClock;

    @Transactional(readOnly = true)
    public ChannelQualityReviewRiskCaseQueuePageDTO queue(
            Integer domain, String mode, String cursor, Integer size, Long operatorUid) {
        requireTables();
        int safeDomain = requireDomain(domain);
        requireOperator(operatorUid);
        requireModerate(operatorUid, safeDomain);
        String safeMode = optionalEnum(mode, QUEUE_MODES, "ALL");
        int pageSize = pageSize(size);
        QueueCursor decoded = decodeCursor(cursor);
        List<ChannelQualityReviewRiskCaseQueueRow> rows = riskCaseMapper.listQueueRows(
                safeDomain,
                safeMode,
                LocalDateTime.ofInstant(analyticsClock.instant(), ZoneOffset.UTC),
                decoded == null ? null : decoded.priority(),
                decoded == null ? null : decoded.updateTime(),
                decoded == null ? null : decoded.rowId(),
                decoded == null ? null : decoded.source(),
                pageSize + 1);
        if (rows == null || rows.size() > pageSize + 1) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        List<ChannelQualityReviewRiskCaseQueueRow> visible = rows.stream().limit(pageSize).toList();
        QueueOrder previous = null;
        for (ChannelQualityReviewRiskCaseQueueRow row : visible) {
            QueueOrder current = requireQueueRow(row, safeDomain);
            if (previous != null && compareQueueOrder(previous, current) >= 0) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            previous = current;
        }
        String nextCursor = rows.size() > pageSize && !visible.isEmpty()
                ? encodeCursor(requireQueueRow(visible.get(visible.size() - 1), safeDomain))
                : null;
        return ChannelQualityReviewRiskCaseQueuePageDTO.builder()
                .nextCursor(nextCursor)
                .items(visible.stream().map(ChannelQualityReviewRiskCaseService::toQueueItem).toList())
                .build();
    }

    @Transactional
    public ChannelQualityReviewRiskCaseDTO create(
            Long batchId, ChannelQualityReviewRiskCaseCreateCmd cmd, Long operatorUid) {
        requireTables();
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        long safeBatchId = requireId(batchId);
        requireOperator(operatorUid);
        ChannelQualityReviewBatchCoordinationRow batch =
                requireBatch(batchMapper.lockCoordinationById(safeBatchId));
        requireModerate(operatorUid, batch.getDomain());
        requireCoordinationVersion(cmd.getExpectedCoordinationVersion(), batch);
        String note = required(cmd.getNote());

        ChannelQualityReviewRiskCaseRow active = riskCaseMapper.selectActiveByBatchId(safeBatchId);
        if (active != null) {
            requireCase(active);
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        Trigger trigger = createTrigger(batch, cmd.getRiskEventId());
        if (trigger.riskEventId() != null && riskCaseMapper.selectByRiskEventId(trigger.riskEventId()) != null) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }

        long caseId = idGenerator.nextId();
        try {
            if (riskCaseMapper.insertCase(
                    caseId,
                    safeBatchId,
                    batch.getDomain(),
                    trigger.triggerType(),
                    trigger.riskEventId(),
                    trigger.riskCode(),
                    trigger.dueState(),
                    "OPEN",
                    null,
                    1,
                    batch.getCoordinationVersion(),
                    operatorUid) != 1) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        governanceService.initializeCaseGovernance(caseId, safeBatchId, batch.getDomain());
        insertEvent(caseId, safeBatchId, operatorUid, "CASE_OPENED", null, "OPEN", null,
                batch.getCoordinationVersion(), 1, note);
        audit(operatorUid, caseId, "CHANNEL_QUALITY_REVIEW_RISK_CASE_CREATE",
                Map.of("batchId", safeBatchId, "triggerType", trigger.triggerType()));
        return detail(caseId, operatorUid);
    }

    @Transactional(readOnly = true)
    public ChannelQualityReviewRiskCaseDTO detail(Long caseId, Long operatorUid) {
        requireTables();
        long safeCaseId = requireId(caseId);
        requireOperator(operatorUid);
        ChannelQualityReviewRiskCaseRow riskCase = requireCase(riskCaseMapper.selectById(safeCaseId));
        ChannelQualityReviewBatchCoordinationRow batch =
                requireBatch(batchMapper.selectCoordinationById(riskCase.getBatchId()));
        requireCaseBatch(riskCase, batch);
        requireModerate(operatorUid, batch.getDomain());
        ChannelQualityReviewBatchRow batchSummary = requireBatchSummary(
                batchMapper.selectById(riskCase.getBatchId()), batch);
        int activeTaskCount = activeTaskCount(batch);
        String dueState = dueState(batch.getEffectiveDueAt(), activeTaskCount);
        return toDetail(riskCase, batch, batchSummary, activeTaskCount, dueState, operatorUid);
    }

    @Transactional(readOnly = true)
    public ChannelQualityReviewRiskCaseEventPageDTO events(
            Long caseId, String cursor, Integer size, Long operatorUid) {
        requireTables();
        long safeCaseId = requireId(caseId);
        requireOperator(operatorUid);
        ChannelQualityReviewRiskCaseRow riskCase = requireCase(riskCaseMapper.selectById(safeCaseId));
        ChannelQualityReviewBatchCoordinationRow batch =
                requireBatch(batchMapper.selectCoordinationById(riskCase.getBatchId()));
        requireCaseBatch(riskCase, batch);
        requireModerate(operatorUid, batch.getDomain());
        long safeCursor = decodeEventCursor(cursor);
        int pageSize = pageSize(size);
        List<ChannelQualityReviewRiskCaseEventRow> rows =
                riskCaseMapper.listEvents(safeCaseId, safeCursor, pageSize + 1);
        if (rows == null || rows.size() > pageSize + 1) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        List<ChannelQualityReviewRiskCaseEventRow> visible = rows.stream().limit(pageSize).toList();
        long previous = Long.MAX_VALUE;
        for (ChannelQualityReviewRiskCaseEventRow row : visible) {
            if (!validEvent(row, riskCase) || row.getId() >= previous) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            previous = row.getId();
        }
        String nextCursor = rows.size() > pageSize && !visible.isEmpty()
                ? encodeEventCursor(visible.get(visible.size() - 1).getId())
                : null;
        return ChannelQualityReviewRiskCaseEventPageDTO.builder()
                .nextCursor(nextCursor)
                .items(visible.stream().map(ChannelQualityReviewRiskCaseService::toEvent).toList())
                .build();
    }

    @Transactional
    public ChannelQualityReviewRiskCaseDTO assignOwner(
            Long caseId, ChannelQualityReviewRiskCaseAssignOwnerCmd cmd, Long operatorUid) {
        requireTables();
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        long safeCaseId = requireId(caseId);
        requireOperator(operatorUid);
        ChannelQualityReviewRiskCaseRow riskCase = requireCase(riskCaseMapper.lockById(safeCaseId));
        ChannelQualityReviewBatchCoordinationRow batch =
                requireBatch(batchMapper.selectCoordinationById(riskCase.getBatchId()));
        requireCaseBatch(riskCase, batch);
        requireModerate(operatorUid, batch.getDomain());
        requireCaseVersion(cmd.getExpectedCaseVersion(), riskCase);
        if (!Set.of("OPEN", "ACKNOWLEDGED", "IN_PROGRESS").contains(riskCase.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        long ownerUid = requireId(cmd.getOwnerUid());
        if (Long.valueOf(ownerUid).equals(riskCase.getOwnerUid())) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        requireEligibleOwner(ownerUid, batch.getDomain());
        String note = required(cmd.getNote());
        advanceCase(riskCase, riskCase.getStatus(), ownerUid, operatorUid, "OWNER_ASSIGNED",
                note, batch.getCoordinationVersion());
        audit(operatorUid, safeCaseId, "CHANNEL_QUALITY_REVIEW_RISK_CASE_ASSIGN_OWNER",
                Map.of("batchId", riskCase.getBatchId(), "ownerUid", ownerUid));
        return detail(safeCaseId, operatorUid);
    }

    @Transactional
    public ChannelQualityReviewRiskCaseDTO acknowledge(
            Long caseId, ChannelQualityReviewRiskCaseAcknowledgeCmd cmd, Long operatorUid) {
        requireTables();
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return ownerTransition(
                caseId,
                cmd.getExpectedCaseVersion(),
                cmd.getNote(),
                operatorUid,
                "OPEN",
                "ACKNOWLEDGED",
                "OWNER_ACKNOWLEDGED",
                "CHANNEL_QUALITY_REVIEW_RISK_CASE_ACKNOWLEDGE");
    }

    @Transactional
    public ChannelQualityReviewRiskCaseDTO recordPlan(
            Long caseId, ChannelQualityReviewRiskCasePlanCmd cmd, Long operatorUid) {
        requireTables();
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return ownerTransition(
                caseId,
                cmd.getExpectedCaseVersion(),
                cmd.getNote(),
                operatorUid,
                "ACKNOWLEDGED",
                "IN_PROGRESS",
                "PLAN_RECORDED",
                "CHANNEL_QUALITY_REVIEW_RISK_CASE_PLAN");
    }

    @Transactional
    public ChannelQualityReviewRiskCaseDTO recordProgress(
            Long caseId, ChannelQualityReviewRiskCaseProgressCmd cmd, Long operatorUid) {
        requireTables();
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return ownerTransition(
                caseId,
                cmd.getExpectedCaseVersion(),
                cmd.getNote(),
                operatorUid,
                "IN_PROGRESS",
                "IN_PROGRESS",
                "PROGRESS_RECORDED",
                "CHANNEL_QUALITY_REVIEW_RISK_CASE_PROGRESS");
    }

    @Transactional
    public ChannelQualityReviewRiskCaseDTO submitResolution(
            Long caseId, ChannelQualityReviewRiskCaseSubmitResolutionCmd cmd, Long operatorUid) {
        requireTables();
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        long safeCaseId = requireId(caseId);
        requireOperator(operatorUid);
        ChannelQualityReviewRiskCaseRow riskCase = requireCase(riskCaseMapper.lockById(safeCaseId));
        ChannelQualityReviewBatchCoordinationRow batch =
                requireBatch(batchMapper.lockCoordinationById(riskCase.getBatchId()));
        requireCaseBatch(riskCase, batch);
        requireModerate(operatorUid, batch.getDomain());
        requireCaseVersion(cmd.getExpectedCaseVersion(), riskCase);
        requireCoordinationVersion(cmd.getExpectedCoordinationVersion(), batch);
        requireOwnerAction(operatorUid, riskCase, batch.getDomain());
        if (!"IN_PROGRESS".equals(riskCase.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        String note = required(cmd.getNote());
        advanceCase(riskCase, "RESOLVED", riskCase.getOwnerUid(), operatorUid,
                "RESOLUTION_SUBMITTED", note, batch.getCoordinationVersion());
        audit(operatorUid, safeCaseId, "CHANNEL_QUALITY_REVIEW_RISK_CASE_RESOLUTION_SUBMIT",
                Map.of("batchId", riskCase.getBatchId(),
                        "coordinationVersion", batch.getCoordinationVersion()));
        return detail(safeCaseId, operatorUid);
    }

    @Transactional
    public ChannelQualityReviewRiskCaseDTO close(
            Long caseId, ChannelQualityReviewRiskCaseCloseCmd cmd, Long operatorUid) {
        throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                "V41 close snapshot contract is required for risk-case closure");
    }

    private ChannelQualityReviewRiskCaseDTO ownerTransition(
            Long caseId,
            Integer expectedCaseVersion,
            String rawNote,
            Long operatorUid,
            String requiredStatus,
            String targetStatus,
            String eventType,
            String auditAction) {
        long safeCaseId = requireId(caseId);
        requireOperator(operatorUid);
        ChannelQualityReviewRiskCaseRow riskCase = requireCase(riskCaseMapper.lockById(safeCaseId));
        ChannelQualityReviewBatchCoordinationRow batch =
                requireBatch(batchMapper.selectCoordinationById(riskCase.getBatchId()));
        requireCaseBatch(riskCase, batch);
        requireModerate(operatorUid, batch.getDomain());
        requireCaseVersion(expectedCaseVersion, riskCase);
        requireOwnerAction(operatorUid, riskCase, batch.getDomain());
        if (!requiredStatus.equals(riskCase.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        if (riskCase.getOwnerUid() == null) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        String note = required(rawNote);
        advanceCase(riskCase, targetStatus, riskCase.getOwnerUid(), operatorUid, eventType,
                note, batch.getCoordinationVersion());
        audit(operatorUid, safeCaseId, auditAction,
                Map.of("batchId", riskCase.getBatchId(), "status", targetStatus));
        return detail(safeCaseId, operatorUid);
    }

    private Trigger createTrigger(ChannelQualityReviewBatchCoordinationRow batch, Long riskEventId) {
        if (riskEventId != null) {
            long safeRiskEventId = requireId(riskEventId);
            ChannelQualityReviewRiskCaseRiskNoteEventRow riskEvent =
                    riskCaseMapper.selectRiskNoteEvent(batch.getId(), safeRiskEventId);
            if (riskEvent == null || !Objects.equals(riskEvent.getId(), safeRiskEventId)
                    || !Objects.equals(riskEvent.getBatchId(), batch.getId())
                    || !"RISK_NOTE_ADDED".equals(riskEvent.getEventType())
                    || !RISK_CODES.contains(riskEvent.getRiskCode())
                    || riskEvent.getCoordinationVersion() == null || riskEvent.getCoordinationVersion() < 0
                    || riskEvent.getCoordinationVersion() > batch.getCoordinationVersion()) {
                throw new BizException(ErrorCode.INVALID_STATUS);
            }
            return new Trigger("RISK_EVENT", safeRiskEventId, riskEvent.getRiskCode(), null);
        }
        String currentDueState = dueState(batch.getEffectiveDueAt(), activeTaskCount(batch));
        if (!"DUE_SOON".equals(currentDueState) && !"OVERDUE".equals(currentDueState)) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        return new Trigger("DUE_STATE", null, null, currentDueState);
    }

    private void advanceCase(
            ChannelQualityReviewRiskCaseRow riskCase,
            String targetStatus,
            Long targetOwnerUid,
            Long operatorUid,
            String eventType,
            String note,
            int observedCoordinationVersion) {
        if ("CLOSED".equals(targetStatus)) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        if (riskCaseMapper.updateCase(
                riskCase.getId(),
                riskCase.getCaseVersion(),
                targetStatus,
                targetOwnerUid) != 1) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        long eventId = insertEvent(
                riskCase.getId(),
                riskCase.getBatchId(),
                operatorUid,
                eventType,
                riskCase.getStatus(),
                targetStatus,
                "OWNER_ASSIGNED".equals(eventType) ? targetOwnerUid : null,
                observedCoordinationVersion,
                riskCase.getCaseVersion() + 1,
                note);
        if (Set.of("OWNER_ASSIGNED", "OWNER_ACKNOWLEDGED", "PLAN_RECORDED").contains(eventType)) {
            ChannelQualityReviewRiskCaseRow updated = new ChannelQualityReviewRiskCaseRow();
            updated.setId(riskCase.getId());
            updated.setBatchId(riskCase.getBatchId());
            updated.setDomain(riskCase.getDomain());
            updated.setStatus(targetStatus);
            updated.setOwnerUid(targetOwnerUid);
            updated.setCaseVersion(riskCase.getCaseVersion() + 1);
            governanceService.appendV40LifecycleFact(
                    updated,
                    new ChannelQualityReviewRiskCaseV40LifecycleEvent(
                            eventId,
                            eventType,
                            targetStatus,
                            "OWNER_ASSIGNED".equals(eventType) ? targetOwnerUid : null,
                            riskCase.getCaseVersion() + 1,
                            analyticsClock.instant()));
        }
    }

    private long insertEvent(
            long caseId,
            long batchId,
            Long operatorUid,
            String eventType,
            String previousStatus,
            String status,
            Long ownerUid,
            int observedCoordinationVersion,
            int caseVersion,
            String note) {
        long eventId = idGenerator.nextId();
        if (riskCaseMapper.insertEvent(
                eventId,
                caseId,
                batchId,
                operatorUid,
                eventType,
                previousStatus,
                status,
                ownerUid,
                observedCoordinationVersion,
                caseVersion,
                note) != 1) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return eventId;
    }

    private ChannelQualityReviewRiskCaseDTO toDetail(
            ChannelQualityReviewRiskCaseRow riskCase,
            ChannelQualityReviewBatchCoordinationRow batch,
            ChannelQualityReviewBatchRow batchSummary,
            int activeTaskCount,
            String dueState,
            Long operatorUid) {
        boolean moderator = canModerate(operatorUid, batch.getDomain());
        boolean ownerAction = canOperateAsOwnerOrModerator(operatorUid, riskCase, batch.getDomain());
        boolean assignable = Set.of("OPEN", "ACKNOWLEDGED", "IN_PROGRESS").contains(riskCase.getStatus());
        return ChannelQualityReviewRiskCaseDTO.builder()
                .id(riskCase.getId())
                .batch(ChannelQualityReviewRiskCaseBatchSummaryDTO.builder()
                        .batchId(riskCase.getBatchId())
                        .domain(riskCase.getDomain())
                        .name(batchSummary.getName())
                        .priority(batchSummary.getPriority())
                        .dueAt(toInstant(batch.getDueAt()))
                        .effectiveDueAt(toInstant(batch.getEffectiveDueAt()))
                        .activeTaskCount(activeTaskCount)
                        .dueState(dueState)
                        .build())
                .triggerType(riskCase.getTriggerType())
                .riskEventId(riskCase.getRiskEventId())
                .riskCode(riskCase.getRiskCode())
                .dueState("DUE_STATE".equals(riskCase.getTriggerType()) ? riskCase.getDueState() : null)
                .status(riskCase.getStatus())
                .ownerUid(riskCase.getOwnerUid())
                .caseVersion(riskCase.getCaseVersion())
                .openedCoordinationVersion(riskCase.getOpenedCoordinationVersion())
                .coordinationVersion(batch.getCoordinationVersion())
                .createTime(toInstant(riskCase.getCreateTime()))
                .updateTime(toInstant(riskCase.getUpdateTime()))
                .canAssignOwner(moderator && assignable)
                .canAcknowledge(ownerAction && "OPEN".equals(riskCase.getStatus())
                        && riskCase.getOwnerUid() != null)
                .canRecordPlan(ownerAction && "ACKNOWLEDGED".equals(riskCase.getStatus()))
                .canRecordProgress(ownerAction && "IN_PROGRESS".equals(riskCase.getStatus()))
                .canSubmitResolution(ownerAction && "IN_PROGRESS".equals(riskCase.getStatus()))
                .canClose(moderator && "RESOLVED".equals(riskCase.getStatus())
                        && canCloseByDueState(riskCase, dueState))
                .build();
    }

    private static ChannelQualityReviewRiskCaseQueueItemDTO toQueueItem(
            ChannelQualityReviewRiskCaseQueueRow row) {
        return ChannelQualityReviewRiskCaseQueueItemDTO.builder()
                .batchId(row.getBatchId())
                .domain(row.getDomain())
                .batchName(row.getBatchName())
                .priority(row.getPriority())
                .dueAt(toInstant(row.getDueAt()))
                .effectiveDueAt(toInstant(row.getEffectiveDueAt()))
                .activeTaskCount(row.getActiveTaskCount())
                .dueState(row.getDueState())
                .triggerType(row.getQueueSource())
                .riskEventId(row.getRiskEventId())
                .riskCode(row.getRiskCode())
                .triggerSummary(queueSummary(row))
                .caseId(row.getCaseId())
                .caseStatus(row.getStatus())
                .coordinationOwnerUid(row.getOwnerUid())
                .coordinationVersion(row.getCoordinationVersion())
                .updateTime(toInstant(row.getUpdateTime()))
                .build();
    }

    private static String queueSummary(ChannelQualityReviewRiskCaseQueueRow row) {
        return switch (row.getQueueSource()) {
            case "ACTIVE_CASE" -> "当前存在活动风险处置单";
            case "UNHANDLED_RISK_EVENT" -> "存在未处置的批次风险说明";
            case "DUE_SOON" -> "当前批次临近有效截止时间";
            case "OVERDUE" -> "当前批次已超过有效截止时间";
            default -> throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        };
    }

    private static ChannelQualityReviewRiskCaseEventDTO toEvent(
            ChannelQualityReviewRiskCaseEventRow row) {
        return ChannelQualityReviewRiskCaseEventDTO.builder()
                .id(row.getId())
                .eventType(row.getEventType())
                .previousStatus(row.getPreviousStatus())
                .status(row.getStatus())
                .caseVersion(row.getCaseVersion())
                .note(row.getNote())
                .createTime(toInstant(row.getCreateTime()))
                .build();
    }

    private int activeTaskCount(ChannelQualityReviewBatchCoordinationRow batch) {
        List<ChannelQualityReviewBatchTaskStatusRow> rows =
                batchMapper.listTaskStatusCounts(List.of(batch.getId()));
        if (rows == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        TASK_STATUSES.forEach(status -> counts.put(status, 0));
        for (ChannelQualityReviewBatchTaskStatusRow row : rows) {
            if (row == null || !Objects.equals(row.getBatchId(), batch.getId())
                    || row.getTaskCount() == null || row.getTaskCount() < 0
                    || !TASK_STATUSES.contains(row.getStatus())) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            counts.put(row.getStatus(), Math.toIntExact(row.getTaskCount()));
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (total != batch.getCandidateCount()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return counts.get("OPEN") + counts.get("CLAIMED") + counts.get("SUBMITTED");
    }

    private String dueState(LocalDateTime effectiveDueAt, int activeTaskCount) {
        if (effectiveDueAt == null || activeTaskCount <= 0) {
            return "NOT_APPLICABLE";
        }
        Instant deadline = toInstant(effectiveDueAt);
        Instant now = analyticsClock.instant();
        if (now.isAfter(deadline)) {
            return "OVERDUE";
        }
        return deadline.minusSeconds(48 * 60 * 60L).isAfter(now) ? "ON_TRACK" : "DUE_SOON";
    }

    private void requireTables() {
        try {
            if (riskCaseMapper.caseTableExists() > 0
                    && riskCaseMapper.eventTableExists() > 0
                    && riskCaseMapper.caseColumnsExist() == 17
                    && riskCaseMapper.eventColumnsExist() == 12
                    && riskCaseMapper.caseConstraintsExist() == 7
                    && riskCaseMapper.eventConstraintsExist() == 4
                    && riskCaseMapper.caseIndexesExist() == 7
                    && riskCaseMapper.eventIndexesExist() == 3
                    && batchMapper.batchTableExists() > 0
                    && batchMapper.taskTableExists() > 0
                    && batchMapper.coordinationEventTableExists() > 0
                    && batchMapper.coordinationColumnsExist() == 2) {
                return;
            }
        } catch (RuntimeException ignored) {
            // Metadata is part of the V40 fail-closed contract.
        }
        throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                "Channel quality review risk-case migration is required: " + MIGRATION);
    }

    private void requireModerate(Long uid, int domain) {
        if (!canModerate(uid, domain)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireEligibleOwner(Long ownerUid, int domain) {
        if (!canModerate(ownerUid, domain)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireOwnerAction(
            Long operatorUid, ChannelQualityReviewRiskCaseRow riskCase, int domain) {
        if (!canOperateAsOwnerOrModerator(operatorUid, riskCase, domain)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean canOperateAsOwnerOrModerator(
            Long operatorUid, ChannelQualityReviewRiskCaseRow riskCase, int domain) {
        return canModerate(operatorUid, domain)
                || (operatorUid != null
                && operatorUid.equals(riskCase.getOwnerUid())
                && canModerate(riskCase.getOwnerUid(), domain));
    }

    private boolean canModerate(Long uid, int domain) {
        return uid != null && uid > 0
                && (isGlobalModerator(uid) || domainModeratorService.canModerateDomain(uid, domain));
    }

    private boolean isGlobalModerator(Long uid) {
        return adminPermissionService.isAdmin(uid)
                || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR)
                || adminPermissionService.isLocalOpenMode();
    }

    private static ChannelQualityReviewBatchCoordinationRow requireBatch(
            ChannelQualityReviewBatchCoordinationRow row) {
        if (row == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (row.getId() == null || row.getId() <= 0 || row.getDomain() == null
                || row.getDomain() < 1 || row.getDomain() > 5
                || !SOURCE_TYPE.equals(row.getSourceType())
                || !StringUtils.hasText(row.getName())
                || row.getAssigneeUid() == null || row.getAssigneeUid() <= 0
                || row.getCoordinationVersion() == null || row.getCoordinationVersion() < 0
                || row.getCandidateCount() == null || row.getCandidateCount() < 1
                || row.getCandidateCount() > MAX_BATCH_TASKS || row.getCreateTime() == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if ((row.getDueAt() == null) != (row.getEffectiveDueAt() == null)
                || (row.getDueAt() != null && row.getEffectiveDueAt().isBefore(row.getDueAt()))) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return row;
    }

    private static ChannelQualityReviewBatchRow requireBatchSummary(
            ChannelQualityReviewBatchRow row, ChannelQualityReviewBatchCoordinationRow coordination) {
        if (row == null || row.getId() == null || !row.getId().equals(coordination.getId())
                || row.getDomain() == null || !row.getDomain().equals(coordination.getDomain())
                || !SOURCE_TYPE.equals(row.getSourceType())
                || !StringUtils.hasText(row.getName()) || !row.getName().equals(coordination.getName())
                || !PRIORITIES.contains(row.getPriority())
                || (row.getDueAt() == null) != (coordination.getDueAt() == null)
                || (row.getDueAt() != null && !row.getDueAt().equals(coordination.getDueAt()))
                || row.getCandidateCount() == null
                || !row.getCandidateCount().equals(coordination.getCandidateCount())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return row;
    }

    private static ChannelQualityReviewRiskCaseRow requireCase(ChannelQualityReviewRiskCaseRow row) {
        if (row == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (row.getId() == null || row.getId() <= 0 || row.getBatchId() == null || row.getBatchId() <= 0
                || row.getDomain() == null || row.getDomain() < 1 || row.getDomain() > 5
                || !CASE_STATUSES.contains(row.getStatus()) || row.getCaseVersion() == null
                || row.getCaseVersion() < 1 || row.getOpenedCoordinationVersion() == null
                || row.getOpenedCoordinationVersion() < 0 || row.getCreatedByUid() == null
                || row.getCreatedByUid() <= 0 || row.getLegacyClosedWithoutSnapshot() == null
                || row.getCreateTime() == null || row.getUpdateTime() == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        boolean riskEvent = "RISK_EVENT".equals(row.getTriggerType());
        boolean dueState = "DUE_STATE".equals(row.getTriggerType());
        if ((!riskEvent && !dueState)
                || (riskEvent && (row.getRiskEventId() == null || row.getRiskEventId() <= 0
                || !RISK_CODES.contains(row.getRiskCode()) || row.getDueState() != null))
                || (dueState && (row.getRiskEventId() != null || row.getRiskCode() != null
                || !"DUE_SOON".equals(row.getDueState()) && !"OVERDUE".equals(row.getDueState())))
                || (!"OPEN".equals(row.getStatus())
                && (row.getOwnerUid() == null || row.getOwnerUid() <= 0))) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        Long expectedActiveBatchId = "CLOSED".equals(row.getStatus()) ? null : row.getBatchId();
        if (expectedActiveBatchId == null ? row.getActiveBatchId() != null
                : !expectedActiveBatchId.equals(row.getActiveBatchId())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        boolean hasSnapshot = row.getV41CloseSnapshotId() != null && row.getV41CloseSnapshotId() > 0;
        boolean legacyClosed = row.getLegacyClosedWithoutSnapshot() == 1;
        if (("CLOSED".equals(row.getStatus()) && hasSnapshot == legacyClosed)
                || (!"CLOSED".equals(row.getStatus()) && (hasSnapshot || legacyClosed))) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return row;
    }

    private static void requireCaseBatch(
            ChannelQualityReviewRiskCaseRow riskCase, ChannelQualityReviewBatchCoordinationRow batch) {
        if (!riskCase.getBatchId().equals(batch.getId())
                || !riskCase.getDomain().equals(batch.getDomain())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private static QueueOrder requireQueueRow(ChannelQualityReviewRiskCaseQueueRow row, int expectedDomain) {
        if (row == null || !QUEUE_SOURCES.contains(row.getQueueSource())
                || row.getQueuePriority() == null || row.getQueuePriority() < 1 || row.getQueuePriority() > 4
                || row.getQueueUpdateTime() == null || row.getQueueRowId() == null || row.getQueueRowId() <= 0
                || row.getBatchId() == null || row.getBatchId() <= 0 || row.getDomain() == null
                || row.getDomain() != expectedDomain || !StringUtils.hasText(row.getBatchName())
                || !PRIORITIES.contains(row.getPriority()) || row.getCoordinationVersion() == null
                || row.getCoordinationVersion() < 0 || row.getCandidateCount() == null
                || row.getCandidateCount() < 1 || row.getCandidateCount() > MAX_BATCH_TASKS
                || row.getActiveTaskCount() == null || row.getActiveTaskCount() < 0
                || row.getActiveTaskCount() > row.getCandidateCount()
                || !DUE_STATES.contains(row.getDueState()) || row.getUpdateTime() == null
                || (row.getDueAt() == null) != (row.getEffectiveDueAt() == null)
                || (row.getDueAt() != null && row.getEffectiveDueAt().isBefore(row.getDueAt()))) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        boolean active = "ACTIVE_CASE".equals(row.getQueueSource());
        boolean risk = "UNHANDLED_RISK_EVENT".equals(row.getQueueSource());
        boolean due = "DUE_SOON".equals(row.getQueueSource()) || "OVERDUE".equals(row.getQueueSource());
        if (active) {
            requireQueueCase(row);
        } else if (risk) {
            if (!"RISK_EVENT".equals(row.getTriggerType()) || row.getRiskEventId() == null
                    || row.getRiskEventId() <= 0 || !RISK_CODES.contains(row.getRiskCode())
                    || row.getCaseId() != null || row.getStatus() != null || row.getOwnerUid() != null
                    || row.getCaseVersion() != null || row.getOpenedCoordinationVersion() != null) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
        } else if (!due || !"DUE_STATE".equals(row.getTriggerType())
                || !row.getQueueSource().equals(row.getDueState()) || row.getRiskEventId() != null
                || row.getRiskCode() != null || row.getCaseId() != null || row.getStatus() != null
                || row.getOwnerUid() != null || row.getCaseVersion() != null
                || row.getOpenedCoordinationVersion() != null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return new QueueOrder(
                row.getQueuePriority(), row.getQueueUpdateTime(), row.getQueueRowId(), row.getQueueSource());
    }

    private static void requireQueueCase(ChannelQualityReviewRiskCaseQueueRow row) {
        if (row.getCaseId() == null || row.getCaseId() <= 0 || !CASE_STATUSES.contains(row.getStatus())
                || "CLOSED".equals(row.getStatus()) || row.getCaseVersion() == null
                || row.getCaseVersion() < 1 || row.getOpenedCoordinationVersion() == null
                || row.getOpenedCoordinationVersion() < 0
                || (!"RISK_EVENT".equals(row.getTriggerType()) && !"DUE_STATE".equals(row.getTriggerType()))) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if ("RISK_EVENT".equals(row.getTriggerType())
                && (row.getRiskEventId() == null || row.getRiskEventId() <= 0
                || !RISK_CODES.contains(row.getRiskCode()))) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if ("DUE_STATE".equals(row.getTriggerType())
                && (row.getRiskEventId() != null || row.getRiskCode() != null)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private static boolean validEvent(
            ChannelQualityReviewRiskCaseEventRow row, ChannelQualityReviewRiskCaseRow riskCase) {
        if (row == null || row.getId() == null || row.getId() <= 0
                || row.getCaseId() == null || !row.getCaseId().equals(riskCase.getId())
                || row.getBatchId() == null || !row.getBatchId().equals(riskCase.getBatchId())
                || row.getOperatorUid() == null || row.getOperatorUid() <= 0 || row.getEventType() == null
                || row.getObservedCoordinationVersion() == null
                || row.getObservedCoordinationVersion() < 0 || row.getCaseVersion() == null
                || row.getCaseVersion() < 1 || row.getCreateTime() == null) {
            return false;
        }
        try {
            required(row.getNote());
        } catch (BizException ignored) {
            return false;
        }
        return switch (row.getEventType()) {
            case "CASE_OPENED" -> row.getPreviousStatus() == null && "OPEN".equals(row.getStatus())
                    && row.getOwnerUid() == null && row.getCaseVersion() == 1;
            case "OWNER_ASSIGNED" -> Set.of("OPEN", "ACKNOWLEDGED", "IN_PROGRESS")
                    .contains(row.getPreviousStatus()) && row.getPreviousStatus().equals(row.getStatus())
                    && row.getOwnerUid() != null && row.getOwnerUid() > 0;
            case "OWNER_ACKNOWLEDGED" -> "OPEN".equals(row.getPreviousStatus())
                    && "ACKNOWLEDGED".equals(row.getStatus()) && row.getOwnerUid() == null;
            case "PLAN_RECORDED" -> "ACKNOWLEDGED".equals(row.getPreviousStatus())
                    && "IN_PROGRESS".equals(row.getStatus()) && row.getOwnerUid() == null;
            case "PROGRESS_RECORDED" -> "IN_PROGRESS".equals(row.getPreviousStatus())
                    && "IN_PROGRESS".equals(row.getStatus()) && row.getOwnerUid() == null;
            case "RESOLUTION_SUBMITTED" -> "IN_PROGRESS".equals(row.getPreviousStatus())
                    && "RESOLVED".equals(row.getStatus()) && row.getOwnerUid() == null;
            case "CASE_CLOSED" -> "RESOLVED".equals(row.getPreviousStatus())
                    && "CLOSED".equals(row.getStatus()) && row.getOwnerUid() == null;
            default -> false;
        };
    }

    private static boolean canCloseByDueState(
            ChannelQualityReviewRiskCaseRow riskCase, String dueState) {
        return !"DUE_STATE".equals(riskCase.getTriggerType())
                || (!"DUE_SOON".equals(dueState) && !"OVERDUE".equals(dueState));
    }

    private static int compareQueueOrder(QueueOrder left, QueueOrder right) {
        int priority = Integer.compare(left.priority(), right.priority());
        if (priority != 0) {
            return priority;
        }
        int updateTime = right.updateTime().compareTo(left.updateTime());
        if (updateTime != 0) {
            return updateTime;
        }
        int rowId = right.rowId().compareTo(left.rowId());
        if (rowId != 0) {
            return rowId;
        }
        return left.source().compareTo(right.source());
    }

    private static String encodeCursor(QueueOrder cursor) {
        String raw = cursor.priority() + "|" + cursor.updateTime().toInstant(ZoneOffset.UTC) + "|"
                + cursor.rowId() + "|" + cursor.source();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static QueueCursor decodeCursor(String rawCursor) {
        if (!StringUtils.hasText(rawCursor)) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(rawCursor), StandardCharsets.UTF_8);
            String[] values = raw.split("\\|", -1);
            if (values.length != 4) {
                throw new IllegalArgumentException();
            }
            int priority = Integer.parseInt(values[0]);
            LocalDateTime updateTime = LocalDateTime.ofInstant(Instant.parse(values[1]), ZoneOffset.UTC);
            long rowId = Long.parseLong(values[2]);
            if (priority < 1 || priority > 4 || rowId <= 0 || !QUEUE_SOURCES.contains(values[3])) {
                throw new IllegalArgumentException();
            }
            return new QueueCursor(priority, updateTime, rowId, values[3]);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static String encodeEventCursor(long eventId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Long.toString(eventId).getBytes(StandardCharsets.UTF_8));
    }

    private static long decodeEventCursor(String rawCursor) {
        if (!StringUtils.hasText(rawCursor)) {
            return 0L;
        }
        try {
            String value = new String(Base64.getUrlDecoder().decode(rawCursor), StandardCharsets.UTF_8);
            long eventId = Long.parseLong(value);
            if (eventId <= 0) {
                throw new IllegalArgumentException();
            }
            return eventId;
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private void audit(Long operatorUid, long caseId, String action, Map<String, Object> after) {
        adminAuditService.recordRequired(
                operatorUid,
                action,
                "CHANNEL_QUALITY_REVIEW_RISK_CASE",
                caseId,
                Map.of(),
                after,
                "channel quality review risk case updated");
    }

    private static void requireCoordinationVersion(
            Integer expectedVersion, ChannelQualityReviewBatchCoordinationRow batch) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (!expectedVersion.equals(batch.getCoordinationVersion())) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private static void requireCaseVersion(
            Integer expectedVersion, ChannelQualityReviewRiskCaseRow riskCase) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (!expectedVersion.equals(riskCase.getCaseVersion())) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private static String optionalEnum(String value, Set<String> allowed, String defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static String required(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String normalized = value.trim();
        if (normalized.length() < 2 || normalized.length() > 500) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static int pageSize(Integer size) {
        int result = size == null ? DEFAULT_PAGE_SIZE : size;
        if (result < 1 || result > MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return result;
    }

    private static int requireDomain(Integer domain) {
        if (domain == null || domain < 1 || domain > 5) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return domain;
    }

    private static long requireId(Long value) {
        if (value == null || value <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static long requireNonNegative(Long value) {
        if (value == null || value < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static void requireOperator(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private record Trigger(String triggerType, Long riskEventId, String riskCode, String dueState) {
    }

    private record QueueOrder(int priority, LocalDateTime updateTime, Long rowId, String source) {
    }

    private record QueueCursor(int priority, LocalDateTime updateTime, long rowId, String source) {
    }
}
