package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.ChannelQualityRiskGovernanceSnapshotQueryFacade;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityRiskGovernanceSnapshotActionRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityRiskGovernanceSnapshotBaseRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityRiskGovernanceSnapshotEvidenceRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityRiskGovernanceSnapshotLearningRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityRiskGovernanceSnapshotQueryMapper;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityRiskGovernanceSnapshotRecurrenceRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityRiskGovernanceSnapshotRootCauseRow;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChannelQualityRiskGovernanceSnapshotQueryFacadeImpl
        implements ChannelQualityRiskGovernanceSnapshotQueryFacade {

    private static final String SCHEMA_VERSION = "V41_GOVERNANCE_SNAPSHOT_V1";
    private static final String ETAG_PREFIX = "sha256:";
    private static final Set<String> CASE_STATUSES =
            Set.of("OPEN", "ACKNOWLEDGED", "IN_PROGRESS", "RESOLVED", "CLOSED");
    private static final Set<String> OUTCOME_TYPES = Set.of(
            "RECOVERY_CONFIRMED", "PARTIAL_RECOVERY", "RISK_CONTAINED",
            "FALSE_POSITIVE_CONFIRMED", "RISK_ACCEPTED");
    private static final Set<String> RECOVERY_STATES =
            Set.of("NOT_APPLICABLE", "NONE", "PARTIAL", "FULL");
    private static final Set<String> RISK_LEVELS = Set.of("NONE", "LOW", "MEDIUM", "HIGH");
    private static final Set<String> ROOT_CAUSE_CATEGORIES = Set.of(
            "PROCESS_GAP", "CONTENT_DEFECT", "CAPACITY_CONSTRAINT", "POLICY_AMBIGUITY",
            "TOOLING_FAILURE", "COMMUNICATION_GAP", "DATA_QUALITY", "EXTERNAL_DEPENDENCY",
            "UNKNOWN");
    private static final Set<String> ACTION_TYPES = Set.of(
            "V39_COORDINATION_EVENT", "V40_CASE_EVENT", "MAINTENANCE_TASK",
            "CONTENT_REVISION", "GOVERNANCE_DECISION", "EXTERNAL_TICKET");
    private static final Set<String> EVIDENCE_TYPES = Set.of(
            "CONTENT_STATE_OBSERVATION", "QUALITY_RECHECK", "TASK_DELIVERY_RECEIPT",
            "COORDINATION_CONFIRMATION", "POLICY_DECISION", "EXTERNAL_CONFIRMATION");
    private static final Set<String> ASSERTION_TYPES = Set.of(
            "SUPPORTS_RECOVERY", "REFUTES_RECOVERY", "SUPPORTS_PARTIAL_RECOVERY",
            "SUPPORTS_CONTAINMENT", "SUPPORTS_FALSE_POSITIVE",
            "SUPPORTS_RISK_ACCEPTANCE", "UNCERTAIN");
    private static final Set<String> RECURRENCE_TYPES = Set.of(
            "SAME_ROOT_CAUSE", "SAME_CHANNEL_PATTERN", "SAME_BATCH_PATTERN", "MANUAL_RELATED");
    private static final Set<String> LEARNING_CATEGORIES = Set.of(
            "PROCESS", "QUALITY", "CAPACITY", "POLICY", "TOOLING", "COMMUNICATION",
            "DATA", "EXTERNAL", "OTHER");

    private final ChannelQualityRiskGovernanceSnapshotQueryMapper snapshotMapper;
    private final List<ChannelQualityRiskCaseCloseCheckProvider> closeCheckProviders;

    @Override
    @Transactional(readOnly = true)
    public V41GovernanceSnapshot getSnapshot(Long caseId) {
        long safeCaseId = requireId(caseId);
        try {
            ChannelQualityRiskGovernanceSnapshotBaseRow base = snapshotMapper.selectBase(safeCaseId);
            requireBase(base, safeCaseId);
            List<ChannelQualityRiskGovernanceSnapshotRootCauseRow> rootCauses =
                    base.getCurrentResolutionRevisionId() == null
                            ? List.of()
                            : requireRows(snapshotMapper.listRootCauses(
                                    safeCaseId, base.getCurrentResolutionRevisionId()));
            List<ChannelQualityRiskGovernanceSnapshotActionRow> actions =
                    requireRows(snapshotMapper.listActions(safeCaseId));
            List<ChannelQualityRiskGovernanceSnapshotEvidenceRow> evidence =
                    requireRows(snapshotMapper.listEvidence(safeCaseId));
            List<ChannelQualityRiskGovernanceSnapshotRecurrenceRow> recurrenceLinks =
                    requireRows(snapshotMapper.listRecurrenceLinks(safeCaseId));
            List<ChannelQualityRiskGovernanceSnapshotLearningRow> learning =
                    base.getRetrospectiveId() == null
                            ? List.of()
                            : requireRows(snapshotMapper.listLearningCategories(base.getRetrospectiveId()));
            return toSnapshot(base, rootCauses, actions, evidence, learning, recurrenceLinks);
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private V41GovernanceSnapshot toSnapshot(
            ChannelQualityRiskGovernanceSnapshotBaseRow base,
            List<ChannelQualityRiskGovernanceSnapshotRootCauseRow> rootCauses,
            List<ChannelQualityRiskGovernanceSnapshotActionRow> actions,
            List<ChannelQualityRiskGovernanceSnapshotEvidenceRow> evidence,
            List<ChannelQualityRiskGovernanceSnapshotLearningRow> learning,
            List<ChannelQualityRiskGovernanceSnapshotRecurrenceRow> recurrenceLinks) {
        validateRootCauses(base, rootCauses);
        List<String> rootCauseCategories = rootCauses.stream()
                .map(ChannelQualityRiskGovernanceSnapshotRootCauseRow::getCategory)
                .toList();
        validateActions(base.getCaseId(), actions);
        validateEvidence(base.getCaseId(), evidence);
        validateLearning(base.getRetrospectiveId(), learning);
        validateRecurrence(base.getCaseId(), base.getDomain(), recurrenceLinks);
        validateCloseShape(base, rootCauseCategories);

        List<ActionRef> actionRefs = actions.stream()
                .map(this::toActionRef)
                .toList();
        List<EvidenceRef> evidenceRefs = evidence.stream()
                .map(this::toEvidenceRef)
                .toList();
        List<String> learningCategories = distinctLearningCategories(learning);
        List<RecurrenceLink> safeRecurrenceLinks = recurrenceLinks.stream()
                .map(this::toRecurrenceLink)
                .toList();
        SnapshotTime resolutionSubmittedAt = SnapshotTime.unavailable(
                base.getCloseSnapshotId() == null
                        ? "NOT_CLOSED"
                        : "NO_STABLE_EVENT_BINDING");
        SnapshotTime closedAt = base.getClosedAt() == null
                ? SnapshotTime.unavailable("NO_CLOSE_SNAPSHOT")
                : SnapshotTime.available(toInstant(base.getClosedAt()));
        String etag = governanceSnapshotEtag(
                base, rootCauseCategories, actionRefs, evidenceRefs,
                learningCategories, safeRecurrenceLinks, resolutionSubmittedAt, closedAt);

        return new V41GovernanceSnapshot(
                SCHEMA_VERSION,
                base.getCaseId(),
                base.getBatchId(),
                base.getDomain(),
                base.getCaseStatus(),
                base.getCaseVersion(),
                base.getCoordinationVersion(),
                base.getGovernanceFactVersion(),
                etag,
                base.getCurrentResolutionRevisionId(),
                base.getOutcomeType(),
                base.getContentRecoveryState(),
                base.getResidualRiskLevel(),
                rootCauseCategories.isEmpty() ? null : rootCauseCategories.get(0),
                rootCauseCategories,
                actionRefs,
                evidenceRefs,
                base.getCloseSnapshotId(),
                resolutionSubmittedAt,
                closedAt,
                base.getPayloadSchemaVersion(),
                base.getSnapshotDigest(),
                base.getRetrospectiveId(),
                base.getRetrospectiveStatus(),
                base.getRetrospectiveVersion(),
                learningCategories,
                safeRecurrenceLinks,
                closeCheckExtensionAvailability());
    }

    private static void requireBase(ChannelQualityRiskGovernanceSnapshotBaseRow row, long caseId) {
        if (row == null || !Long.valueOf(caseId).equals(row.getCaseId())
                || !positive(row.getBatchId()) || row.getDomain() == null
                || row.getDomain() < 1 || row.getDomain() > 5
                || !CASE_STATUSES.contains(row.getCaseStatus())
                || row.getCaseVersion() == null || row.getCaseVersion() < 0
                || row.getCoordinationVersion() == null || row.getCoordinationVersion() < 0
                || row.getGovernanceFactVersion() == null || row.getGovernanceFactVersion() < 0) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (row.getCurrentResolutionRevisionId() == null
                && (row.getOutcomeType() != null || row.getContentRecoveryState() != null
                || row.getResidualRiskLevel() != null)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (row.getCurrentResolutionRevisionId() != null
                && (!OUTCOME_TYPES.contains(row.getOutcomeType())
                || !RECOVERY_STATES.contains(row.getContentRecoveryState())
                || !RISK_LEVELS.contains(row.getResidualRiskLevel()))) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (row.getRetrospectiveId() == null
                && (row.getRetrospectiveStatus() != null || row.getRetrospectiveVersion() != null)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (row.getRetrospectiveId() != null
                && (!positive(row.getRetrospectiveId())
                || !Set.of("PENDING", "IN_PROGRESS", "COMPLETED").contains(row.getRetrospectiveStatus())
                || row.getRetrospectiveVersion() == null || row.getRetrospectiveVersion() < 0)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private static void validateRootCauses(
            ChannelQualityRiskGovernanceSnapshotBaseRow base,
            List<ChannelQualityRiskGovernanceSnapshotRootCauseRow> rows) {
        if (rows == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (base.getCurrentResolutionRevisionId() == null && !rows.isEmpty()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (base.getCurrentResolutionRevisionId() != null) {
            if (rows.isEmpty() || rows.size() > 6) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            int expectedSequence = 0;
            int primaryCount = 0;
            for (ChannelQualityRiskGovernanceSnapshotRootCauseRow row : rows) {
                if (row == null || !positive(row.getId())
                        || !base.getCaseId().equals(row.getCaseId())
                        || !base.getCurrentResolutionRevisionId().equals(row.getResolutionRevisionId())
                        || !StringUtils.hasText(row.getCauseRole())
                        || !ROOT_CAUSE_CATEGORIES.contains(row.getCategory())
                        || row.getSequenceNo() == null
                        || row.getSequenceNo() != expectedSequence) {
                    throw new BizException(ErrorCode.DEPENDENCY_ERROR);
                }
                if (expectedSequence == 0) {
                    if (!"PRIMARY".equals(row.getCauseRole())) {
                        throw new BizException(ErrorCode.DEPENDENCY_ERROR);
                    }
                    primaryCount++;
                } else if (!"CONTRIBUTING".equals(row.getCauseRole())) {
                    throw new BizException(ErrorCode.DEPENDENCY_ERROR);
                }
                expectedSequence++;
            }
            if (primaryCount != 1) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
        }
    }

    private static void validateActions(
            Long caseId, List<ChannelQualityRiskGovernanceSnapshotActionRow> rows) {
        if (rows == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        long previousId = 0;
        for (ChannelQualityRiskGovernanceSnapshotActionRow row : rows) {
            if (row == null || !positive(row.getId()) || row.getId() <= previousId
                    || !caseId.equals(row.getCaseId()) || !ACTION_TYPES.contains(row.getReferenceType())
                    || (row.getObservedVersion() != null && row.getObservedVersion() < 0)
                    || row.getOccurredAt() == null || !StringUtils.hasText(row.getSummary())) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            previousId = row.getId();
        }
    }

    private static void validateEvidence(
            Long caseId, List<ChannelQualityRiskGovernanceSnapshotEvidenceRow> rows) {
        if (rows == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        long previousId = 0;
        for (ChannelQualityRiskGovernanceSnapshotEvidenceRow row : rows) {
            if (row == null || !positive(row.getId()) || row.getId() <= previousId
                    || !caseId.equals(row.getCaseId()) || !EVIDENCE_TYPES.contains(row.getEvidenceType())
                    || !ASSERTION_TYPES.contains(row.getAssertionType())
                    || !StringUtils.hasText(row.getSubjectType())
                    || !StringUtils.hasText(row.getSourceType())
                    || (row.getSourceVersion() != null && row.getSourceVersion() < 0)
                    || row.getObservedAt() == null || !StringUtils.hasText(row.getSummary())) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            previousId = row.getId();
        }
    }

    private static void validateLearning(
            Long retrospectiveId, List<ChannelQualityRiskGovernanceSnapshotLearningRow> rows) {
        if (rows == null || (retrospectiveId == null && !rows.isEmpty())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        long previousTime = Long.MIN_VALUE;
        for (ChannelQualityRiskGovernanceSnapshotLearningRow row : rows) {
            if (row == null || !retrospectiveId.equals(row.getRetrospectiveId())
                    || !LEARNING_CATEGORIES.contains(row.getLearningCategory())
                    || row.getOccurredAt() == null
                    || row.getOccurredAt().toInstant(ZoneOffset.UTC).toEpochMilli() < previousTime) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            previousTime = row.getOccurredAt().toInstant(ZoneOffset.UTC).toEpochMilli();
        }
    }

    private static void validateRecurrence(
            Long caseId,
            Integer domain,
            List<ChannelQualityRiskGovernanceSnapshotRecurrenceRow> rows) {
        if (rows == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        long previousId = 0;
        for (ChannelQualityRiskGovernanceSnapshotRecurrenceRow row : rows) {
            if (row == null || !positive(row.getId()) || row.getId() <= previousId
                    || !caseId.equals(row.getCurrentCaseId()) || !positive(row.getPreviousCaseId())
                    || caseId.equals(row.getPreviousCaseId()) || !RECURRENCE_TYPES.contains(row.getRelationType())
                    || !ROOT_CAUSE_CATEGORIES.contains(row.getRootCauseCategory())
                    || row.getLinkedAt() == null || !validDigest(row.getCommandFingerprint())) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            previousId = row.getId();
        }
        if (domain == null || domain < 1 || domain > 5) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private static void validateCloseShape(
            ChannelQualityRiskGovernanceSnapshotBaseRow base,
            List<String> rootCauseCategories) {
        boolean hasSnapshot = base.getCloseSnapshotId() != null;
        if (!hasSnapshot) {
            if (base.getClosedCaseVersion() != null || base.getClosedCoordinationVersion() != null
                    || base.getClosedGovernanceVersion() != null || base.getPayloadSchemaVersion() != null
                    || base.getSnapshotDigest() != null || base.getClosedAt() != null
                    || base.getSnapshotPrimaryRootCause() != null) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            return;
        }
        if (!"CLOSED".equals(base.getCaseStatus())
                || base.getClosedCaseVersion() == null || base.getClosedCaseVersion() < 1
                || !base.getCaseVersion().equals(base.getClosedCaseVersion())
                || !base.getClosedCoordinationVersion().equals(base.getCoordinationVersion())
                || !base.getClosedGovernanceVersion().equals(base.getGovernanceFactVersion())
                || base.getPayloadSchemaVersion() == null || base.getPayloadSchemaVersion() != 1
                || !validDigest(base.getSnapshotDigest()) || base.getClosedAt() == null
                || rootCauseCategories.isEmpty()
                || !rootCauseCategories.get(0).equals(base.getSnapshotPrimaryRootCause())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private ActionRef toActionRef(ChannelQualityRiskGovernanceSnapshotActionRow row) {
        return new ActionRef(
                row.getId(),
                row.getReferenceType(),
                row.getObservedVersion(),
                SnapshotTime.available(toInstant(row.getOccurredAt())),
                sha256(row.getSummary()));
    }

    private EvidenceRef toEvidenceRef(ChannelQualityRiskGovernanceSnapshotEvidenceRow row) {
        return new EvidenceRef(
                row.getId(),
                row.getEvidenceType(),
                row.getAssertionType(),
                row.getSubjectType(),
                row.getSourceType(),
                row.getSourceVersion(),
                SnapshotTime.available(toInstant(row.getObservedAt())),
                sha256(row.getSummary()));
    }

    private RecurrenceLink toRecurrenceLink(ChannelQualityRiskGovernanceSnapshotRecurrenceRow row) {
        return new RecurrenceLink(
                row.getId(),
                row.getPreviousCaseId(),
                row.getRelationType(),
                row.getRootCauseCategory(),
                SnapshotTime.available(toInstant(row.getLinkedAt())),
                sha256(String.join("|",
                        Long.toString(row.getId()),
                        Long.toString(row.getCurrentCaseId()),
                        Long.toString(row.getPreviousCaseId()),
                        row.getRelationType(),
                        row.getRootCauseCategory(),
                        row.getLinkedAt().toString())));
    }

    private static List<String> distinctLearningCategories(
            List<ChannelQualityRiskGovernanceSnapshotLearningRow> rows) {
        return new ArrayList<>(rows.stream()
                .map(ChannelQualityRiskGovernanceSnapshotLearningRow::getLearningCategory)
                .collect(LinkedHashSet::new, Set::add, Set::addAll));
    }

    private CloseCheckExtensionAvailability closeCheckExtensionAvailability() {
        if (closeCheckProviders == null || closeCheckProviders.isEmpty()) {
            return new CloseCheckExtensionAvailability("UNAVAILABLE", true, List.of());
        }
        try {
            List<ProviderContract> providers = closeCheckProviders.stream()
                    .map(provider -> new ProviderContract(
                            provider.code(), provider.contractVersion(), provider.order()))
                    .toList();
            Set<String> codes = new HashSet<>();
            for (ProviderContract provider : providers) {
                if (!StringUtils.hasText(provider.code())
                        || !provider.code().matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")
                        || provider.contractVersion() < 1
                        || provider.order() < 0
                        || !codes.add(provider.code())) {
                    return new CloseCheckExtensionAvailability("UNAVAILABLE", true, List.of());
                }
            }
            return new CloseCheckExtensionAvailability("AVAILABLE", false, providers);
        } catch (RuntimeException ex) {
            return new CloseCheckExtensionAvailability("UNAVAILABLE", true, List.of());
        }
    }

    private static String governanceSnapshotEtag(
            ChannelQualityRiskGovernanceSnapshotBaseRow base,
            List<String> rootCauseCategories,
            List<ActionRef> actions,
            List<EvidenceRef> evidence,
            List<String> learningCategories,
            List<RecurrenceLink> recurrenceLinks,
            SnapshotTime resolutionSubmittedAt,
            SnapshotTime closedAt) {
        StringBuilder value = new StringBuilder();
        append(value, SCHEMA_VERSION);
        append(value, base.getCaseId());
        append(value, base.getBatchId());
        append(value, base.getDomain());
        append(value, base.getCaseStatus());
        append(value, base.getCaseVersion());
        append(value, base.getCoordinationVersion());
        append(value, base.getGovernanceFactVersion());
        append(value, base.getCurrentResolutionRevisionId());
        append(value, base.getOutcomeType());
        append(value, base.getContentRecoveryState());
        append(value, base.getResidualRiskLevel());
        rootCauseCategories.forEach(item -> append(value, item));
        actions.forEach(item -> {
            append(value, item.actionId());
            append(value, item.referenceType());
            append(value, item.observedVersion());
            appendTime(value, item.occurredAt());
            append(value, item.summaryDigest());
        });
        evidence.forEach(item -> {
            append(value, item.evidenceId());
            append(value, item.evidenceType());
            append(value, item.assertionType());
            append(value, item.subjectType());
            append(value, item.sourceType());
            append(value, item.sourceVersion());
            appendTime(value, item.observedAt());
            append(value, item.summaryDigest());
        });
        append(value, base.getCloseSnapshotId());
        appendTime(value, resolutionSubmittedAt);
        appendTime(value, closedAt);
        append(value, base.getPayloadSchemaVersion());
        append(value, base.getSnapshotDigest());
        append(value, base.getRetrospectiveId());
        append(value, base.getRetrospectiveStatus());
        append(value, base.getRetrospectiveVersion());
        learningCategories.forEach(item -> append(value, item));
        recurrenceLinks.stream()
                .sorted((left, right) -> left.linkId().compareTo(right.linkId()))
                .forEach(item -> {
                    append(value, item.linkId());
                    append(value, item.previousCaseId());
                    append(value, item.relationType());
                    append(value, item.rootCauseCategory());
                    appendTime(value, item.linkedAt());
                    append(value, item.linkDigest());
                });
        return ETAG_PREFIX + sha256(value.toString());
    }

    private static void append(StringBuilder builder, Object value) {
        String normalized = value == null ? "<null>" : value.toString();
        builder.append(normalized.length()).append(':').append(normalized).append('|');
    }

    private static void appendTime(StringBuilder builder, SnapshotTime value) {
        append(builder, value == null ? null : value.availability());
        append(builder, value == null || value.value() == null ? null : value.value().toString());
        append(builder, value == null ? null : value.reasonCode());
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }

    private static boolean validDigest(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static long requireId(Long value) {
        if (!positive(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static Instant toInstant(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }

    private static <T> List<T> requireRows(List<T> rows) {
        if (rows == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return rows;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
