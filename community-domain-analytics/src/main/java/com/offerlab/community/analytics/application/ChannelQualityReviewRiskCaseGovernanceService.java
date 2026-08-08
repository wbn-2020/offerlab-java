package com.offerlab.community.analytics.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseActionReferenceDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseCloseCheckDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseClosePreviewDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseCloseSnapshotDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseCloseSnapshotReadDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseCloseCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseEvidenceEntryDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.ActionReferenceCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.ClosePreviewCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.EvidenceEntryCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.RecurrenceLinkCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.ResolutionRevisionCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.RetrospectiveAssignOwnerCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.RetrospectiveCompleteCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.RetrospectiveFindingCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.RetrospectiveInitializeCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.RetrospectiveStartCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCursorPageDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceMilestoneDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseRecurrenceLinkDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseResolutionRevisionDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseRetrospectiveDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseRetrospectiveEventDTO;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchCoordinationRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchMapper;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchTaskStatusRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseActionReferenceRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseCloseCheckRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseCloseSnapshotRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseEvidenceEntryRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseEventRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseGovernanceCaseRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseGovernanceMapper;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseGovernanceMilestoneRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseGovernanceRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseRecurrenceLinkRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseResolutionRevisionRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseResolutionRootCauseRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseRetrospectiveEventRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseRetrospectiveRow;
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
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ChannelQualityReviewRiskCaseGovernanceService {

    private static final String SOURCE_TYPE = "CHANNEL_HEALTH";
    private static final String MIGRATION =
            "db/migration/20260807_channel_quality_review_risk_case_evidence_retrospective.sql";
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 20;
    private static final int MAX_SELECTED_ITEMS = 20;
    private static final int MAX_BATCH_TASKS = 20;
    private static final String FACT_CONTRACT_VERSION = "V41_FACT_V1";
    private static final Set<String> CASE_STATUSES =
            Set.of("OPEN", "ACKNOWLEDGED", "IN_PROGRESS", "RESOLVED", "CLOSED");
    private static final Set<String> OUTCOME_TYPES = Set.of(
            "RECOVERY_CONFIRMED", "PARTIAL_RECOVERY", "RISK_CONTAINED",
            "FALSE_POSITIVE_CONFIRMED", "RISK_ACCEPTED");
    private static final Set<String> RECOVERY_STATES =
            Set.of("NOT_APPLICABLE", "NONE", "PARTIAL", "FULL");
    private static final Set<String> RISK_LEVELS = Set.of("NONE", "LOW", "MEDIUM", "HIGH");
    private static final Set<String> ROOT_CAUSES = Set.of(
            "PROCESS_GAP", "CONTENT_DEFECT", "CAPACITY_CONSTRAINT", "POLICY_AMBIGUITY",
            "TOOLING_FAILURE", "COMMUNICATION_GAP", "DATA_QUALITY", "EXTERNAL_DEPENDENCY",
            "UNKNOWN");
    private static final Set<String> REFERENCE_TYPES = Set.of(
            "V39_COORDINATION_EVENT", "V40_CASE_EVENT", "MAINTENANCE_TASK",
            "CONTENT_REVISION", "GOVERNANCE_DECISION", "EXTERNAL_TICKET");
    private static final Set<String> EVIDENCE_TYPES = Set.of(
            "CONTENT_STATE_OBSERVATION", "QUALITY_RECHECK", "TASK_DELIVERY_RECEIPT",
            "COORDINATION_CONFIRMATION", "POLICY_DECISION", "EXTERNAL_CONFIRMATION");
    private static final Set<String> ASSERTION_TYPES = Set.of(
            "SUPPORTS_RECOVERY", "REFUTES_RECOVERY", "SUPPORTS_PARTIAL_RECOVERY",
            "SUPPORTS_CONTAINMENT", "SUPPORTS_FALSE_POSITIVE",
            "SUPPORTS_RISK_ACCEPTANCE", "UNCERTAIN");
    private static final Set<String> EVIDENCE_SUBJECT_TYPES = Set.of(
            "RISK_CASE", "CONTENT", "MAINTENANCE_TASK", "BATCH",
            "GOVERNANCE_DECISION", "EXTERNAL_TICKET");
    private static final Set<String> EVIDENCE_SOURCE_TYPES = Set.of(
            "CONTENT_REVISION", "QUALITY_RECHECK", "DATA_VALIDATION",
            "MAINTENANCE_TASK", "V39_COORDINATION_EVENT", "GOVERNANCE_DECISION",
            "EXTERNAL_CONFIRMATION");
    private static final Set<String> RETROSPECTIVE_STATUSES =
            Set.of("PENDING", "IN_PROGRESS", "COMPLETED");
    private static final Set<String> LEARNING_CATEGORIES = Set.of(
            "PROCESS", "QUALITY", "CAPACITY", "POLICY", "TOOLING", "COMMUNICATION",
            "DATA", "EXTERNAL", "OTHER");
    private static final Set<String> RECURRENCE_TYPES = Set.of(
            "SAME_ROOT_CAUSE", "SAME_CHANNEL_PATTERN", "SAME_BATCH_PATTERN", "MANUAL_RELATED");
    private static final Set<String> FACT_CODES = Set.of(
            "OWNER_ASSIGNED", "OWNER_ACKNOWLEDGED", "PLAN_RECORDED",
            "CLOSE_SNAPSHOT_GENERATED", "RETROSPECTIVE_PENDING",
            "RETROSPECTIVE_OWNER_ASSIGNED", "RETROSPECTIVE_COMPLETED");
    private static final Set<String> TASK_STATUSES =
            Set.of("OPEN", "CLAIMED", "SUBMITTED", "COMPLETED", "CLOSED");
    private static final Set<String> INTERNAL_CLOSE_CHECK_CODES = Set.of(
            "CASE_STATUS_RESOLVED", "CASE_BATCH_VERSION_MATCH", "LATEST_RESOLUTION_REVISION",
            "ACTION_REFERENCE_PRESENT", "EVIDENCE_SELECTION_VALID", "OUTCOME_EVIDENCE_MATCH",
            "TASK_STATUS_NOT_RECOVERY_PROOF", "DUE_TRIGGER_CLEARED",
            "NEGATIVE_EVIDENCE_HANDLED", "RETROSPECTIVE_OWNER_ELIGIBLE", "AUDIT_WRITABLE");
    private static final Pattern SENSITIVE_TEXT = Pattern.compile(
            "(?i)(authorization\\s*:|bearer\\s+|api[_-]?key\\s*[=:]|password\\s*[=:]|secret\\s*[=:])");

    private final ChannelQualityReviewRiskCaseGovernanceMapper governanceMapper;
    private final ChannelQualityReviewBatchMapper batchMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final AdminAuditService adminAuditService;
    private final AdminPermissionService adminPermissionService;
    private final DomainModeratorService domainModeratorService;
    private final ObjectMapper objectMapper;
    private final Clock analyticsClock;
    private final List<ChannelQualityRiskCaseCloseCheckProvider> closeCheckProviders;

    /**
     * Internal V40 integration point. It is idempotent and intentionally has no audit record of its own:
     * the enclosing V40 lifecycle transaction already writes the user-visible audit event.
     */
    @Transactional
    public void initializeCaseGovernance(Long caseId, Long batchId, Integer domain) {
        requireTables();
        long safeCaseId = requireId(caseId);
        long safeBatchId = requireId(batchId);
        int safeDomain = requireDomain(domain);
        ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase =
                requireCase(governanceMapper.selectCaseById(safeCaseId));
        if (!Long.valueOf(safeBatchId).equals(riskCase.getBatchId())
                || !Integer.valueOf(safeDomain).equals(riskCase.getDomain())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        ChannelQualityReviewRiskCaseGovernanceRow existing =
                governanceMapper.selectGovernance(safeCaseId);
        if (existing != null) {
            requireGovernance(existing, riskCase);
            return;
        }
        try {
            if (governanceMapper.insertGovernance(safeCaseId, safeBatchId, safeDomain) != 1) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
        } catch (DuplicateKeyException ex) {
            ChannelQualityReviewRiskCaseGovernanceRow concurrent =
                    governanceMapper.selectGovernance(safeCaseId);
            if (concurrent == null) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            requireGovernance(concurrent, riskCase);
        }
    }

    /**
     * Internal V40 integration point. The caller owns the V40 case lock and writes the lifecycle event in
     * the same transaction; this method only appends the matching V41 stable fact.
     */
    @Transactional
    public void appendV40LifecycleFact(
            ChannelQualityReviewRiskCaseRow lockedCase,
            ChannelQualityReviewRiskCaseV40LifecycleEvent lifecycleEvent) {
        requireTables();
        requireV40Case(lockedCase);
        V40LifecycleFact event = requireV40LifecycleEvent(lifecycleEvent, lockedCase);
        String factCode = switch (event.eventType()) {
            case "OWNER_ASSIGNED" -> "OWNER_ASSIGNED";
            case "OWNER_ACKNOWLEDGED" -> "OWNER_ACKNOWLEDGED";
            case "PLAN_RECORDED" -> "PLAN_RECORDED";
            default -> null;
        };
        if (factCode == null) {
            return;
        }
        initializeCaseGovernance(lockedCase.getId(), lockedCase.getBatchId(), lockedCase.getDomain());
        ChannelQualityReviewRiskCaseGovernanceRow governance =
                requireGovernance(governanceMapper.lockGovernance(lockedCase.getId()), lockedCase);
        if (governanceMapper.selectGovernanceMilestoneBySource(
                "V40_CASE_EVENT", event.eventId(), factCode) != null) {
            return;
        }
        long ownerUid;
        int epoch;
        String requiredAction;
        if ("OWNER_ASSIGNED".equals(factCode)) {
            ownerUid = requireId(event.assignedOwnerUid());
            epoch = governanceMapper.maxResponsibilityEpoch(lockedCase.getId(), "CASE_OWNER") + 1;
            requiredAction = switch (event.status()) {
                case "OPEN" -> "ACKNOWLEDGE_CASE";
                case "ACKNOWLEDGED" -> "RECORD_PLAN";
                default -> "NONE";
            };
        } else {
            ownerUid = requireId(lockedCase.getOwnerUid());
            epoch = governanceMapper.maxResponsibilityEpoch(lockedCase.getId(), "CASE_OWNER");
            if (epoch < 1) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            requiredAction = "NONE";
        }
        insertFact(
                lockedCase.getId(),
                lockedCase.getBatchId(),
                lockedCase.getDomain(),
                factCode,
                toLocal(event.occurredAt()),
                "V40_CASE_EVENT",
                event.eventId(),
                ownerUid,
                "CASE_OWNER",
                epoch,
                event.status(),
                requiredAction,
                null);
    }

    @Transactional(readOnly = true)
    public ChannelQualityReviewRiskCaseGovernanceDTO governance(Long caseId, Long operatorUid) {
        requireTables();
        long safeCaseId = requireId(caseId);
        requireOperator(operatorUid);
        ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase =
                requireCase(governanceMapper.selectCaseById(safeCaseId));
        ChannelQualityReviewBatchCoordinationRow batch =
                requireBatch(batchMapper.selectCoordinationById(riskCase.getBatchId()));
        requireCaseBatch(riskCase, batch);
        requireModerate(operatorUid, riskCase.getDomain());
        ChannelQualityReviewRiskCaseGovernanceRow governance =
                requireGovernance(governanceMapper.selectGovernance(safeCaseId), riskCase);
        ChannelQualityReviewRiskCaseResolutionRevisionRow current = governance.getCurrentResolutionRevisionId() == null
                ? null
                : requireResolution(governanceMapper.selectResolutionRevision(
                        safeCaseId, governance.getCurrentResolutionRevisionId()));
        ChannelQualityReviewRiskCaseRetrospectiveRow retrospective =
                governanceMapper.selectRetrospective(safeCaseId);
        if (retrospective != null) {
            requireRetrospective(retrospective, riskCase);
        }
        int actionCount = governanceMapper.countActionReferences(safeCaseId);
        int evidenceCount = governanceMapper.countEvidenceEntries(safeCaseId);
        if (actionCount < 0 || evidenceCount < 0) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        boolean moderator = canModerate(operatorUid, riskCase.getDomain());
        boolean active = !"CLOSED".equals(riskCase.getStatus());
        boolean resolved = "RESOLVED".equals(riskCase.getStatus());
        boolean legacyClosed = legacyClosed(riskCase);
        return ChannelQualityReviewRiskCaseGovernanceDTO.builder()
                .caseId(riskCase.getId())
                .batchId(riskCase.getBatchId())
                .domain(riskCase.getDomain())
                .caseStatus(riskCase.getStatus())
                .caseVersion(riskCase.getCaseVersion())
                .coordinationVersion(batch.getCoordinationVersion())
                .governanceVersion(governance.getGovernanceVersion())
                .governanceFactVersion(governance.getGovernanceVersion())
                .governanceSnapshotEtag(governanceEtag(
                        riskCase, batch, governance, actionCount, evidenceCount, retrospective))
                .currentResolutionRevision(toResolutionSummary(current))
                .actionReferenceCount(actionCount)
                .evidenceCount(evidenceCount)
                .closeSnapshotId(riskCase.getV41CloseSnapshotId())
                .legacyClosedWithoutSnapshot(legacyClosed)
                .retrospective(toRetrospectiveSummary(retrospective))
                .milestoneFactVersion(FACT_CONTRACT_VERSION)
                .canAddResolutionRevision(moderator && active)
                .canAddActionReference(moderator && active)
                .canAddEvidence(moderator && active)
                .canPreviewClose(moderator && resolved)
                .canClose(moderator && resolved)
                .canInitializeRetrospective(moderator && legacyClosed && retrospective == null)
                .canManageRetrospective(moderator && retrospective != null
                        && !"COMPLETED".equals(retrospective.getStatus()))
                .canLinkRecurrence(moderator && active)
                .build();
    }

    @Transactional(readOnly = true)
    public ChannelQualityReviewRiskCaseGovernanceCursorPageDTO<ChannelQualityReviewRiskCaseGovernanceMilestoneDTO>
    milestones(Long caseId, String cursor, Integer size, Long operatorUid) {
        requireTables();
        ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase =
                readCaseForModerator(caseId, operatorUid);
        long safeCursor = decodeCursor(cursor);
        int pageSize = pageSize(size);
        List<ChannelQualityReviewRiskCaseGovernanceMilestoneRow> rows =
                governanceMapper.listGovernanceMilestones(riskCase.getId(), safeCursor, pageSize + 1);
        return cursorPage(rows, pageSize,
                ChannelQualityReviewRiskCaseGovernanceService::requireMilestone, this::toMilestone);
    }

    @Transactional(readOnly = true)
    public ChannelQualityReviewRiskCaseGovernanceCursorPageDTO<ChannelQualityReviewRiskCaseResolutionRevisionDTO>
    resolutionRevisions(Long caseId, String cursor, Integer size, Long operatorUid) {
        requireTables();
        ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase =
                readCaseForModerator(caseId, operatorUid);
        List<ChannelQualityReviewRiskCaseResolutionRevisionRow> rows =
                governanceMapper.listResolutionRevisions(riskCase.getId(), decodeCursor(cursor), pageSize(size) + 1);
        return cursorPage(rows, pageSize(size),
                ChannelQualityReviewRiskCaseGovernanceService::requireResolution,
                row -> toResolution(row, listRootCauses(riskCase.getId(), row.getId())));
    }

    @Transactional
    public ChannelQualityReviewRiskCaseResolutionRevisionDTO addResolutionRevision(
            Long caseId, ResolutionRevisionCmd cmd, Long operatorUid) {
        requireTables();
        ResolutionRequest request = requireResolutionRequest(cmd);
        CaseAggregate aggregate = lockAggregate(caseId, operatorUid);
        ChannelQualityReviewRiskCaseResolutionRevisionRow existing =
                governanceMapper.selectResolutionRevisionByCommand(
                        aggregate.riskCase().getId(), request.commandId());
        if (existing != null) {
            if (!request.commandFingerprint().equals(existing.getCommandFingerprint())) {
                throw new BizException(ErrorCode.DUPLICATE_OPERATION);
            }
            return toResolution(existing, listRootCauses(aggregate.riskCase().getId(), existing.getId()));
        }
        requireActiveGovernanceWrite(aggregate, request.expectedCaseVersion(), request.expectedGovernanceVersion());
        int revisionNo = governanceMapper.maxResolutionRevisionNo(aggregate.riskCase().getId()) + 1;
        long revisionId = idGenerator.nextId();
        try {
            requireWrite(governanceMapper.insertResolutionRevision(
                    revisionId,
                    aggregate.riskCase().getId(),
                    revisionNo,
                    request.outcomeType(),
                    request.contentRecoveryState(),
                    request.recoveryScope(),
                    request.residualRiskLevel(),
                    request.summary(),
                    operatorUid,
                    request.commandId(),
                    request.commandFingerprint()));
            int sequence = 1;
            for (RootCauseRequest rootCause : request.rootCauses()) {
                requireWrite(governanceMapper.insertRootCause(
                        idGenerator.nextId(),
                        aggregate.riskCase().getId(),
                        revisionId,
                        rootCause.role(),
                        rootCause.category(),
                        "PRIMARY".equals(rootCause.role()) ? 0 : sequence++,
                        rootCause.note()));
            }
            requireWrite(governanceMapper.advanceGovernanceResolution(
                    aggregate.riskCase().getId(),
                    aggregate.governance().getGovernanceVersion(),
                    revisionId));
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        audit(operatorUid, aggregate.riskCase().getId(),
                "CHANNEL_QUALITY_RISK_CASE_RESOLUTION_REVISION_ADD",
                Map.of("revisionId", revisionId, "revisionNo", revisionNo, "outcomeType", request.outcomeType()));
        ChannelQualityReviewRiskCaseResolutionRevisionRow created =
                requireResolution(governanceMapper.selectResolutionRevision(aggregate.riskCase().getId(), revisionId));
        return toResolution(created, listRootCauses(aggregate.riskCase().getId(), revisionId));
    }

    @Transactional(readOnly = true)
    public ChannelQualityReviewRiskCaseGovernanceCursorPageDTO<ChannelQualityReviewRiskCaseActionReferenceDTO>
    actionReferences(Long caseId, String cursor, Integer size, Long operatorUid) {
        requireTables();
        ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase =
                readCaseForModerator(caseId, operatorUid);
        int pageSize = pageSize(size);
        List<ChannelQualityReviewRiskCaseActionReferenceRow> rows =
                governanceMapper.listActionReferences(riskCase.getId(), decodeCursor(cursor), pageSize + 1);
        return cursorPage(rows, pageSize,
                ChannelQualityReviewRiskCaseGovernanceService::requireActionReference,
                this::toActionReference);
    }

    @Transactional
    public ChannelQualityReviewRiskCaseActionReferenceDTO addActionReference(
            Long caseId, ActionReferenceCmd cmd, Long operatorUid) {
        requireTables();
        ActionRequest request = requireActionRequest(cmd);
        CaseAggregate aggregate = lockAggregate(caseId, operatorUid);
        ChannelQualityReviewRiskCaseActionReferenceRow existing =
                governanceMapper.selectActionReferenceByCommand(
                        aggregate.riskCase().getId(), request.commandId());
        if (existing != null) {
            if (!request.commandFingerprint().equals(existing.getCommandFingerprint())) {
                throw new BizException(ErrorCode.DUPLICATE_OPERATION);
            }
            return toActionReference(existing);
        }
        requireActiveGovernanceWrite(aggregate, request.expectedCaseVersion(), request.expectedGovernanceVersion());
        verifyActionReference(aggregate.riskCase(), request);
        if (request.correctionOfReferenceId() != null) {
            requireActionReference(governanceMapper.selectActionReference(
                    aggregate.riskCase().getId(), request.correctionOfReferenceId()));
        }
        long id = idGenerator.nextId();
        try {
            requireWrite(governanceMapper.insertActionReference(
                    id,
                    aggregate.riskCase().getId(),
                    request.referenceType(),
                    request.referenceKey(),
                    request.observedVersion(),
                    toLocal(request.occurredAt()),
                    request.summary(),
                    request.referenceFingerprint(),
                    request.correctionOfReferenceId(),
                    operatorUid,
                    request.commandId(),
                    request.commandFingerprint()));
            requireWrite(governanceMapper.advanceGovernanceVersion(
                    aggregate.riskCase().getId(), aggregate.governance().getGovernanceVersion()));
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        audit(operatorUid, aggregate.riskCase().getId(),
                "CHANNEL_QUALITY_RISK_CASE_ACTION_REFERENCE_ADD",
                Map.of("actionReferenceId", id, "referenceType", request.referenceType()));
        return toActionReference(requireActionReference(
                governanceMapper.selectActionReference(aggregate.riskCase().getId(), id)));
    }

    @Transactional(readOnly = true)
    public ChannelQualityReviewRiskCaseGovernanceCursorPageDTO<ChannelQualityReviewRiskCaseEvidenceEntryDTO>
    evidence(Long caseId, String cursor, Integer size, Long operatorUid) {
        requireTables();
        ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase =
                readCaseForModerator(caseId, operatorUid);
        int pageSize = pageSize(size);
        List<ChannelQualityReviewRiskCaseEvidenceEntryRow> rows =
                governanceMapper.listEvidenceEntries(riskCase.getId(), decodeCursor(cursor), pageSize + 1);
        return cursorPage(rows, pageSize,
                ChannelQualityReviewRiskCaseGovernanceService::requireEvidenceEntry,
                this::toEvidenceEntry);
    }

    @Transactional
    public ChannelQualityReviewRiskCaseEvidenceEntryDTO addEvidence(
            Long caseId, EvidenceEntryCmd cmd, Long operatorUid) {
        requireTables();
        EvidenceRequest request = requireEvidenceRequest(cmd);
        CaseAggregate aggregate = lockAggregate(caseId, operatorUid);
        ChannelQualityReviewRiskCaseEvidenceEntryRow existing =
                governanceMapper.selectEvidenceEntryByCommand(
                        aggregate.riskCase().getId(), request.commandId());
        if (existing != null) {
            if (!request.commandFingerprint().equals(existing.getCommandFingerprint())) {
                throw new BizException(ErrorCode.DUPLICATE_OPERATION);
            }
            return toEvidenceEntry(existing);
        }
        requireActiveGovernanceWrite(aggregate, request.expectedCaseVersion(), request.expectedGovernanceVersion());
        verifyEvidenceSource(aggregate.riskCase(), request);
        if (request.correctionOfEntryId() != null) {
            requireEvidenceEntry(governanceMapper.selectEvidenceEntry(
                    aggregate.riskCase().getId(), request.correctionOfEntryId()));
        }
        long id = idGenerator.nextId();
        try {
            requireWrite(governanceMapper.insertEvidenceEntry(
                    id,
                    aggregate.riskCase().getId(),
                    request.evidenceType(),
                    request.assertionType(),
                    request.subjectType(),
                    request.subjectRef(),
                    request.sourceType(),
                    request.sourceRef(),
                    request.sourceVersion(),
                    toLocal(request.observedAt()),
                    request.summary(),
                    request.evidenceDigest(),
                    request.correctionOfEntryId(),
                    operatorUid,
                    request.commandId(),
                    request.commandFingerprint()));
            requireWrite(governanceMapper.advanceGovernanceVersion(
                    aggregate.riskCase().getId(), aggregate.governance().getGovernanceVersion()));
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        audit(operatorUid, aggregate.riskCase().getId(),
                "CHANNEL_QUALITY_RISK_CASE_EVIDENCE_ADD",
                Map.of("evidenceEntryId", id, "evidenceType", request.evidenceType()));
        return toEvidenceEntry(requireEvidenceEntry(
                governanceMapper.selectEvidenceEntry(aggregate.riskCase().getId(), id)));
    }

    @Transactional(readOnly = true)
    public ChannelQualityReviewRiskCaseClosePreviewDTO closePreview(
            Long caseId, ClosePreviewCmd cmd, Long operatorUid) {
        requireTables();
        CloseRequest request = requireClosePreviewRequest(cmd);
        CaseAggregate aggregate = readAggregate(caseId, operatorUid);
        CloseSelection selection = buildCloseSelection(aggregate, request);
        return toClosePreview(aggregate, selection.checks());
    }

    @Transactional
    public ChannelQualityReviewRiskCaseCloseSnapshotDTO closeWithSnapshot(
            Long caseId, ChannelQualityReviewRiskCaseCloseCmd cmd, Long operatorUid) {
        requireTables();
        CloseRequest request = requireCloseRequest(cmd);
        CaseAggregate aggregate = lockAggregate(caseId, operatorUid);
        ChannelQualityReviewRiskCaseCloseSnapshotRow existing =
                governanceMapper.selectCloseSnapshotByCommand(
                        aggregate.riskCase().getId(), request.commandId());
        if (existing != null) {
            if (!request.commandFingerprint().equals(existing.getCommandFingerprint())) {
                throw new BizException(ErrorCode.DUPLICATE_OPERATION);
            }
            return toCloseSnapshot(requireSnapshot(existing));
        }
        CloseSelection selection = buildCloseSelection(aggregate, request);
        requireReadyToClose(selection.checks());
        long snapshotId = idGenerator.nextId();
        int closedCaseVersion = aggregate.riskCase().getCaseVersion() + 1;
        String payload = closeSnapshotPayload(aggregate, selection, closedCaseVersion);
        String snapshotDigest = sha256(payload);
        try {
            requireWrite(governanceMapper.insertCloseSnapshot(
                    snapshotId,
                    aggregate.riskCase().getId(),
                    aggregate.riskCase().getBatchId(),
                    aggregate.riskCase().getDomain(),
                    selection.resolution().getId(),
                    closedCaseVersion,
                    aggregate.batch().getCoordinationVersion(),
                    aggregate.governance().getGovernanceVersion(),
                    selection.resolution().getOutcomeType(),
                    selection.resolution().getContentRecoveryState(),
                    primaryRootCause(selection.rootCauses()).getCategory(),
                    selection.resolution().getResidualRiskLevel(),
                    selection.actionReferences().size(),
                    selection.evidenceEntries().size(),
                    1,
                    payload,
                    snapshotDigest,
                    operatorUid,
                    request.commandId(),
                    request.commandFingerprint()));
            for (CheckedCloseResult check : selection.checks()) {
                if (isBlockingFailure(check)) {
                    throw new BizException(ErrorCode.DEPENDENCY_ERROR);
                }
                requireWrite(governanceMapper.insertCloseCheck(
                        idGenerator.nextId(),
                        snapshotId,
                        aggregate.riskCase().getId(),
                        check.code(),
                        check.version(),
                        check.result().requirementLevel(),
                        check.result().result(),
                        check.result().reasonCode(),
                        check.result().summary()));
            }
            requireWrite(governanceMapper.closeCaseWithSnapshot(
                    aggregate.riskCase().getId(),
                    aggregate.riskCase().getCaseVersion(),
                    snapshotId));
            long closeEventId = idGenerator.nextId();
            requireWrite(governanceMapper.insertClosedCaseEvent(
                    closeEventId,
                    aggregate.riskCase().getId(),
                    aggregate.riskCase().getBatchId(),
                    operatorUid,
                    aggregate.batch().getCoordinationVersion(),
                    closedCaseVersion,
                    request.note()));
            long retrospectiveId = idGenerator.nextId();
            requireWrite(governanceMapper.insertRetrospective(
                    retrospectiveId,
                    aggregate.riskCase().getId(),
                    snapshotId,
                    aggregate.riskCase().getDomain(),
                    request.retrospectiveOwnerUid(),
                    0));
            long retrospectiveEventId = idGenerator.nextId();
            requireWrite(governanceMapper.insertRetrospectiveEvent(
                    retrospectiveEventId,
                    retrospectiveId,
                    aggregate.riskCase().getId(),
                    operatorUid,
                    "RETROSPECTIVE_CREATED",
                    null,
                    "PENDING",
                    request.retrospectiveOwnerUid(),
                    null,
                    null,
                    null,
                    1,
                    request.commandId(),
                    request.commandFingerprint(),
                    request.note()));
            insertFact(
                    aggregate.riskCase().getId(), aggregate.riskCase().getBatchId(),
                    aggregate.riskCase().getDomain(), "CLOSE_SNAPSHOT_GENERATED",
                    toLocal(analyticsClock.instant()), "V41_CLOSE_SNAPSHOT", snapshotId,
                    null, "CASE", null, null, "NONE", null);
            insertFact(
                    aggregate.riskCase().getId(), aggregate.riskCase().getBatchId(),
                    aggregate.riskCase().getDomain(), "RETROSPECTIVE_PENDING",
                    toLocal(analyticsClock.instant()), "V41_RETROSPECTIVE", retrospectiveEventId,
                    request.retrospectiveOwnerUid(), "RETROSPECTIVE", 1, null, "NONE", retrospectiveId);
            insertFact(
                    aggregate.riskCase().getId(), aggregate.riskCase().getBatchId(),
                    aggregate.riskCase().getDomain(), "RETROSPECTIVE_OWNER_ASSIGNED",
                    toLocal(analyticsClock.instant()), "V41_RETROSPECTIVE", retrospectiveEventId,
                    request.retrospectiveOwnerUid(), "RETROSPECTIVE", 1, null, "NONE", retrospectiveId);
        } catch (DuplicateKeyException ex) {
            ChannelQualityReviewRiskCaseCloseSnapshotRow concurrent =
                    governanceMapper.selectCloseSnapshotByCommand(
                            aggregate.riskCase().getId(), request.commandId());
            if (concurrent != null && request.commandFingerprint().equals(concurrent.getCommandFingerprint())) {
                return toCloseSnapshot(requireSnapshot(concurrent));
            }
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        audit(operatorUid, aggregate.riskCase().getId(),
                "CHANNEL_QUALITY_RISK_CASE_EVIDENCE_CLOSE",
                Map.of(
                        "snapshotId", snapshotId,
                        "resolutionRevisionId", selection.resolution().getId(),
                        "actionReferenceCount", selection.actionReferences().size(),
                        "evidenceCount", selection.evidenceEntries().size(),
                        "snapshotDigest", snapshotDigest));
        return toCloseSnapshot(requireSnapshot(governanceMapper.selectCloseSnapshotByCase(
                aggregate.riskCase().getId())));
    }

    @Transactional(readOnly = true)
    public ChannelQualityReviewRiskCaseCloseSnapshotReadDTO closeSnapshot(Long caseId, Long operatorUid) {
        requireTables();
        ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase =
                readCaseForModerator(caseId, operatorUid);
        if (legacyClosed(riskCase)) {
            return ChannelQualityReviewRiskCaseCloseSnapshotReadDTO.builder()
                    .caseId(riskCase.getId())
                    .legacyClosedWithoutSnapshot(true)
                    .snapshot(null)
                    .build();
        }
        ChannelQualityReviewRiskCaseCloseSnapshotRow snapshot =
                governanceMapper.selectCloseSnapshotByCase(riskCase.getId());
        if (snapshot == null) {
            return ChannelQualityReviewRiskCaseCloseSnapshotReadDTO.builder()
                    .caseId(riskCase.getId())
                    .legacyClosedWithoutSnapshot(false)
                    .snapshot(null)
                    .build();
        }
        return ChannelQualityReviewRiskCaseCloseSnapshotReadDTO.builder()
                .caseId(riskCase.getId())
                .legacyClosedWithoutSnapshot(false)
                .snapshot(toCloseSnapshot(requireSnapshot(snapshot)))
                .build();
    }

    @Transactional(readOnly = true)
    public ChannelQualityReviewRiskCaseRetrospectiveDTO retrospective(Long caseId, Long operatorUid) {
        requireTables();
        ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase =
                readCaseForModerator(caseId, operatorUid);
        ChannelQualityReviewRiskCaseRetrospectiveRow row =
                governanceMapper.selectRetrospective(riskCase.getId());
        return row == null ? null : toRetrospective(requireRetrospective(row, riskCase), operatorUid);
    }

    @Transactional
    public ChannelQualityReviewRiskCaseRetrospectiveDTO initializeRetrospective(
            Long caseId, RetrospectiveInitializeCmd cmd, Long operatorUid) {
        requireTables();
        RetrospectiveInitializeRequest request = requireRetrospectiveInitializeRequest(cmd);
        CaseAggregate aggregate = lockAggregate(caseId, operatorUid);
        if (!legacyClosed(aggregate.riskCase())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        if (governanceMapper.selectRetrospective(aggregate.riskCase().getId()) != null) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        requireEligibleOwner(request.ownerUid(), aggregate.riskCase().getDomain());
        long retrospectiveId = idGenerator.nextId();
        long eventId = idGenerator.nextId();
        try {
            requireWrite(governanceMapper.insertRetrospective(
                    retrospectiveId, aggregate.riskCase().getId(), null, aggregate.riskCase().getDomain(),
                    request.ownerUid(), 1));
            requireWrite(governanceMapper.insertRetrospectiveEvent(
                    eventId, retrospectiveId, aggregate.riskCase().getId(), operatorUid,
                    "RETROSPECTIVE_CREATED", null, "PENDING", request.ownerUid(), null, null, null,
                    1, request.commandId(), request.commandFingerprint(), request.note()));
            insertFact(
                    aggregate.riskCase().getId(), aggregate.riskCase().getBatchId(),
                    aggregate.riskCase().getDomain(), "RETROSPECTIVE_PENDING",
                    toLocal(analyticsClock.instant()), "V41_RETROSPECTIVE", eventId,
                    request.ownerUid(), "RETROSPECTIVE", 1, null, "NONE", retrospectiveId);
            insertFact(
                    aggregate.riskCase().getId(), aggregate.riskCase().getBatchId(),
                    aggregate.riskCase().getDomain(), "RETROSPECTIVE_OWNER_ASSIGNED",
                    toLocal(analyticsClock.instant()), "V41_RETROSPECTIVE", eventId,
                    request.ownerUid(), "RETROSPECTIVE", 1, null, "NONE", retrospectiveId);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        audit(operatorUid, aggregate.riskCase().getId(),
                "CHANNEL_QUALITY_RISK_CASE_RETROSPECTIVE_INITIALIZE",
                Map.of("retrospectiveId", retrospectiveId, "legacyBaseline", true));
        return toRetrospective(requireRetrospective(
                governanceMapper.selectRetrospective(aggregate.riskCase().getId()), aggregate.riskCase()), operatorUid);
    }

    @Transactional
    public ChannelQualityReviewRiskCaseRetrospectiveDTO assignRetrospectiveOwner(
            Long caseId, RetrospectiveAssignOwnerCmd cmd, Long operatorUid) {
        requireTables();
        RetrospectiveOwnerRequest request = requireRetrospectiveOwnerRequest(cmd);
        CaseAggregate aggregate = lockAggregate(caseId, operatorUid);
        ChannelQualityReviewRiskCaseRetrospectiveRow retrospective =
                requireRetrospective(governanceMapper.lockRetrospective(aggregate.riskCase().getId()), aggregate.riskCase());
        ChannelQualityReviewRiskCaseRetrospectiveEventRow existing =
                governanceMapper.selectRetrospectiveEventByCommand(retrospective.getId(), request.commandId());
        if (existing != null) {
            return replayRetrospective(existing, request.commandFingerprint(), retrospective, operatorUid);
        }
        requireRetrospectiveVersion(request.expectedRetrospectiveVersion(), retrospective);
        if (!Set.of("PENDING", "IN_PROGRESS").contains(retrospective.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        requireEligibleOwner(request.ownerUid(), aggregate.riskCase().getDomain());
        int newVersion = retrospective.getRetrospectiveVersion() + 1;
        long eventId = idGenerator.nextId();
        requireWrite(governanceMapper.advanceRetrospective(
                retrospective.getId(), retrospective.getRetrospectiveVersion(), retrospective.getStatus(),
                request.ownerUid(), retrospective.getStartedAt(), null));
        requireWrite(governanceMapper.insertRetrospectiveEvent(
                eventId, retrospective.getId(), aggregate.riskCase().getId(), operatorUid,
                "RETROSPECTIVE_OWNER_ASSIGNED", retrospective.getStatus(), retrospective.getStatus(),
                request.ownerUid(), null, null, null, newVersion,
                request.commandId(), request.commandFingerprint(), request.note()));
        int epoch = governanceMapper.maxResponsibilityEpoch(
                aggregate.riskCase().getId(), "RETROSPECTIVE") + 1;
        insertFact(
                aggregate.riskCase().getId(), aggregate.riskCase().getBatchId(), aggregate.riskCase().getDomain(),
                "RETROSPECTIVE_OWNER_ASSIGNED", toLocal(analyticsClock.instant()), "V41_RETROSPECTIVE", eventId,
                request.ownerUid(), "RETROSPECTIVE", epoch, null, "NONE", retrospective.getId());
        audit(operatorUid, aggregate.riskCase().getId(),
                "CHANNEL_QUALITY_RISK_CASE_RETROSPECTIVE_ASSIGN",
                Map.of("retrospectiveId", retrospective.getId(), "retrospectiveVersion", newVersion));
        return toRetrospective(requireRetrospective(
                governanceMapper.selectRetrospective(aggregate.riskCase().getId()), aggregate.riskCase()), operatorUid);
    }

    @Transactional
    public ChannelQualityReviewRiskCaseRetrospectiveDTO startRetrospective(
            Long caseId, RetrospectiveStartCmd cmd, Long operatorUid) {
        requireTables();
        RetrospectiveMutationRequest request = requireRetrospectiveStartRequest(cmd);
        return mutateRetrospective(caseId, request, operatorUid, "RETROSPECTIVE_STARTED",
                "PENDING", "IN_PROGRESS", null, null, null, false);
    }

    @Transactional
    public ChannelQualityReviewRiskCaseRetrospectiveDTO recordRetrospectiveFinding(
            Long caseId, RetrospectiveFindingCmd cmd, Long operatorUid) {
        requireTables();
        RetrospectiveFindingRequest request = requireRetrospectiveFindingRequest(cmd);
        return mutateRetrospective(caseId,
                new RetrospectiveMutationRequest(
                        request.expectedRetrospectiveVersion(), request.commandId(),
                        request.note(), request.commandFingerprint()),
                operatorUid, "RETROSPECTIVE_FINDING_RECORDED",
                "IN_PROGRESS", "IN_PROGRESS", request.learningCategory(),
                request.findingSummary(), request.preventionActionSummary(), false);
    }

    @Transactional
    public ChannelQualityReviewRiskCaseRetrospectiveDTO completeRetrospective(
            Long caseId, RetrospectiveCompleteCmd cmd, Long operatorUid) {
        requireTables();
        RetrospectiveMutationRequest request = requireRetrospectiveCompleteRequest(cmd);
        return mutateRetrospective(caseId, request, operatorUid, "RETROSPECTIVE_COMPLETED",
                "IN_PROGRESS", "COMPLETED", null, null, null, true);
    }

    @Transactional(readOnly = true)
    public ChannelQualityReviewRiskCaseGovernanceCursorPageDTO<ChannelQualityReviewRiskCaseRetrospectiveEventDTO>
    retrospectiveEvents(Long caseId, String cursor, Integer size, Long operatorUid) {
        requireTables();
        ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase =
                readCaseForModerator(caseId, operatorUid);
        ChannelQualityReviewRiskCaseRetrospectiveRow retrospective =
                requireRetrospective(governanceMapper.selectRetrospective(riskCase.getId()), riskCase);
        int pageSize = pageSize(size);
        List<ChannelQualityReviewRiskCaseRetrospectiveEventRow> rows =
                governanceMapper.listRetrospectiveEvents(retrospective.getId(), decodeCursor(cursor), pageSize + 1);
        return cursorPage(rows, pageSize,
                ChannelQualityReviewRiskCaseGovernanceService::requireRetrospectiveEvent,
                this::toRetrospectiveEvent);
    }

    @Transactional(readOnly = true)
    public ChannelQualityReviewRiskCaseGovernanceCursorPageDTO<ChannelQualityReviewRiskCaseRecurrenceLinkDTO>
    recurrenceLinks(Long caseId, String cursor, Integer size, Long operatorUid) {
        requireTables();
        ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase =
                readCaseForModerator(caseId, operatorUid);
        int pageSize = pageSize(size);
        List<ChannelQualityReviewRiskCaseRecurrenceLinkRow> rows =
                governanceMapper.listRecurrenceLinks(riskCase.getId(), decodeCursor(cursor), pageSize + 1);
        return cursorPage(rows, pageSize,
                ChannelQualityReviewRiskCaseGovernanceService::requireRecurrenceLink,
                this::toRecurrenceLink);
    }

    @Transactional
    public ChannelQualityReviewRiskCaseRecurrenceLinkDTO addRecurrenceLink(
            Long caseId, RecurrenceLinkCmd cmd, Long operatorUid) {
        requireTables();
        RecurrenceRequest request = requireRecurrenceRequest(cmd);
        CaseAggregate aggregate = lockAggregate(caseId, operatorUid);
        ChannelQualityReviewRiskCaseRecurrenceLinkRow existing =
                governanceMapper.selectRecurrenceLinkByCommand(
                        aggregate.riskCase().getId(), request.commandId());
        if (existing != null) {
            if (!request.commandFingerprint().equals(existing.getCommandFingerprint())) {
                throw new BizException(ErrorCode.DUPLICATE_OPERATION);
            }
            return toRecurrenceLink(requireRecurrenceLink(existing));
        }
        requireCaseVersion(request.expectedCurrentCaseVersion(), aggregate.riskCase());
        if ("CLOSED".equals(aggregate.riskCase().getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        ChannelQualityReviewRiskCaseGovernanceCaseRow previous =
                requireCase(governanceMapper.selectCaseById(request.previousCaseId()));
        if (!"CLOSED".equals(previous.getStatus())
                || !previous.getDomain().equals(aggregate.riskCase().getDomain())
                || !previous.getCreateTime().isBefore(aggregate.riskCase().getCreateTime())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        long id = idGenerator.nextId();
        try {
            requireWrite(governanceMapper.insertRecurrenceLink(
                    id, aggregate.riskCase().getId(), previous.getId(), aggregate.riskCase().getDomain(),
                    request.relationType(), request.rootCauseCategory(), request.note(), operatorUid,
                    request.commandId(), request.commandFingerprint()));
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        audit(operatorUid, aggregate.riskCase().getId(),
                "CHANNEL_QUALITY_RISK_CASE_RECURRENCE_LINK",
                Map.of("recurrenceLinkId", id, "previousCaseId", previous.getId()));
        return toRecurrenceLink(requireRecurrenceLink(
                governanceMapper.listRecurrenceLinks(aggregate.riskCase().getId(), id + 1, 1)
                        .stream().findFirst().orElse(null)));
    }

    private ChannelQualityReviewRiskCaseRetrospectiveDTO mutateRetrospective(
            Long caseId,
            RetrospectiveMutationRequest request,
            Long operatorUid,
            String eventType,
            String requiredStatus,
            String targetStatus,
            String learningCategory,
            String findingSummary,
            String preventionActionSummary,
            boolean appendCompletedFact) {
        CaseAggregate aggregate = lockAggregate(caseId, operatorUid);
        ChannelQualityReviewRiskCaseRetrospectiveRow retrospective =
                requireRetrospective(governanceMapper.lockRetrospective(aggregate.riskCase().getId()), aggregate.riskCase());
        ChannelQualityReviewRiskCaseRetrospectiveEventRow existing =
                governanceMapper.selectRetrospectiveEventByCommand(retrospective.getId(), request.commandId());
        if (existing != null) {
            return replayRetrospective(existing, request.commandFingerprint(), retrospective, operatorUid);
        }
        requireRetrospectiveVersion(request.expectedRetrospectiveVersion(), retrospective);
        if (!requiredStatus.equals(retrospective.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        int newVersion = retrospective.getRetrospectiveVersion() + 1;
        LocalDateTime now = toLocal(analyticsClock.instant());
        LocalDateTime startedAt = "IN_PROGRESS".equals(targetStatus) && retrospective.getStartedAt() == null
                ? now : retrospective.getStartedAt();
        LocalDateTime completedAt = "COMPLETED".equals(targetStatus) ? now : null;
        long eventId = idGenerator.nextId();
        requireWrite(governanceMapper.advanceRetrospective(
                retrospective.getId(), retrospective.getRetrospectiveVersion(), targetStatus,
                retrospective.getOwnerUid(), startedAt, completedAt));
        requireWrite(governanceMapper.insertRetrospectiveEvent(
                eventId, retrospective.getId(), aggregate.riskCase().getId(), operatorUid,
                eventType, retrospective.getStatus(), targetStatus, null,
                learningCategory, findingSummary, preventionActionSummary, newVersion,
                request.commandId(), request.commandFingerprint(), request.note()));
        if (appendCompletedFact) {
            int responsibilityEpoch = governanceMapper.maxResponsibilityEpoch(
                    aggregate.riskCase().getId(), "RETROSPECTIVE");
            if (responsibilityEpoch < 1) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            insertFact(
                    aggregate.riskCase().getId(), aggregate.riskCase().getBatchId(),
                    aggregate.riskCase().getDomain(), "RETROSPECTIVE_COMPLETED", now,
                    "V41_RETROSPECTIVE", eventId, retrospective.getOwnerUid(), "RETROSPECTIVE",
                    responsibilityEpoch, null, "NONE",
                    retrospective.getId());
        }
        audit(operatorUid, aggregate.riskCase().getId(),
                switch (eventType) {
                    case "RETROSPECTIVE_STARTED" -> "CHANNEL_QUALITY_RISK_CASE_RETROSPECTIVE_START";
                    case "RETROSPECTIVE_FINDING_RECORDED" -> "CHANNEL_QUALITY_RISK_CASE_RETROSPECTIVE_FINDING";
                    case "RETROSPECTIVE_COMPLETED" -> "CHANNEL_QUALITY_RISK_CASE_RETROSPECTIVE_COMPLETE";
                    default -> throw new BizException(ErrorCode.DEPENDENCY_ERROR);
                },
                Map.of("retrospectiveId", retrospective.getId(), "retrospectiveVersion", newVersion));
        return toRetrospective(requireRetrospective(
                governanceMapper.selectRetrospective(aggregate.riskCase().getId()), aggregate.riskCase()), operatorUid);
    }

    private CloseSelection buildCloseSelection(CaseAggregate aggregate, CloseRequest request) {
        ChannelQualityReviewRiskCaseResolutionRevisionRow resolution =
                governanceMapper.selectResolutionRevision(aggregate.riskCase().getId(), request.resolutionRevisionId());
        List<ChannelQualityReviewRiskCaseResolutionRootCauseRow> rootCauses =
                resolution == null ? List.of() : listRootCauses(aggregate.riskCase().getId(), resolution.getId());
        List<ChannelQualityReviewRiskCaseActionReferenceRow> actions =
                governanceMapper.listActionReferencesByIds(aggregate.riskCase().getId(), request.actionReferenceIds());
        List<ChannelQualityReviewRiskCaseEvidenceEntryRow> evidence =
                governanceMapper.listEvidenceEntriesByIds(aggregate.riskCase().getId(), request.evidenceEntryIds());
        String dueState = dueState(aggregate.batch());
        ChannelQualityRiskCaseCloseCheckContext context = closeCheckContext(
                aggregate, resolution, rootCauses, actions, evidence, request.retrospectiveOwnerUid(), dueState);
        List<CheckedCloseResult> checks = new ArrayList<>();
        checks.add(internalCheck("CASE_STATUS_RESOLVED", "RESOLVED".equals(aggregate.riskCase().getStatus()),
                "CASE_NOT_RESOLVED", "风险处置单必须处于已提交结论状态。"));
        boolean versionsMatch = request.expectedCaseVersion() == aggregate.riskCase().getCaseVersion()
                && request.expectedCoordinationVersion() == aggregate.batch().getCoordinationVersion()
                && request.expectedGovernanceVersion() == aggregate.governance().getGovernanceVersion();
        checks.add(internalCheck("CASE_BATCH_VERSION_MATCH", versionsMatch,
                "VERSION_MISMATCH", "处置单、批次或治理版本已发生变化。"));
        boolean latestResolution = resolution != null
                && resolution.getId().equals(aggregate.governance().getCurrentResolutionRevisionId())
                && validRootCauses(rootCauses);
        checks.add(internalCheck("LATEST_RESOLUTION_REVISION", latestResolution,
                "RESOLUTION_NOT_CURRENT", "必须选择当前最新且根因完整的结论修订。"));
        checks.add(internalCheck("ACTION_REFERENCE_PRESENT", validSelectedActions(actions, request.actionReferenceIds()),
                "ACTION_REFERENCE_INVALID", "关闭至少需要一条未被纠正的有效行动引用。"));
        checks.add(internalCheck("EVIDENCE_SELECTION_VALID", validSelectedEvidence(evidence, request.evidenceEntryIds()),
                "EVIDENCE_SELECTION_INVALID", "关闭证据必须属于当前处置单、唯一且为纠正链末端。"));
        checks.add(internalCheck("OUTCOME_EVIDENCE_MATCH", outcomeEvidenceMatches(resolution, evidence, actions),
                "OUTCOME_EVIDENCE_MISMATCH", "所选证据不足以支撑当前处置结论。"));
        checks.add(internalCheck("TASK_STATUS_NOT_RECOVERY_PROOF", hasNonTaskRecoveryEvidence(resolution, evidence),
                "TASK_STATUS_ONLY", "恢复结论不能仅由任务或批次状态支撑。"));
        checks.add(internalCheck("DUE_TRIGGER_CLEARED",
                !"DUE_STATE".equals(aggregate.riskCase().getTriggerType())
                        || !Set.of("DUE_SOON", "OVERDUE").contains(dueState),
                "DUE_TRIGGER_ACTIVE", "到期触发风险尚未解除。"));
        checks.add(internalCheck("NEGATIVE_EVIDENCE_HANDLED",
                noUncorrectedNegativeEvidence(resolution, evidence),
                "NEGATIVE_EVIDENCE_UNHANDLED", "存在反证恢复的证据，不能声明完全恢复。"));
        checks.add(internalCheck("RETROSPECTIVE_OWNER_ELIGIBLE",
                canModerate(request.retrospectiveOwnerUid(), aggregate.riskCase().getDomain()),
                "RETROSPECTIVE_OWNER_INELIGIBLE", "复盘负责人当前不具备频道治理资格。"));
        checks.add(internalCheck("AUDIT_WRITABLE", true,
                "AUDIT_REQUIRED", "关闭将在同一事务中写入管理员审计。"));
        checks.addAll(extensionChecks(context));
        checks.sort(Comparator.comparingInt(CheckedCloseResult::order).thenComparing(CheckedCloseResult::code));
        return new CloseSelection(resolution, rootCauses, actions, evidence, checks);
    }

    private List<CheckedCloseResult> extensionChecks(ChannelQualityRiskCaseCloseCheckContext context) {
        Set<String> codes = new HashSet<>(INTERNAL_CLOSE_CHECK_CODES);
        List<CheckedCloseResult> results = new ArrayList<>();
        List<ChannelQualityRiskCaseCloseCheckProvider> providers =
                closeCheckProviders == null ? List.of() : closeCheckProviders;
        for (ChannelQualityRiskCaseCloseCheckProvider provider : providers) {
            if (provider == null || !stableCode(provider.code()) || provider.contractVersion() < 1
                    || provider.order() < 0 || !codes.add(provider.code())) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            ChannelQualityRiskCaseCloseCheckResult result;
            try {
                result = provider.evaluate(context);
            } catch (RuntimeException ex) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            requireCloseCheckResult(result);
            results.add(new CheckedCloseResult(provider.code(), provider.contractVersion(), provider.order() + 100,
                    result));
        }
        return results;
    }

    private CheckedCloseResult internalCheck(
            String code, boolean pass, String failureReasonCode, String summary) {
        return new CheckedCloseResult(
                code,
                1,
                0,
                new ChannelQualityRiskCaseCloseCheckResult(
                        "BLOCKING",
                        pass ? "PASS" : "FAIL",
                        pass ? "CHECK_PASSED" : failureReasonCode,
                        summary));
    }

    private void requireReadyToClose(List<CheckedCloseResult> checks) {
        List<String> failures = checks.stream()
                .filter(this::isBlockingFailure)
                .map(check -> check.code() + ":" + check.result().reasonCode())
                .toList();
        if (!failures.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "CLOSE_CHECK_FAILED", failures);
        }
    }

    private String closeSnapshotPayload(
            CaseAggregate aggregate, CloseSelection selection, int closedCaseVersion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("payloadSchemaVersion", 1);
        payload.put("caseId", aggregate.riskCase().getId());
        payload.put("batchId", aggregate.riskCase().getBatchId());
        payload.put("domain", aggregate.riskCase().getDomain());
        payload.put("closedCaseVersion", closedCaseVersion);
        payload.put("closedCoordinationVersion", aggregate.batch().getCoordinationVersion());
        payload.put("closedGovernanceVersion", aggregate.governance().getGovernanceVersion());
        payload.put("resolution", safeResolutionPayload(selection.resolution(), selection.rootCauses()));
        payload.put("actionReferences", selection.actionReferences().stream()
                .map(this::safeActionPayload).toList());
        payload.put("evidenceEntries", selection.evidenceEntries().stream()
                .map(this::safeEvidencePayload).toList());
        payload.put("checks", selection.checks().stream().map(this::safeCheckPayload).toList());
        return canonicalJson(payload);
    }

    private ChannelQualityRiskCaseCloseCheckContext closeCheckContext(
            CaseAggregate aggregate,
            ChannelQualityReviewRiskCaseResolutionRevisionRow resolution,
            List<ChannelQualityReviewRiskCaseResolutionRootCauseRow> rootCauses,
            List<ChannelQualityReviewRiskCaseActionReferenceRow> actions,
            List<ChannelQualityReviewRiskCaseEvidenceEntryRow> evidence,
            Long retrospectiveOwnerUid,
            String dueState) {
        ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase = aggregate.riskCase();
        ChannelQualityReviewBatchCoordinationRow batch = aggregate.batch();
        ChannelQualityReviewRiskCaseGovernanceRow governance = aggregate.governance();
        return new ChannelQualityRiskCaseCloseCheckContext(
                new ChannelQualityRiskCaseCloseCheckContext.RiskCase(
                        riskCase.getId(), riskCase.getBatchId(), riskCase.getDomain(), riskCase.getTriggerType(),
                        riskCase.getStatus(), riskCase.getOwnerUid(), riskCase.getCaseVersion(),
                        riskCase.getOpenedCoordinationVersion(), riskCase.getV41CloseSnapshotId(),
                        riskCase.getLegacyClosedWithoutSnapshot(), riskCase.getCreateTime(), riskCase.getUpdateTime()),
                new ChannelQualityRiskCaseCloseCheckContext.Batch(
                        batch.getId(), batch.getDomain(), batch.getSourceType(), batch.getName(),
                        batch.getAssigneeUid(), batch.getDueAt(), batch.getEffectiveDueAt(),
                        batch.getCoordinationVersion(), batch.getCandidateCount(), batch.getCreateTime()),
                new ChannelQualityRiskCaseCloseCheckContext.Governance(
                        governance.getCaseId(), governance.getBatchId(), governance.getDomain(),
                        governance.getGovernanceVersion(), governance.getCurrentResolutionRevisionId(),
                        governance.getCreateTime(), governance.getUpdateTime()),
                resolution == null ? null : new ChannelQualityRiskCaseCloseCheckContext.ResolutionRevision(
                        resolution.getId(), resolution.getRevisionNo(), resolution.getOutcomeType(),
                        resolution.getContentRecoveryState(), resolution.getRecoveryScope(),
                        resolution.getResidualRiskLevel(), resolution.getSummary(), resolution.getCreateTime()),
                rootCauses == null ? List.of() : rootCauses.stream()
                        .map(row -> new ChannelQualityRiskCaseCloseCheckContext.RootCause(
                                row.getId(), row.getCauseRole(), row.getCategory(), row.getSequenceNo(),
                                row.getNote()))
                        .toList(),
                actions == null ? List.of() : actions.stream()
                        .map(row -> new ChannelQualityRiskCaseCloseCheckContext.ActionReference(
                                row.getId(), row.getReferenceType(), row.getObservedVersion(), row.getOccurredAt(),
                                row.getSummary(), row.getCorrectionOfReferenceId()))
                        .toList(),
                evidence == null ? List.of() : evidence.stream()
                        .map(row -> new ChannelQualityRiskCaseCloseCheckContext.EvidenceEntry(
                                row.getId(), row.getEvidenceType(), row.getAssertionType(), row.getSubjectType(),
                                row.getSourceType(), row.getSourceVersion(), row.getObservedAt(), row.getSummary(),
                                row.getCorrectionOfEntryId()))
                        .toList(),
                retrospectiveOwnerUid, dueState, analyticsClock.instant());
    }

    private boolean isBlockingFailure(CheckedCloseResult check) {
        return "BLOCKING".equals(check.result().requirementLevel())
                && "FAIL".equals(check.result().result());
    }

    private Map<String, Object> safeResolutionPayload(
            ChannelQualityReviewRiskCaseResolutionRevisionRow resolution,
            List<ChannelQualityReviewRiskCaseResolutionRootCauseRow> rootCauses) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", resolution.getId());
        payload.put("revisionNo", resolution.getRevisionNo());
        payload.put("outcomeType", resolution.getOutcomeType());
        payload.put("contentRecoveryState", resolution.getContentRecoveryState());
        payload.put("recoveryScope", resolution.getRecoveryScope());
        payload.put("residualRiskLevel", resolution.getResidualRiskLevel());
        payload.put("summary", resolution.getSummary());
        payload.put("rootCauses", rootCauses.stream().map(rootCause -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("role", rootCause.getCauseRole());
            value.put("category", rootCause.getCategory());
            value.put("sequenceNo", rootCause.getSequenceNo());
            value.put("note", rootCause.getNote());
            return value;
        }).toList());
        return payload;
    }

    private Map<String, Object> safeActionPayload(ChannelQualityReviewRiskCaseActionReferenceRow row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", row.getId());
        payload.put("referenceType", row.getReferenceType());
        payload.put("observedVersion", row.getObservedVersion());
        payload.put("occurredAt", toInstant(row.getOccurredAt()).toString());
        payload.put("summary", row.getSummary());
        payload.put("correctionOfReferenceId", row.getCorrectionOfReferenceId());
        return payload;
    }

    private Map<String, Object> safeEvidencePayload(ChannelQualityReviewRiskCaseEvidenceEntryRow row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", row.getId());
        payload.put("evidenceType", row.getEvidenceType());
        payload.put("assertionType", row.getAssertionType());
        payload.put("subjectType", row.getSubjectType());
        payload.put("sourceType", row.getSourceType());
        payload.put("sourceVersion", row.getSourceVersion());
        payload.put("observedAt", toInstant(row.getObservedAt()).toString());
        payload.put("summary", row.getSummary());
        payload.put("correctionOfEntryId", row.getCorrectionOfEntryId());
        return payload;
    }

    private Map<String, Object> safeCheckPayload(CheckedCloseResult check) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("providerCode", check.code());
        payload.put("providerVersion", check.version());
        payload.put("requirementLevel", check.result().requirementLevel());
        payload.put("result", check.result().result());
        payload.put("reasonCode", check.result().reasonCode());
        payload.put("summary", check.result().summary());
        return payload;
    }

    private ChannelQualityReviewRiskCaseClosePreviewDTO toClosePreview(
            CaseAggregate aggregate, List<CheckedCloseResult> checks) {
        return ChannelQualityReviewRiskCaseClosePreviewDTO.builder()
                .caseId(aggregate.riskCase().getId())
                .readyToClose(checks.stream().noneMatch(this::isBlockingFailure))
                .caseVersion(aggregate.riskCase().getCaseVersion())
                .coordinationVersion(aggregate.batch().getCoordinationVersion())
                .governanceVersion(aggregate.governance().getGovernanceVersion())
                .governanceFactVersion(aggregate.governance().getGovernanceVersion())
                .checks(checks.stream().map(this::toCloseCheck).toList())
                .build();
    }

    private ChannelQualityReviewRiskCaseCloseSnapshotDTO toCloseSnapshot(
            ChannelQualityReviewRiskCaseCloseSnapshotRow snapshot) {
        requireSnapshot(snapshot);
        if (!sha256(snapshot.getCanonicalPayload()).equals(snapshot.getSnapshotDigest())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        List<ChannelQualityReviewRiskCaseCloseCheckRow> checks =
                governanceMapper.listCloseChecks(snapshot.getId());
        if (checks == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        JsonNode payload;
        try {
            payload = objectMapper.readTree(snapshot.getCanonicalPayload());
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        List<ChannelQualityReviewRiskCaseResolutionRevisionDTO.RootCauseDTO> rootCauses =
                snapshotRootCauses(payload.path("resolution").path("rootCauses"));
        List<ChannelQualityReviewRiskCaseActionReferenceDTO> actionReferences =
                snapshotActions(payload.path("actionReferences"));
        List<ChannelQualityReviewRiskCaseEvidenceEntryDTO> evidenceEntries =
                snapshotEvidence(payload.path("evidenceEntries"));
        List<ChannelQualityReviewRiskCaseCloseCheckDTO> persistedChecks =
                checks.stream().map(this::toCloseCheck).toList();
        verifySnapshotPayload(snapshot, payload, rootCauses, actionReferences, evidenceEntries, persistedChecks);
        return ChannelQualityReviewRiskCaseCloseSnapshotDTO.builder()
                .id(snapshot.getId())
                .caseId(snapshot.getCaseId())
                .batchId(snapshot.getBatchId())
                .domain(snapshot.getDomain())
                .resolutionRevisionId(snapshot.getResolutionRevisionId())
                .closedCaseVersion(snapshot.getClosedCaseVersion())
                .closedCoordinationVersion(snapshot.getClosedCoordinationVersion())
                .closedGovernanceVersion(snapshot.getClosedGovernanceVersion())
                .outcomeType(snapshot.getOutcomeType())
                .contentRecoveryState(snapshot.getContentRecoveryState())
                .primaryRootCause(snapshot.getPrimaryRootCause())
                .residualRiskLevel(snapshot.getResidualRiskLevel())
                .actionReferenceCount(snapshot.getActionReferenceCount())
                .evidenceCount(snapshot.getEvidenceCount())
                .payloadSchemaVersion(snapshot.getPayloadSchemaVersion())
                .snapshotDigest(snapshot.getSnapshotDigest())
                .rootCauses(rootCauses)
                .actionReferences(actionReferences)
                .evidenceEntries(evidenceEntries)
                .checks(persistedChecks)
                .createTime(toInstant(snapshot.getCreateTime()))
                .build();
    }

    private void verifySnapshotPayload(
            ChannelQualityReviewRiskCaseCloseSnapshotRow snapshot,
            JsonNode payload,
            List<ChannelQualityReviewRiskCaseResolutionRevisionDTO.RootCauseDTO> rootCauses,
            List<ChannelQualityReviewRiskCaseActionReferenceDTO> actionReferences,
            List<ChannelQualityReviewRiskCaseEvidenceEntryDTO> evidenceEntries,
            List<ChannelQualityReviewRiskCaseCloseCheckDTO> persistedChecks) {
        JsonNode resolution = payload.path("resolution");
        if (!payload.isObject()
                || requiredJsonInt(payload, "payloadSchemaVersion") != snapshot.getPayloadSchemaVersion()
                || requiredJsonLong(payload, "caseId") != snapshot.getCaseId()
                || requiredJsonLong(payload, "batchId") != snapshot.getBatchId()
                || requiredJsonInt(payload, "domain") != snapshot.getDomain()
                || requiredJsonInt(payload, "closedCaseVersion") != snapshot.getClosedCaseVersion()
                || requiredJsonInt(payload, "closedCoordinationVersion") != snapshot.getClosedCoordinationVersion()
                || requiredJsonInt(payload, "closedGovernanceVersion") != snapshot.getClosedGovernanceVersion()
                || !resolution.isObject()
                || requiredJsonLong(resolution, "id") != snapshot.getResolutionRevisionId()
                || !snapshot.getOutcomeType().equals(requiredJsonText(resolution, "outcomeType"))
                || !snapshot.getContentRecoveryState().equals(requiredJsonText(resolution, "contentRecoveryState"))
                || !snapshot.getResidualRiskLevel().equals(requiredJsonText(resolution, "residualRiskLevel"))
                || rootCauses.isEmpty()
                || !snapshot.getPrimaryRootCause().equals(rootCauses.get(0).getCategory())
                || snapshot.getActionReferenceCount() != actionReferences.size()
                || snapshot.getEvidenceCount() != evidenceEntries.size()
                || !persistedChecks.equals(snapshotChecks(payload.path("checks")))) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private List<ChannelQualityReviewRiskCaseCloseCheckDTO> snapshotChecks(JsonNode array) {
        if (!array.isArray()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        List<ChannelQualityReviewRiskCaseCloseCheckDTO> result = new ArrayList<>();
        for (JsonNode item : array) {
            ChannelQualityReviewRiskCaseCloseCheckDTO check = ChannelQualityReviewRiskCaseCloseCheckDTO.builder()
                    .providerCode(requiredJsonText(item, "providerCode"))
                    .providerVersion(requiredJsonInt(item, "providerVersion"))
                    .requirementLevel(requiredJsonText(item, "requirementLevel"))
                    .result(requiredJsonText(item, "result"))
                    .reasonCode(requiredJsonText(item, "reasonCode"))
                    .summary(requiredJsonText(item, "summary"))
                    .build();
            if (!Set.of("BLOCKING", "ADVISORY").contains(check.getRequirementLevel())
                    || !Set.of("PASS", "NOT_APPLICABLE", "FAIL").contains(check.getResult())
                    || ("BLOCKING".equals(check.getRequirementLevel()) && "FAIL".equals(check.getResult()))
                    || !stableCode(check.getProviderCode()) || check.getProviderVersion() < 1
                    || !stableCode(check.getReasonCode())
                    || requiredText(check.getSummary(), 2, 500) == null) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            result.add(check);
        }
        return result;
    }

    private List<ChannelQualityReviewRiskCaseResolutionRevisionDTO.RootCauseDTO> snapshotRootCauses(JsonNode array) {
        if (!array.isArray()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        List<ChannelQualityReviewRiskCaseResolutionRevisionDTO.RootCauseDTO> result = new ArrayList<>();
        for (JsonNode item : array) {
            result.add(ChannelQualityReviewRiskCaseResolutionRevisionDTO.RootCauseDTO.builder()
                    .role(requiredJsonText(item, "role"))
                    .category(requiredJsonText(item, "category"))
                    .sequenceNo(requiredJsonInt(item, "sequenceNo"))
                    .note(requiredJsonText(item, "note"))
                    .build());
        }
        return result;
    }

    private List<ChannelQualityReviewRiskCaseActionReferenceDTO> snapshotActions(JsonNode array) {
        if (!array.isArray()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        List<ChannelQualityReviewRiskCaseActionReferenceDTO> result = new ArrayList<>();
        for (JsonNode item : array) {
            result.add(ChannelQualityReviewRiskCaseActionReferenceDTO.builder()
                    .id(requiredJsonLong(item, "id"))
                    .referenceType(requiredJsonText(item, "referenceType"))
                    .observedVersion(optionalJsonInt(item, "observedVersion"))
                    .occurredAt(Instant.parse(requiredJsonText(item, "occurredAt")))
                    .summary(requiredJsonText(item, "summary"))
                    .correctionOfReferenceId(optionalJsonLong(item, "correctionOfReferenceId"))
                    .build());
        }
        return result;
    }

    private List<ChannelQualityReviewRiskCaseEvidenceEntryDTO> snapshotEvidence(JsonNode array) {
        if (!array.isArray()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        List<ChannelQualityReviewRiskCaseEvidenceEntryDTO> result = new ArrayList<>();
        for (JsonNode item : array) {
            result.add(ChannelQualityReviewRiskCaseEvidenceEntryDTO.builder()
                    .id(requiredJsonLong(item, "id"))
                    .evidenceType(requiredJsonText(item, "evidenceType"))
                    .assertionType(requiredJsonText(item, "assertionType"))
                    .subjectType(requiredJsonText(item, "subjectType"))
                    .sourceType(requiredJsonText(item, "sourceType"))
                    .sourceVersion(optionalJsonInt(item, "sourceVersion"))
                    .observedAt(Instant.parse(requiredJsonText(item, "observedAt")))
                    .summary(requiredJsonText(item, "summary"))
                    .correctionOfEntryId(optionalJsonLong(item, "correctionOfEntryId"))
                    .build());
        }
        return result;
    }

    private ChannelQualityReviewRiskCaseRetrospectiveDTO replayRetrospective(
            ChannelQualityReviewRiskCaseRetrospectiveEventRow existing,
            String fingerprint,
            ChannelQualityReviewRiskCaseRetrospectiveRow retrospective,
            Long operatorUid) {
        requireRetrospectiveEvent(existing);
        if (!fingerprint.equals(existing.getCommandFingerprint())) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        return toRetrospective(retrospective, operatorUid);
    }

    private ChannelQualityReviewRiskCaseGovernanceCaseRow readCaseForModerator(Long caseId, Long operatorUid) {
        long safeCaseId = requireId(caseId);
        requireOperator(operatorUid);
        ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase =
                requireCase(governanceMapper.selectCaseById(safeCaseId));
        requireModerate(operatorUid, riskCase.getDomain());
        return riskCase;
    }

    private CaseAggregate readAggregate(Long caseId, Long operatorUid) {
        ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase = readCaseForModerator(caseId, operatorUid);
        ChannelQualityReviewBatchCoordinationRow batch =
                requireBatch(batchMapper.selectCoordinationById(riskCase.getBatchId()));
        requireCaseBatch(riskCase, batch);
        ChannelQualityReviewRiskCaseGovernanceRow governance =
                requireGovernance(governanceMapper.selectGovernance(riskCase.getId()), riskCase);
        return new CaseAggregate(riskCase, batch, governance);
    }

    private CaseAggregate lockAggregate(Long caseId, Long operatorUid) {
        long safeCaseId = requireId(caseId);
        requireOperator(operatorUid);
        ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase =
                requireCase(governanceMapper.lockCaseById(safeCaseId));
        ChannelQualityReviewBatchCoordinationRow batch =
                requireBatch(batchMapper.lockCoordinationById(riskCase.getBatchId()));
        requireCaseBatch(riskCase, batch);
        requireModerate(operatorUid, riskCase.getDomain());
        ChannelQualityReviewRiskCaseGovernanceRow governance =
                requireGovernance(governanceMapper.lockGovernance(riskCase.getId()), riskCase);
        return new CaseAggregate(riskCase, batch, governance);
    }

    private void requireActiveGovernanceWrite(
            CaseAggregate aggregate, Integer expectedCaseVersion, Integer expectedGovernanceVersion) {
        requireCaseVersion(expectedCaseVersion, aggregate.riskCase());
        requireGovernanceVersion(expectedGovernanceVersion, aggregate.governance());
        if ("CLOSED".equals(aggregate.riskCase().getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
    }

    private void insertFact(
            Long caseId,
            Long batchId,
            Integer domain,
            String milestoneCode,
            LocalDateTime occurredAt,
            String sourceType,
            Long sourceId,
            Long ownerUid,
            String responsibilityScope,
            Integer responsibilityEpoch,
            String caseStatusAfter,
            String requiredAction,
            Long retrospectiveId) {
        if (!FACT_CODES.contains(milestoneCode) || !Set.of("V40_CASE_EVENT", "V41_CLOSE_SNAPSHOT", "V41_RETROSPECTIVE")
                .contains(sourceType) || !Set.of("CASE_OWNER", "RETROSPECTIVE", "CASE").contains(responsibilityScope)
                || !Set.of("ACKNOWLEDGE_CASE", "RECORD_PLAN", "NONE").contains(requiredAction)
                || occurredAt == null || sourceId == null || sourceId <= 0) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        ChannelQualityReviewRiskCaseGovernanceMilestoneRow existing =
                governanceMapper.selectGovernanceMilestoneBySource(sourceType, sourceId, milestoneCode);
        if (existing != null) {
            requireMilestone(existing);
            return;
        }
        try {
            requireWrite(governanceMapper.insertGovernanceMilestone(
                    idGenerator.nextId(), caseId, batchId, domain, milestoneCode, occurredAt, sourceType, sourceId,
                    ownerUid, responsibilityScope, responsibilityEpoch, caseStatusAfter, requiredAction,
                    retrospectiveId));
        } catch (DuplicateKeyException ex) {
            ChannelQualityReviewRiskCaseGovernanceMilestoneRow concurrent =
                    governanceMapper.selectGovernanceMilestoneBySource(sourceType, sourceId, milestoneCode);
            if (concurrent == null) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            requireMilestone(concurrent);
        }
    }

    private void verifyActionReference(
            ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase, ActionRequest request) {
        long referenceId;
        switch (request.referenceType()) {
            case "V39_COORDINATION_EVENT" -> {
                referenceId = parseInternalReference(request.referenceKey());
                if (governanceMapper.coordinationEventExists(riskCase.getBatchId(), referenceId) != 1) {
                    throw new BizException(ErrorCode.PARAM_ERROR);
                }
            }
            case "V40_CASE_EVENT" -> {
                referenceId = parseInternalReference(request.referenceKey());
                if (governanceMapper.caseEventExists(riskCase.getId(), referenceId) != 1) {
                    throw new BizException(ErrorCode.PARAM_ERROR);
                }
            }
            case "MAINTENANCE_TASK" -> {
                referenceId = parseInternalReference(request.referenceKey());
                if (governanceMapper.maintenanceTaskExists(riskCase.getBatchId(), referenceId) != 1) {
                    throw new BizException(ErrorCode.PARAM_ERROR);
                }
            }
            case "GOVERNANCE_DECISION" -> {
                referenceId = parseInternalReference(request.referenceKey());
                if (governanceMapper.selectResolutionRevision(riskCase.getId(), referenceId) == null) {
                    throw new BizException(ErrorCode.PARAM_ERROR);
                }
            }
            case "EXTERNAL_TICKET" -> {
                // External tickets are recorded as controlled references and are never fetched in this transaction.
            }
            case "CONTENT_REVISION" -> throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            default -> throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private void verifyEvidenceSource(
            ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase, EvidenceRequest request) {
        if ("V39_COORDINATION_EVENT".equals(request.sourceType())) {
            if (governanceMapper.coordinationEventExists(
                    riskCase.getBatchId(), parseInternalReference(request.sourceRef())) != 1) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
        } else if ("MAINTENANCE_TASK".equals(request.sourceType())) {
            if (governanceMapper.maintenanceTaskExists(
                    riskCase.getBatchId(), parseInternalReference(request.sourceRef())) != 1) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
        } else if ("GOVERNANCE_DECISION".equals(request.sourceType())) {
            if (governanceMapper.selectResolutionRevision(
                    riskCase.getId(), parseInternalReference(request.sourceRef())) == null) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
        } else if ("CONTENT_REVISION".equals(request.sourceType())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private boolean validSelectedActions(
            List<ChannelQualityReviewRiskCaseActionReferenceRow> rows, List<Long> expectedIds) {
        if (rows == null || rows.size() != expectedIds.size()
                || governanceMapper.countActionReferenceCorrections(
                rows.isEmpty() ? 0L : rows.get(0).getCaseId(), expectedIds) != 0) {
            return false;
        }
        return rows.stream().allMatch(row -> {
            try {
                requireActionReference(row);
                return true;
            } catch (BizException ex) {
                return false;
            }
        });
    }

    private boolean validSelectedEvidence(
            List<ChannelQualityReviewRiskCaseEvidenceEntryRow> rows, List<Long> expectedIds) {
        if (rows == null || rows.size() != expectedIds.size()
                || governanceMapper.countEvidenceEntryCorrections(
                rows.isEmpty() ? 0L : rows.get(0).getCaseId(), expectedIds) != 0) {
            return false;
        }
        return rows.stream().allMatch(row -> {
            try {
                requireEvidenceEntry(row);
                return true;
            } catch (BizException ex) {
                return false;
            }
        });
    }

    private boolean outcomeEvidenceMatches(
            ChannelQualityReviewRiskCaseResolutionRevisionRow resolution,
            List<ChannelQualityReviewRiskCaseEvidenceEntryRow> evidence,
            List<ChannelQualityReviewRiskCaseActionReferenceRow> actions) {
        if (resolution == null || evidence == null || actions == null) {
            return false;
        }
        return switch (resolution.getOutcomeType()) {
            case "RECOVERY_CONFIRMED" -> containsAssertion(evidence, "SUPPORTS_RECOVERY")
                    && evidence.stream().noneMatch(row -> "REFUTES_RECOVERY".equals(row.getAssertionType()));
            case "PARTIAL_RECOVERY" -> resolution.getResidualRiskLevel() != null
                    && !"NONE".equals(resolution.getResidualRiskLevel())
                    && (containsAssertion(evidence, "SUPPORTS_PARTIAL_RECOVERY")
                    || containsAssertion(evidence, "SUPPORTS_RECOVERY"));
            case "RISK_CONTAINED" -> !actions.isEmpty()
                    && containsAssertion(evidence, "SUPPORTS_CONTAINMENT");
            case "FALSE_POSITIVE_CONFIRMED" -> containsAssertion(evidence, "SUPPORTS_FALSE_POSITIVE")
                    && evidence.stream().anyMatch(row -> Set.of("QUALITY_RECHECK", "POLICY_DECISION")
                    .contains(row.getEvidenceType()));
            case "RISK_ACCEPTED" -> !"NONE".equals(resolution.getResidualRiskLevel())
                    && evidence.stream().anyMatch(row -> "SUPPORTS_RISK_ACCEPTANCE".equals(row.getAssertionType())
                    && "POLICY_DECISION".equals(row.getEvidenceType()));
            default -> false;
        };
    }

    private boolean hasNonTaskRecoveryEvidence(
            ChannelQualityReviewRiskCaseResolutionRevisionRow resolution,
            List<ChannelQualityReviewRiskCaseEvidenceEntryRow> evidence) {
        if (resolution == null || !Set.of("RECOVERY_CONFIRMED", "PARTIAL_RECOVERY")
                .contains(resolution.getOutcomeType())) {
            return true;
        }
        return evidence.stream().anyMatch(row -> (
                "SUPPORTS_RECOVERY".equals(row.getAssertionType())
                        || "SUPPORTS_PARTIAL_RECOVERY".equals(row.getAssertionType()))
                && !Set.of("MAINTENANCE_TASK", "V39_COORDINATION_EVENT").contains(row.getSourceType()));
    }

    private boolean noUncorrectedNegativeEvidence(
            ChannelQualityReviewRiskCaseResolutionRevisionRow resolution,
            List<ChannelQualityReviewRiskCaseEvidenceEntryRow> evidence) {
        return resolution == null || !"RECOVERY_CONFIRMED".equals(resolution.getOutcomeType())
                || evidence.stream().noneMatch(row -> "REFUTES_RECOVERY".equals(row.getAssertionType()));
    }

    private static boolean containsAssertion(
            List<ChannelQualityReviewRiskCaseEvidenceEntryRow> evidence, String assertionType) {
        return evidence.stream().anyMatch(row -> assertionType.equals(row.getAssertionType()));
    }

    private static ChannelQualityReviewRiskCaseResolutionRootCauseRow primaryRootCause(
            List<ChannelQualityReviewRiskCaseResolutionRootCauseRow> rootCauses) {
        return rootCauses.stream()
                .filter(row -> "PRIMARY".equals(row.getCauseRole()))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.DEPENDENCY_ERROR));
    }

    private List<ChannelQualityReviewRiskCaseResolutionRootCauseRow> listRootCauses(
            Long caseId, Long revisionId) {
        List<ChannelQualityReviewRiskCaseResolutionRootCauseRow> rows =
                governanceMapper.listRootCauses(caseId, revisionId);
        if (!validRootCauses(rows)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return rows;
    }

    private static boolean validRootCauses(List<ChannelQualityReviewRiskCaseResolutionRootCauseRow> rows) {
        if (rows == null || rows.isEmpty() || rows.size() > 6) {
            return false;
        }
        int primary = 0;
        Set<Integer> sequences = new HashSet<>();
        for (ChannelQualityReviewRiskCaseResolutionRootCauseRow row : rows) {
            if (row == null || row.getId() == null || row.getId() <= 0 || row.getCaseId() == null
                    || row.getCaseId() <= 0 || row.getResolutionRevisionId() == null
                    || row.getResolutionRevisionId() <= 0 || !ROOT_CAUSES.contains(row.getCategory())
                    || row.getCreateTime() == null) {
                return false;
            }
            if ("PRIMARY".equals(row.getCauseRole()) && Integer.valueOf(0).equals(row.getSequenceNo())) {
                primary++;
            } else if (!"CONTRIBUTING".equals(row.getCauseRole()) || row.getSequenceNo() == null
                    || row.getSequenceNo() < 1 || row.getSequenceNo() > 5
                    || !sequences.add(row.getSequenceNo())) {
                return false;
            }
        }
        return primary == 1;
    }

    private ChannelQualityReviewRiskCaseResolutionRevisionDTO toResolution(
            ChannelQualityReviewRiskCaseResolutionRevisionRow row,
            List<ChannelQualityReviewRiskCaseResolutionRootCauseRow> rootCauses) {
        requireResolution(row);
        return ChannelQualityReviewRiskCaseResolutionRevisionDTO.builder()
                .id(row.getId())
                .caseId(row.getCaseId())
                .revisionNo(row.getRevisionNo())
                .outcomeType(row.getOutcomeType())
                .contentRecoveryState(row.getContentRecoveryState())
                .recoveryScope(row.getRecoveryScope())
                .residualRiskLevel(row.getResidualRiskLevel())
                .summary(row.getSummary())
                .rootCauses(rootCauses.stream().map(root -> ChannelQualityReviewRiskCaseResolutionRevisionDTO.RootCauseDTO
                        .builder()
                        .role(root.getCauseRole())
                        .category(root.getCategory())
                        .sequenceNo(root.getSequenceNo())
                        .note(root.getNote())
                        .build()).toList())
                .createTime(toInstant(row.getCreateTime()))
                .build();
    }

    private ChannelQualityReviewRiskCaseGovernanceDTO.ResolutionRevisionSummaryDTO toResolutionSummary(
            ChannelQualityReviewRiskCaseResolutionRevisionRow row) {
        if (row == null) {
            return null;
        }
        requireResolution(row);
        return ChannelQualityReviewRiskCaseGovernanceDTO.ResolutionRevisionSummaryDTO.builder()
                .id(row.getId())
                .revisionNo(row.getRevisionNo())
                .outcomeType(row.getOutcomeType())
                .contentRecoveryState(row.getContentRecoveryState())
                .residualRiskLevel(row.getResidualRiskLevel())
                .summary(row.getSummary())
                .createTime(toInstant(row.getCreateTime()))
                .build();
    }

    private ChannelQualityReviewRiskCaseActionReferenceDTO toActionReference(
            ChannelQualityReviewRiskCaseActionReferenceRow row) {
        requireActionReference(row);
        return ChannelQualityReviewRiskCaseActionReferenceDTO.builder()
                .id(row.getId())
                .caseId(row.getCaseId())
                .referenceType(row.getReferenceType())
                .observedVersion(row.getObservedVersion())
                .occurredAt(toInstant(row.getOccurredAt()))
                .summary(row.getSummary())
                .correctionOfReferenceId(row.getCorrectionOfReferenceId())
                .createTime(toInstant(row.getCreateTime()))
                .build();
    }

    private ChannelQualityReviewRiskCaseEvidenceEntryDTO toEvidenceEntry(
            ChannelQualityReviewRiskCaseEvidenceEntryRow row) {
        requireEvidenceEntry(row);
        return ChannelQualityReviewRiskCaseEvidenceEntryDTO.builder()
                .id(row.getId())
                .caseId(row.getCaseId())
                .evidenceType(row.getEvidenceType())
                .assertionType(row.getAssertionType())
                .subjectType(row.getSubjectType())
                .sourceType(row.getSourceType())
                .sourceVersion(row.getSourceVersion())
                .observedAt(toInstant(row.getObservedAt()))
                .summary(row.getSummary())
                .correctionOfEntryId(row.getCorrectionOfEntryId())
                .createTime(toInstant(row.getCreateTime()))
                .build();
    }

    private ChannelQualityReviewRiskCaseCloseCheckDTO toCloseCheck(CheckedCloseResult check) {
        return ChannelQualityReviewRiskCaseCloseCheckDTO.builder()
                .providerCode(check.code())
                .providerVersion(check.version())
                .requirementLevel(check.result().requirementLevel())
                .result(check.result().result())
                .reasonCode(check.result().reasonCode())
                .summary(check.result().summary())
                .build();
    }

    private ChannelQualityReviewRiskCaseCloseCheckDTO toCloseCheck(ChannelQualityReviewRiskCaseCloseCheckRow row) {
        requireCloseCheck(row);
        return ChannelQualityReviewRiskCaseCloseCheckDTO.builder()
                .providerCode(row.getProviderCode())
                .providerVersion(row.getProviderVersion())
                .requirementLevel(row.getRequirementLevel())
                .result(row.getResult())
                .reasonCode(row.getReasonCode())
                .summary(row.getSummary())
                .build();
    }

    private ChannelQualityReviewRiskCaseRetrospectiveDTO toRetrospective(
            ChannelQualityReviewRiskCaseRetrospectiveRow row, Long operatorUid) {
        requireRetrospective(row, null);
        boolean moderator = canModerate(operatorUid, row.getDomain());
        return ChannelQualityReviewRiskCaseRetrospectiveDTO.builder()
                .id(row.getId())
                .caseId(row.getCaseId())
                .closeSnapshotId(row.getCloseSnapshotId())
                .domain(row.getDomain())
                .status(row.getStatus())
                .ownerUid(row.getOwnerUid())
                .retrospectiveVersion(row.getRetrospectiveVersion())
                .legacyBaseline(row.getLegacyBaseline() == 1)
                .startedAt(toInstant(row.getStartedAt()))
                .completedAt(toInstant(row.getCompletedAt()))
                .createTime(toInstant(row.getCreateTime()))
                .updateTime(toInstant(row.getUpdateTime()))
                .canAssignOwner(moderator && Set.of("PENDING", "IN_PROGRESS").contains(row.getStatus()))
                .canStart(moderator && "PENDING".equals(row.getStatus()))
                .canRecordFinding(moderator && "IN_PROGRESS".equals(row.getStatus()))
                .canComplete(moderator && "IN_PROGRESS".equals(row.getStatus()))
                .build();
    }

    private ChannelQualityReviewRiskCaseGovernanceDTO.RetrospectiveSummaryDTO toRetrospectiveSummary(
            ChannelQualityReviewRiskCaseRetrospectiveRow row) {
        if (row == null) {
            return null;
        }
        requireRetrospective(row, null);
        return ChannelQualityReviewRiskCaseGovernanceDTO.RetrospectiveSummaryDTO.builder()
                .id(row.getId())
                .status(row.getStatus())
                .ownerUid(row.getOwnerUid())
                .retrospectiveVersion(row.getRetrospectiveVersion())
                .legacyBaseline(row.getLegacyBaseline() == 1)
                .updateTime(toInstant(row.getUpdateTime()))
                .build();
    }

    private ChannelQualityReviewRiskCaseRetrospectiveEventDTO toRetrospectiveEvent(
            ChannelQualityReviewRiskCaseRetrospectiveEventRow row) {
        requireRetrospectiveEvent(row);
        return ChannelQualityReviewRiskCaseRetrospectiveEventDTO.builder()
                .id(row.getId())
                .eventType(row.getEventType())
                .previousStatus(row.getPreviousStatus())
                .status(row.getStatus())
                .ownerUid(row.getOwnerUid())
                .learningCategory(row.getLearningCategory())
                .findingSummary(row.getFindingSummary())
                .preventionActionSummary(row.getPreventionActionSummary())
                .retrospectiveVersion(row.getRetrospectiveVersion())
                .note(row.getNote())
                .createTime(toInstant(row.getCreateTime()))
                .build();
    }

    private ChannelQualityReviewRiskCaseRecurrenceLinkDTO toRecurrenceLink(
            ChannelQualityReviewRiskCaseRecurrenceLinkRow row) {
        requireRecurrenceLink(row);
        return ChannelQualityReviewRiskCaseRecurrenceLinkDTO.builder()
                .id(row.getId())
                .previousCaseId(row.getPreviousCaseId())
                .relationType(row.getRelationType())
                .rootCauseCategory(row.getRootCauseCategory())
                .note(row.getNote())
                .linkDigest(recurrenceLinkDigest(row))
                .createTime(toInstant(row.getCreateTime()))
                .build();
    }

    private String recurrenceLinkDigest(ChannelQualityReviewRiskCaseRecurrenceLinkRow row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("currentCaseId", row.getCurrentCaseId());
        payload.put("previousCaseId", row.getPreviousCaseId());
        payload.put("relationType", row.getRelationType());
        payload.put("rootCauseCategory", row.getRootCauseCategory());
        payload.put("note", row.getNote());
        payload.put("commandFingerprint", row.getCommandFingerprint());
        return "sha256:" + sha256(canonicalJson(payload));
    }

    private ChannelQualityReviewRiskCaseGovernanceMilestoneDTO toMilestone(
            ChannelQualityReviewRiskCaseGovernanceMilestoneRow row) {
        requireMilestone(row);
        return ChannelQualityReviewRiskCaseGovernanceMilestoneDTO.builder()
                .sourceFactId(row.getId())
                .milestoneCode(row.getMilestoneCode())
                .occurredAt(toInstant(row.getOccurredAt()))
                .sourceType(row.getSourceType())
                .responsibilityScope(row.getResponsibilityScope())
                .responsibilityEpoch(row.getResponsibilityEpoch())
                .ownerUid(row.getOwnerUid())
                .caseStatusAfter(row.getCaseStatusAfter())
                .requiredAction(row.getRequiredAction())
                .contractVersion(row.getContractVersion())
                .factVersion(row.getFactVersion())
                .build();
    }

    private void requireTables() {
        try {
            if (governanceMapper.v41TablesExist() == 11
                    && governanceMapper.v41CaseColumnsExist() == 2
                    && governanceMapper.v41ConstraintsExist() == 21
                    && governanceMapper.v41IndexesExist() == 38
                    && governanceMapper.v41FactContractMatches() == 1
                    && batchMapper.batchTableExists() > 0
                    && batchMapper.taskTableExists() > 0
                    && batchMapper.coordinationEventTableExists() > 0) {
                return;
            }
        } catch (RuntimeException ignored) {
            // V41 metadata is an explicit fail-closed dependency.
        }
        throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                "Channel quality risk-case V41 migration and fact contract are required: " + MIGRATION);
    }

    private static ChannelQualityReviewRiskCaseGovernanceCaseRow requireCase(
            ChannelQualityReviewRiskCaseGovernanceCaseRow row) {
        if (row == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (row.getId() == null || row.getId() <= 0 || row.getBatchId() == null || row.getBatchId() <= 0
                || row.getDomain() == null || row.getDomain() < 1 || row.getDomain() > 5
                || !CASE_STATUSES.contains(row.getStatus()) || row.getCaseVersion() == null
                || row.getCaseVersion() < 1 || row.getOpenedCoordinationVersion() == null
                || row.getOpenedCoordinationVersion() < 0 || row.getCreateTime() == null
                || row.getUpdateTime() == null || row.getLegacyClosedWithoutSnapshot() == null
                || !Set.of(0, 1).contains(row.getLegacyClosedWithoutSnapshot())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        boolean closed = "CLOSED".equals(row.getStatus());
        boolean snapshot = row.getV41CloseSnapshotId() != null && row.getV41CloseSnapshotId() > 0;
        boolean legacy = row.getLegacyClosedWithoutSnapshot() == 1;
        if ((!closed && (snapshot || legacy)) || (closed && snapshot == legacy)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return row;
    }

    private static void requireV40Case(ChannelQualityReviewRiskCaseRow row) {
        if (row == null || row.getId() == null || row.getId() <= 0 || row.getBatchId() == null
                || row.getBatchId() <= 0 || row.getDomain() == null || row.getDomain() < 1
                || row.getDomain() > 5 || !CASE_STATUSES.contains(row.getStatus())
                || row.getCaseVersion() == null || row.getCaseVersion() < 1
                || row.getOwnerUid() == null || row.getOwnerUid() <= 0) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private static ChannelQualityReviewRiskCaseGovernanceRow requireGovernance(
            ChannelQualityReviewRiskCaseGovernanceRow row,
            ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase) {
        if (row == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (row.getCaseId() == null || row.getCaseId() <= 0 || row.getBatchId() == null || row.getBatchId() <= 0
                || row.getDomain() == null || row.getDomain() < 1 || row.getDomain() > 5
                || row.getGovernanceVersion() == null || row.getGovernanceVersion() < 0
                || row.getCreateTime() == null || row.getUpdateTime() == null
                || (row.getCurrentResolutionRevisionId() != null && row.getCurrentResolutionRevisionId() <= 0)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (riskCase != null && (!row.getCaseId().equals(riskCase.getId())
                || !row.getBatchId().equals(riskCase.getBatchId())
                || !row.getDomain().equals(riskCase.getDomain()))) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return row;
    }

    private static ChannelQualityReviewRiskCaseGovernanceRow requireGovernance(
            ChannelQualityReviewRiskCaseGovernanceRow row,
            ChannelQualityReviewRiskCaseRow riskCase) {
        if (row == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (riskCase != null && (row.getCaseId() == null || row.getBatchId() == null || row.getDomain() == null
                || !row.getCaseId().equals(riskCase.getId())
                || !row.getBatchId().equals(riskCase.getBatchId())
                || !row.getDomain().equals(riskCase.getDomain()))) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return requireGovernance(row, (ChannelQualityReviewRiskCaseGovernanceCaseRow) null);
    }

    private static ChannelQualityReviewBatchCoordinationRow requireBatch(
            ChannelQualityReviewBatchCoordinationRow row) {
        if (row == null || row.getId() == null || row.getId() <= 0 || row.getDomain() == null
                || row.getDomain() < 1 || row.getDomain() > 5 || !SOURCE_TYPE.equals(row.getSourceType())
                || !StringUtils.hasText(row.getName()) || row.getAssigneeUid() == null || row.getAssigneeUid() <= 0
                || row.getCoordinationVersion() == null || row.getCoordinationVersion() < 0
                || row.getCandidateCount() == null || row.getCandidateCount() < 1
                || row.getCandidateCount() > MAX_BATCH_TASKS || row.getCreateTime() == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return row;
    }

    private static ChannelQualityReviewRiskCaseResolutionRevisionRow requireResolution(
            ChannelQualityReviewRiskCaseResolutionRevisionRow row) {
        if (row == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (row.getId() == null || row.getId() <= 0 || row.getCaseId() == null || row.getCaseId() <= 0
                || row.getRevisionNo() == null || row.getRevisionNo() < 1
                || !OUTCOME_TYPES.contains(row.getOutcomeType())
                || !RECOVERY_STATES.contains(row.getContentRecoveryState())
                || !RISK_LEVELS.contains(row.getResidualRiskLevel()) || row.getCreatedByUid() == null
                || row.getCreatedByUid() <= 0 || row.getCreateTime() == null
                || !stableFingerprint(row.getCommandFingerprint())
                || !stableCommandId(row.getCommandId())
                || requiredText(row.getRecoveryScope(), 2, 500) == null
                || requiredText(row.getSummary(), 2, 1000) == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return row;
    }

    private static ChannelQualityReviewRiskCaseActionReferenceRow requireActionReference(
            ChannelQualityReviewRiskCaseActionReferenceRow row) {
        if (row == null || row.getId() == null || row.getId() <= 0 || row.getCaseId() == null
                || row.getCaseId() <= 0 || !REFERENCE_TYPES.contains(row.getReferenceType())
                || row.getOccurredAt() == null || row.getCreatedByUid() == null || row.getCreatedByUid() <= 0
                || row.getCreateTime() == null || !stableFingerprint(row.getReferenceFingerprint())
                || !stableFingerprint(row.getCommandFingerprint()) || !stableCommandId(row.getCommandId())
                || requiredText(row.getReferenceKey(), 1, 160) == null
                || requiredText(row.getSummary(), 2, 500) == null
                || (row.getObservedVersion() != null && row.getObservedVersion() < 0)
                || (row.getCorrectionOfReferenceId() != null && row.getCorrectionOfReferenceId() <= 0)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return row;
    }

    private static ChannelQualityReviewRiskCaseEvidenceEntryRow requireEvidenceEntry(
            ChannelQualityReviewRiskCaseEvidenceEntryRow row) {
        if (row == null || row.getId() == null || row.getId() <= 0 || row.getCaseId() == null
                || row.getCaseId() <= 0 || !EVIDENCE_TYPES.contains(row.getEvidenceType())
                || !ASSERTION_TYPES.contains(row.getAssertionType())
                || !EVIDENCE_SUBJECT_TYPES.contains(row.getSubjectType())
                || !EVIDENCE_SOURCE_TYPES.contains(row.getSourceType())
                || row.getObservedAt() == null || row.getCreatedByUid() == null || row.getCreatedByUid() <= 0
                || row.getCreateTime() == null || !stableFingerprint(row.getEvidenceDigest())
                || !stableFingerprint(row.getCommandFingerprint()) || !stableCommandId(row.getCommandId())
                || requiredText(row.getSubjectRef(), 1, 160) == null
                || requiredText(row.getSourceRef(), 1, 160) == null
                || requiredText(row.getSummary(), 2, 1000) == null
                || (row.getSourceVersion() != null && row.getSourceVersion() < 0)
                || (row.getCorrectionOfEntryId() != null && row.getCorrectionOfEntryId() <= 0)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return row;
    }

    private static ChannelQualityReviewRiskCaseCloseSnapshotRow requireSnapshot(
            ChannelQualityReviewRiskCaseCloseSnapshotRow row) {
        if (row == null || row.getId() == null || row.getId() <= 0 || row.getCaseId() == null
                || row.getCaseId() <= 0 || row.getBatchId() == null || row.getBatchId() <= 0
                || row.getDomain() == null || row.getDomain() < 1 || row.getDomain() > 5
                || row.getResolutionRevisionId() == null || row.getResolutionRevisionId() <= 0
                || row.getClosedCaseVersion() == null || row.getClosedCaseVersion() < 1
                || row.getClosedCoordinationVersion() == null || row.getClosedCoordinationVersion() < 0
                || row.getClosedGovernanceVersion() == null || row.getClosedGovernanceVersion() < 0
                || !OUTCOME_TYPES.contains(row.getOutcomeType())
                || !RECOVERY_STATES.contains(row.getContentRecoveryState())
                || !ROOT_CAUSES.contains(row.getPrimaryRootCause())
                || !RISK_LEVELS.contains(row.getResidualRiskLevel())
                || row.getActionReferenceCount() == null || row.getActionReferenceCount() < 1
                || row.getEvidenceCount() == null || row.getEvidenceCount() < 1
                || !Integer.valueOf(1).equals(row.getPayloadSchemaVersion())
                || row.getClosedByUid() == null || row.getClosedByUid() <= 0
                || row.getCreateTime() == null || !stableFingerprint(row.getSnapshotDigest())
                || !stableFingerprint(row.getCommandFingerprint()) || !stableCommandId(row.getCommandId())
                || !StringUtils.hasText(row.getCanonicalPayload())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return row;
    }

    private static void requireCloseCheck(ChannelQualityReviewRiskCaseCloseCheckRow row) {
        if (row == null || row.getId() == null || row.getId() <= 0 || row.getSnapshotId() == null
                || row.getSnapshotId() <= 0 || row.getCaseId() == null || row.getCaseId() <= 0
                || !stableCode(row.getProviderCode()) || row.getProviderVersion() == null
                || row.getProviderVersion() < 1 || !Set.of("BLOCKING", "ADVISORY").contains(row.getRequirementLevel())
                || !Set.of("PASS", "NOT_APPLICABLE", "FAIL").contains(row.getResult())
                || ("BLOCKING".equals(row.getRequirementLevel()) && "FAIL".equals(row.getResult()))
                || !stableCode(row.getReasonCode()) || requiredText(row.getSummary(), 2, 500) == null
                || row.getCreateTime() == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private static void requireCloseCheckResult(ChannelQualityRiskCaseCloseCheckResult result) {
        if (result == null || !Set.of("BLOCKING", "ADVISORY").contains(result.requirementLevel())
                || !Set.of("PASS", "NOT_APPLICABLE", "FAIL").contains(result.result())
                || !stableCode(result.reasonCode()) || result.summary() == null
                || result.summary().trim().length() < 2 || result.summary().trim().length() > 500
                || result.summary().chars().anyMatch(Character::isISOControl)
                || SENSITIVE_TEXT.matcher(result.summary()).find()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private static void requireWrite(int updated) {
        if (updated != 1) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private static ChannelQualityReviewRiskCaseRetrospectiveRow requireRetrospective(
            ChannelQualityReviewRiskCaseRetrospectiveRow row,
            ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase) {
        if (row == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (row.getId() == null || row.getId() <= 0 || row.getCaseId() == null || row.getCaseId() <= 0
                || row.getDomain() == null || row.getDomain() < 1 || row.getDomain() > 5
                || !RETROSPECTIVE_STATUSES.contains(row.getStatus()) || row.getOwnerUid() == null
                || row.getOwnerUid() <= 0 || row.getRetrospectiveVersion() == null
                || row.getRetrospectiveVersion() < 1 || row.getLegacyBaseline() == null
                || !Set.of(0, 1).contains(row.getLegacyBaseline()) || row.getCreateTime() == null
                || row.getUpdateTime() == null
                || (row.getLegacyBaseline() == 0 && (row.getCloseSnapshotId() == null || row.getCloseSnapshotId() <= 0))
                || (row.getLegacyBaseline() == 1 && row.getCloseSnapshotId() != null)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (riskCase != null && (!row.getCaseId().equals(riskCase.getId())
                || !row.getDomain().equals(riskCase.getDomain()))) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return row;
    }

    private static ChannelQualityReviewRiskCaseRetrospectiveEventRow requireRetrospectiveEvent(
            ChannelQualityReviewRiskCaseRetrospectiveEventRow row) {
        if (row == null || row.getId() == null || row.getId() <= 0 || row.getRetrospectiveId() == null
                || row.getRetrospectiveId() <= 0 || row.getCaseId() == null || row.getCaseId() <= 0
                || row.getOperatorUid() == null || row.getOperatorUid() <= 0 || row.getRetrospectiveVersion() == null
                || row.getRetrospectiveVersion() < 1 || !stableCommandId(row.getCommandId())
                || !stableFingerprint(row.getCommandFingerprint()) || requiredText(row.getNote(), 2, 1000) == null
                || row.getCreateTime() == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return row;
    }

    private static ChannelQualityReviewRiskCaseRecurrenceLinkRow requireRecurrenceLink(
            ChannelQualityReviewRiskCaseRecurrenceLinkRow row) {
        if (row == null || row.getId() == null || row.getId() <= 0 || row.getCurrentCaseId() == null
                || row.getCurrentCaseId() <= 0 || row.getPreviousCaseId() == null || row.getPreviousCaseId() <= 0
                || row.getCurrentCaseId().equals(row.getPreviousCaseId()) || row.getDomain() == null
                || row.getDomain() < 1 || row.getDomain() > 5 || !RECURRENCE_TYPES.contains(row.getRelationType())
                || !ROOT_CAUSES.contains(row.getRootCauseCategory()) || row.getLinkedByUid() == null
                || row.getLinkedByUid() <= 0 || !stableCommandId(row.getCommandId())
                || !stableFingerprint(row.getCommandFingerprint()) || requiredText(row.getNote(), 2, 500) == null
                || row.getCreateTime() == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return row;
    }

    private static ChannelQualityReviewRiskCaseGovernanceMilestoneRow requireMilestone(
            ChannelQualityReviewRiskCaseGovernanceMilestoneRow row) {
        if (row == null || row.getId() == null || row.getId() <= 0 || row.getCaseId() == null
                || row.getCaseId() <= 0 || row.getBatchId() == null || row.getBatchId() <= 0
                || row.getDomain() == null || row.getDomain() < 1 || row.getDomain() > 5
                || !FACT_CODES.contains(row.getMilestoneCode()) || row.getOccurredAt() == null
                || !Set.of("V40_CASE_EVENT", "V41_CLOSE_SNAPSHOT", "V41_RETROSPECTIVE").contains(row.getSourceType())
                || row.getSourceId() == null || row.getSourceId() <= 0
                || !Set.of("CASE_OWNER", "RETROSPECTIVE", "CASE").contains(row.getResponsibilityScope())
                || !Set.of("ACKNOWLEDGE_CASE", "RECORD_PLAN", "NONE").contains(row.getRequiredAction())
                || !FACT_CONTRACT_VERSION.equals(row.getContractVersion())
                || !Integer.valueOf(1).equals(row.getFactVersion()) || row.getCreateTime() == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        boolean ownerFact = Set.of(
                "OWNER_ASSIGNED", "OWNER_ACKNOWLEDGED", "PLAN_RECORDED",
                "RETROSPECTIVE_PENDING", "RETROSPECTIVE_OWNER_ASSIGNED",
                "RETROSPECTIVE_COMPLETED").contains(row.getMilestoneCode());
        if (ownerFact && (row.getOwnerUid() == null || row.getOwnerUid() <= 0
                || row.getResponsibilityEpoch() == null || row.getResponsibilityEpoch() < 1)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return row;
    }

    private static void requireCaseBatch(
            ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase,
            ChannelQualityReviewBatchCoordinationRow batch) {
        if (!riskCase.getBatchId().equals(batch.getId()) || !riskCase.getDomain().equals(batch.getDomain())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private void requireModerate(Long uid, int domain) {
        if (!canModerate(uid, domain)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireEligibleOwner(Long uid, int domain) {
        if (!canModerate(uid, domain)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean canModerate(Long uid, int domain) {
        return uid != null && uid > 0
                && (adminPermissionService.isAdmin(uid)
                || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR)
                || adminPermissionService.isLocalOpenMode()
                || domainModeratorService.canModerateDomain(uid, domain));
    }

    private String dueState(ChannelQualityReviewBatchCoordinationRow batch) {
        List<ChannelQualityReviewBatchTaskStatusRow> rows =
                batchMapper.listTaskStatusCounts(List.of(batch.getId()));
        if (rows == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        Map<String, Integer> counts = new TreeMap<>();
        TASK_STATUSES.forEach(status -> counts.put(status, 0));
        for (ChannelQualityReviewBatchTaskStatusRow row : rows) {
            if (row == null || row.getBatchId() == null || !row.getBatchId().equals(batch.getId())
                    || !TASK_STATUSES.contains(row.getStatus()) || row.getTaskCount() == null
                    || row.getTaskCount() < 0) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            counts.put(row.getStatus(), Math.toIntExact(row.getTaskCount()));
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (total != batch.getCandidateCount()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        int active = counts.get("OPEN") + counts.get("CLAIMED") + counts.get("SUBMITTED");
        if (batch.getEffectiveDueAt() == null || active == 0) {
            return "NOT_APPLICABLE";
        }
        Instant dueAt = toInstant(batch.getEffectiveDueAt());
        if (analyticsClock.instant().isAfter(dueAt)) {
            return "OVERDUE";
        }
        return dueAt.minusSeconds(48 * 60 * 60L).isAfter(analyticsClock.instant()) ? "ON_TRACK" : "DUE_SOON";
    }

    private ResolutionRequest requireResolutionRequest(ResolutionRevisionCmd cmd) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int expectedCaseVersion = requireVersion(cmd.getExpectedCaseVersion());
        int expectedGovernanceVersion = requireVersion(cmd.getExpectedGovernanceVersion());
        String commandId = requireCommandId(cmd.getCommandId());
        String outcomeType = requireEnum(cmd.getOutcomeType(), OUTCOME_TYPES);
        String recoveryState = requireEnum(cmd.getContentRecoveryState(), RECOVERY_STATES);
        String residualRisk = requireEnum(cmd.getResidualRiskLevel(), RISK_LEVELS);
        String recoveryScope = requiredText(cmd.getRecoveryScope(), 2, 500);
        String summary = requiredText(cmd.getSummary(), 2, 1000);
        List<RootCauseRequest> rootCauses = requireRootCauses(cmd.getRootCauses());
        if (("RECOVERY_CONFIRMED".equals(outcomeType) && !"FULL".equals(recoveryState))
                || ("PARTIAL_RECOVERY".equals(outcomeType)
                && (!"PARTIAL".equals(recoveryState) || "NONE".equals(residualRisk)))
                || ("FALSE_POSITIVE_CONFIRMED".equals(outcomeType)
                && (!"NOT_APPLICABLE".equals(recoveryState) || !"NONE".equals(residualRisk)))
                || ("RISK_ACCEPTED".equals(outcomeType)
                && ("NONE".equals(residualRisk)
                || !Set.of("NOT_APPLICABLE", "NONE").contains(recoveryState)))) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("outcomeType", outcomeType);
        payload.put("contentRecoveryState", recoveryState);
        payload.put("recoveryScope", recoveryScope);
        payload.put("residualRiskLevel", residualRisk);
        payload.put("summary", summary);
        payload.put("rootCauses", rootCauses.stream().map(root -> Map.of(
                "role", root.role(), "category", root.category(), "note", root.note())).toList());
        return new ResolutionRequest(
                expectedCaseVersion, expectedGovernanceVersion, commandId, outcomeType, recoveryState,
                recoveryScope, residualRisk, summary, rootCauses, sha256(canonicalJson(payload)));
    }

    private ActionRequest requireActionRequest(ActionReferenceCmd cmd) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int expectedCaseVersion = requireVersion(cmd.getExpectedCaseVersion());
        int expectedGovernanceVersion = requireVersion(cmd.getExpectedGovernanceVersion());
        String commandId = requireCommandId(cmd.getCommandId());
        String referenceType = requireEnum(cmd.getReferenceType(), REFERENCE_TYPES);
        String referenceKey = requiredText(cmd.getReferenceKey(), 1, 160);
        Integer observedVersion = optionalVersion(cmd.getObservedVersion());
        Instant occurredAt = requireOccurredAt(cmd.getOccurredAt());
        String summary = requiredText(cmd.getSummary(), 2, 500);
        Long correction = optionalId(cmd.getCorrectionOfReferenceId());
        Map<String, Object> reference = new LinkedHashMap<>();
        reference.put("referenceType", referenceType);
        reference.put("referenceKey", referenceKey);
        reference.put("observedVersion", observedVersion);
        reference.put("occurredAt", occurredAt.toString());
        reference.put("summary", summary);
        reference.put("correctionOfReferenceId", correction);
        String canonical = canonicalJson(reference);
        return new ActionRequest(
                expectedCaseVersion, expectedGovernanceVersion, commandId, referenceType, referenceKey,
                observedVersion, occurredAt, summary, correction, sha256(canonical), sha256(canonical));
    }

    private EvidenceRequest requireEvidenceRequest(EvidenceEntryCmd cmd) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int expectedCaseVersion = requireVersion(cmd.getExpectedCaseVersion());
        int expectedGovernanceVersion = requireVersion(cmd.getExpectedGovernanceVersion());
        String commandId = requireCommandId(cmd.getCommandId());
        String evidenceType = requireEnum(cmd.getEvidenceType(), EVIDENCE_TYPES);
        String assertionType = requireEnum(cmd.getAssertionType(), ASSERTION_TYPES);
        String subjectType = requireEnum(cmd.getSubjectType(), EVIDENCE_SUBJECT_TYPES);
        String subjectRef = requiredText(cmd.getSubjectRef(), 1, 160);
        String sourceType = requireEnum(cmd.getSourceType(), EVIDENCE_SOURCE_TYPES);
        String sourceRef = requiredText(cmd.getSourceRef(), 1, 160);
        Integer sourceVersion = optionalVersion(cmd.getSourceVersion());
        Instant observedAt = requireOccurredAt(cmd.getObservedAt());
        String summary = requiredText(cmd.getSummary(), 2, 1000);
        Long correction = optionalId(cmd.getCorrectionOfEntryId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evidenceType", evidenceType);
        payload.put("assertionType", assertionType);
        payload.put("subjectType", subjectType);
        payload.put("subjectRef", subjectRef);
        payload.put("sourceType", sourceType);
        payload.put("sourceRef", sourceRef);
        payload.put("sourceVersion", sourceVersion);
        payload.put("observedAt", observedAt.toString());
        payload.put("summary", summary);
        payload.put("correctionOfEntryId", correction);
        String canonical = canonicalJson(payload);
        return new EvidenceRequest(
                expectedCaseVersion, expectedGovernanceVersion, commandId, evidenceType, assertionType,
                subjectType, subjectRef, sourceType, sourceRef, sourceVersion, observedAt, summary, correction,
                sha256(canonical), sha256(canonical));
    }

    private CloseRequest requireClosePreviewRequest(ClosePreviewCmd cmd) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return closeRequest(
                cmd.getExpectedCaseVersion(),
                cmd.getExpectedCoordinationVersion(),
                cmd.getExpectedGovernanceVersion(),
                cmd.getResolutionRevisionId(),
                cmd.getActionReferenceIds(),
                cmd.getEvidenceEntryIds(),
                cmd.getRetrospectiveOwnerUid(),
                null,
                null);
    }

    private CloseRequest requireCloseRequest(ChannelQualityReviewRiskCaseCloseCmd cmd) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return closeRequest(
                cmd.getExpectedCaseVersion(),
                cmd.getExpectedCoordinationVersion(),
                cmd.getExpectedGovernanceVersion(),
                cmd.getResolutionRevisionId(),
                cmd.getActionReferenceIds(),
                cmd.getEvidenceEntryIds(),
                cmd.getRetrospectiveOwnerUid(),
                requireCommandId(cmd.getCommandId()),
                requiredText(cmd.getNote(), 2, 500));
    }

    private CloseRequest closeRequest(
            Integer expectedCaseVersion,
            Integer expectedCoordinationVersion,
            Integer expectedGovernanceVersion,
            Long resolutionRevisionId,
            List<Long> actionReferenceIds,
            List<Long> evidenceEntryIds,
            Long retrospectiveOwnerUid,
            String commandId,
            String note) {
        int safeCaseVersion = requireVersion(expectedCaseVersion);
        int safeCoordinationVersion = requireVersion(expectedCoordinationVersion);
        int safeGovernanceVersion = requireVersion(expectedGovernanceVersion);
        long safeResolutionId = requireId(resolutionRevisionId);
        List<Long> actions = requireSelectedIds(actionReferenceIds);
        List<Long> evidence = requireSelectedIds(evidenceEntryIds);
        long ownerUid = requireId(retrospectiveOwnerUid);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resolutionRevisionId", safeResolutionId);
        payload.put("actionReferenceIds", actions);
        payload.put("evidenceEntryIds", evidence);
        payload.put("retrospectiveOwnerUid", ownerUid);
        payload.put("note", note);
        return new CloseRequest(
                safeCaseVersion, safeCoordinationVersion, safeGovernanceVersion, safeResolutionId,
                actions, evidence, ownerUid, commandId, note,
                commandId == null ? null : sha256(canonicalJson(payload)));
    }

    private RetrospectiveInitializeRequest requireRetrospectiveInitializeRequest(
            RetrospectiveInitializeCmd cmd) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (requireVersion(cmd.getExpectedRetrospectiveVersion()) != 0) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        long ownerUid = requireId(cmd.getOwnerUid());
        String commandId = requireCommandId(cmd.getCommandId());
        String note = requiredText(cmd.getNote(), 2, 1000);
        return new RetrospectiveInitializeRequest(
                ownerUid, commandId, note, sha256(canonicalJson(Map.of("ownerUid", ownerUid, "note", note))));
    }

    private RetrospectiveOwnerRequest requireRetrospectiveOwnerRequest(RetrospectiveAssignOwnerCmd cmd) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int version = requireVersion(cmd.getExpectedRetrospectiveVersion());
        long ownerUid = requireId(cmd.getOwnerUid());
        String commandId = requireCommandId(cmd.getCommandId());
        String note = requiredText(cmd.getNote(), 2, 1000);
        return new RetrospectiveOwnerRequest(version, ownerUid, commandId, note,
                sha256(canonicalJson(Map.of("ownerUid", ownerUid, "note", note))));
    }

    private RetrospectiveMutationRequest requireRetrospectiveStartRequest(RetrospectiveStartCmd cmd) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int version = requireVersion(cmd.getExpectedRetrospectiveVersion());
        String commandId = requireCommandId(cmd.getCommandId());
        String note = requiredText(cmd.getNote(), 2, 1000);
        return new RetrospectiveMutationRequest(version, commandId, note,
                sha256(canonicalJson(Map.of("note", note))));
    }

    private RetrospectiveFindingRequest requireRetrospectiveFindingRequest(RetrospectiveFindingCmd cmd) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int version = requireVersion(cmd.getExpectedRetrospectiveVersion());
        String commandId = requireCommandId(cmd.getCommandId());
        String category = requireEnum(cmd.getLearningCategory(), LEARNING_CATEGORIES);
        String finding = requiredText(cmd.getFindingSummary(), 2, 1000);
        String prevention = requiredText(cmd.getPreventionActionSummary(), 2, 1000);
        String note = requiredText(cmd.getNote(), 2, 1000);
        return new RetrospectiveFindingRequest(version, commandId, note,
                sha256(canonicalJson(Map.of(
                        "learningCategory", category,
                        "findingSummary", finding,
                        "preventionActionSummary", prevention,
                        "note", note))),
                category, finding, prevention);
    }

    private RetrospectiveMutationRequest requireRetrospectiveCompleteRequest(RetrospectiveCompleteCmd cmd) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int version = requireVersion(cmd.getExpectedRetrospectiveVersion());
        String commandId = requireCommandId(cmd.getCommandId());
        String note = requiredText(cmd.getNote(), 2, 1000);
        return new RetrospectiveMutationRequest(version, commandId, note,
                sha256(canonicalJson(Map.of("note", note))));
    }

    private RecurrenceRequest requireRecurrenceRequest(RecurrenceLinkCmd cmd) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int expectedVersion = requireVersion(cmd.getExpectedCurrentCaseVersion());
        long previousCaseId = requireId(cmd.getPreviousCaseId());
        String relationType = requireEnum(cmd.getRelationType(), RECURRENCE_TYPES);
        String rootCauseCategory = requireEnum(cmd.getRootCauseCategory(), ROOT_CAUSES);
        String commandId = requireCommandId(cmd.getCommandId());
        String note = requiredText(cmd.getNote(), 2, 500);
        return new RecurrenceRequest(expectedVersion, previousCaseId, relationType, rootCauseCategory,
                commandId, note, sha256(canonicalJson(Map.of(
                        "previousCaseId", previousCaseId,
                        "relationType", relationType,
                        "rootCauseCategory", rootCauseCategory,
                        "note", note))));
    }

    private static List<RootCauseRequest> requireRootCauses(
            List<com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.RootCauseCmd> values) {
        if (values == null || values.isEmpty() || values.size() > 6) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        List<RootCauseRequest> result = new ArrayList<>();
        int primary = 0;
        int contributors = 0;
        for (com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.RootCauseCmd value : values) {
            if (value == null) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
            String role = requireEnum(value.getRole(), Set.of("PRIMARY", "CONTRIBUTING"));
            String category = requireEnum(value.getCategory(), ROOT_CAUSES);
            String note = requiredText(value.getNote(), 2, 500);
            if ("PRIMARY".equals(role)) {
                primary++;
            } else {
                contributors++;
            }
            result.add(new RootCauseRequest(role, category, note));
        }
        if (primary != 1 || contributors > 5) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return result;
    }

    private static V40LifecycleFact requireV40LifecycleEvent(
            ChannelQualityReviewRiskCaseV40LifecycleEvent value,
            ChannelQualityReviewRiskCaseRow lockedCase) {
        if (value == null || value.eventId() == null || value.eventId() <= 0
                || !Set.of("OWNER_ASSIGNED", "OWNER_ACKNOWLEDGED", "PLAN_RECORDED")
                .contains(value.eventType()) || value.status() == null
                || !CASE_STATUSES.contains(value.status()) || value.caseVersion() == null
                || value.caseVersion() < 1 || value.occurredAt() == null
                || value.caseVersion() > lockedCase.getCaseVersion()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if ("OWNER_ASSIGNED".equals(value.eventType())
                && (value.assignedOwnerUid() == null || value.assignedOwnerUid() <= 0)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return new V40LifecycleFact(
                value.eventId(), value.eventType(), value.status(), value.assignedOwnerUid(),
                value.caseVersion(), value.occurredAt());
    }

    private static void requireCaseVersion(
            Integer expectedVersion, ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (!expectedVersion.equals(riskCase.getCaseVersion())) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private static void requireGovernanceVersion(
            Integer expectedVersion, ChannelQualityReviewRiskCaseGovernanceRow governance) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (!expectedVersion.equals(governance.getGovernanceVersion())) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private static void requireRetrospectiveVersion(
            Integer expectedVersion, ChannelQualityReviewRiskCaseRetrospectiveRow retrospective) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (!expectedVersion.equals(retrospective.getRetrospectiveVersion())) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private void audit(Long operatorUid, Long caseId, String action, Map<String, Object> after) {
        adminAuditService.recordRequired(
                operatorUid,
                action,
                "CHANNEL_QUALITY_REVIEW_RISK_CASE",
                caseId,
                Map.of(),
                after,
                "channel quality risk-case governance updated");
    }

    private static boolean legacyClosed(ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase) {
        return "CLOSED".equals(riskCase.getStatus())
                && Integer.valueOf(1).equals(riskCase.getLegacyClosedWithoutSnapshot());
    }

    private String governanceEtag(
            ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase,
            ChannelQualityReviewBatchCoordinationRow batch,
            ChannelQualityReviewRiskCaseGovernanceRow governance,
            int actionCount,
            int evidenceCount,
            ChannelQualityReviewRiskCaseRetrospectiveRow retrospective) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", riskCase.getId());
        payload.put("caseVersion", riskCase.getCaseVersion());
        payload.put("coordinationVersion", batch.getCoordinationVersion());
        payload.put("governanceVersion", governance.getGovernanceVersion());
        payload.put("currentResolutionRevisionId", governance.getCurrentResolutionRevisionId());
        payload.put("actionReferenceCount", actionCount);
        payload.put("evidenceCount", evidenceCount);
        payload.put("closeSnapshotId", riskCase.getV41CloseSnapshotId());
        payload.put("retrospectiveId", retrospective == null ? null : retrospective.getId());
        payload.put("retrospectiveVersion", retrospective == null ? null : retrospective.getRetrospectiveVersion());
        payload.put("retrospectiveStatus", retrospective == null ? null : retrospective.getStatus());
        return sha256(canonicalJson(payload));
    }

    private String canonicalJson(Object payload) {
        try {
            return objectMapper.copy()
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                    .writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String requiredText(String value, int min, int max) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String normalized = value.trim();
        if (normalized.length() < min || normalized.length() > max
                || normalized.chars().anyMatch(Character::isISOControl)
                || SENSITIVE_TEXT.matcher(normalized).find()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static String requireEnum(String value, Set<String> allowed) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static String requireCommandId(String value) {
        String normalized = requiredText(value, 1, 64);
        if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static boolean stableCommandId(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
    }

    private static boolean stableFingerprint(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static boolean stableCode(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{0,63}");
    }

    private static int requireVersion(Integer value) {
        if (value == null || value < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static Integer optionalVersion(Integer value) {
        if (value != null && value < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static long requireId(Long value) {
        if (value == null || value <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static Long optionalId(Long value) {
        if (value != null && value <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static int requireDomain(Integer value) {
        if (value == null || value < 1 || value > 5) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private Instant requireOccurredAt(Instant value) {
        if (value == null || value.isAfter(analyticsClock.instant().plusSeconds(60))) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static void requireOperator(Long value) {
        if (value == null || value <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static int pageSize(Integer value) {
        int result = value == null ? DEFAULT_PAGE_SIZE : value;
        if (result < 1 || result > MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return result;
    }

    private static List<Long> requireSelectedIds(List<Long> values) {
        if (values == null || values.isEmpty() || values.size() > MAX_SELECTED_ITEMS) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        List<Long> normalized = values.stream().map(ChannelQualityReviewRiskCaseGovernanceService::requireId)
                .sorted().toList();
        if (new HashSet<>(normalized).size() != normalized.size()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static long decodeCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return 0L;
        }
        try {
            long value = Long.parseLong(new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
            if (value <= 0) {
                throw new IllegalArgumentException();
            }
            return value;
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static String encodeCursor(long id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Long.toString(id).getBytes(StandardCharsets.UTF_8));
    }

    private static LocalDateTime toLocal(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static long parseInternalReference(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static String requiredJsonText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || !StringUtils.hasText(value.asText())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return value.asText();
    }

    private static int requiredJsonInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return value.intValue();
    }

    private static Integer optionalJsonInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : requiredJsonInt(node, field);
    }

    private static long requiredJsonLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong() || value.longValue() <= 0) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return value.longValue();
    }

    private static Long optionalJsonLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : requiredJsonLong(node, field);
    }

    private <R, T> ChannelQualityReviewRiskCaseGovernanceCursorPageDTO<T> cursorPage(
            List<R> rows,
            int pageSize,
            java.util.function.Consumer<R> validator,
            java.util.function.Function<R, T> mapper) {
        if (rows == null || rows.size() > pageSize + 1) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        List<R> visible = rows.stream().limit(pageSize).toList();
        long previous = Long.MAX_VALUE;
        for (R row : visible) {
            validator.accept(row);
            long id = extractId(row);
            if (id >= previous) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            previous = id;
        }
        return ChannelQualityReviewRiskCaseGovernanceCursorPageDTO.<T>builder()
                .nextCursor(rows.size() > pageSize && !visible.isEmpty()
                        ? encodeCursor(extractId(visible.get(visible.size() - 1)))
                        : null)
                .items(visible.stream().map(mapper).toList())
                .build();
    }

    private static long extractId(Object value) {
        if (value instanceof ChannelQualityReviewRiskCaseGovernanceMilestoneRow row) {
            return requireId(row.getId());
        }
        if (value instanceof ChannelQualityReviewRiskCaseResolutionRevisionRow row) {
            return requireId(row.getId());
        }
        if (value instanceof ChannelQualityReviewRiskCaseActionReferenceRow row) {
            return requireId(row.getId());
        }
        if (value instanceof ChannelQualityReviewRiskCaseEvidenceEntryRow row) {
            return requireId(row.getId());
        }
        if (value instanceof ChannelQualityReviewRiskCaseRetrospectiveEventRow row) {
            return requireId(row.getId());
        }
        if (value instanceof ChannelQualityReviewRiskCaseRecurrenceLinkRow row) {
            return requireId(row.getId());
        }
        throw new BizException(ErrorCode.DEPENDENCY_ERROR);
    }

    private record CaseAggregate(
            ChannelQualityReviewRiskCaseGovernanceCaseRow riskCase,
            ChannelQualityReviewBatchCoordinationRow batch,
            ChannelQualityReviewRiskCaseGovernanceRow governance) {
    }

    private record RootCauseRequest(String role, String category, String note) {
    }

    private record ResolutionRequest(
            int expectedCaseVersion,
            int expectedGovernanceVersion,
            String commandId,
            String outcomeType,
            String contentRecoveryState,
            String recoveryScope,
            String residualRiskLevel,
            String summary,
            List<RootCauseRequest> rootCauses,
            String commandFingerprint) {
    }

    private record ActionRequest(
            int expectedCaseVersion,
            int expectedGovernanceVersion,
            String commandId,
            String referenceType,
            String referenceKey,
            Integer observedVersion,
            Instant occurredAt,
            String summary,
            Long correctionOfReferenceId,
            String referenceFingerprint,
            String commandFingerprint) {
    }

    private record EvidenceRequest(
            int expectedCaseVersion,
            int expectedGovernanceVersion,
            String commandId,
            String evidenceType,
            String assertionType,
            String subjectType,
            String subjectRef,
            String sourceType,
            String sourceRef,
            Integer sourceVersion,
            Instant observedAt,
            String summary,
            Long correctionOfEntryId,
            String evidenceDigest,
            String commandFingerprint) {
    }

    private record CloseRequest(
            int expectedCaseVersion,
            int expectedCoordinationVersion,
            int expectedGovernanceVersion,
            long resolutionRevisionId,
            List<Long> actionReferenceIds,
            List<Long> evidenceEntryIds,
            long retrospectiveOwnerUid,
            String commandId,
            String note,
            String commandFingerprint) {
    }

    private record CloseSelection(
            ChannelQualityReviewRiskCaseResolutionRevisionRow resolution,
            List<ChannelQualityReviewRiskCaseResolutionRootCauseRow> rootCauses,
            List<ChannelQualityReviewRiskCaseActionReferenceRow> actionReferences,
            List<ChannelQualityReviewRiskCaseEvidenceEntryRow> evidenceEntries,
            List<CheckedCloseResult> checks) {
    }

    private record CheckedCloseResult(
            String code,
            int version,
            int order,
            ChannelQualityRiskCaseCloseCheckResult result) {
    }

    private record V40LifecycleFact(
            long eventId,
            String eventType,
            String status,
            Long assignedOwnerUid,
            int caseVersion,
            Instant occurredAt) {
    }

    private record RetrospectiveInitializeRequest(
            long ownerUid, String commandId, String note, String commandFingerprint) {
    }

    private record RetrospectiveOwnerRequest(
            int expectedRetrospectiveVersion,
            long ownerUid,
            String commandId,
            String note,
            String commandFingerprint) {
    }

    private record RetrospectiveMutationRequest(
            int expectedRetrospectiveVersion, String commandId, String note, String commandFingerprint) {
    }

    private record RetrospectiveFindingRequest(
            int expectedRetrospectiveVersion,
            String commandId,
            String note,
            String commandFingerprint,
            String learningCategory,
            String findingSummary,
            String preventionActionSummary) {
    }

    private record RecurrenceRequest(
            int expectedCurrentCaseVersion,
            long previousCaseId,
            String relationType,
            String rootCauseCategory,
            String commandId,
            String note,
            String commandFingerprint) {
    }

}
