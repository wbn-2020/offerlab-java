package com.offerlab.community.analytics.application;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Immutable, permission-filtered inputs for deterministic close checks.
 */
public record ChannelQualityRiskCaseCloseCheckContext(
        RiskCase riskCase,
        Batch batch,
        Governance governance,
        ResolutionRevision resolutionRevision,
        List<RootCause> rootCauses,
        List<ActionReference> actionReferences,
        List<EvidenceEntry> evidenceEntries,
        Long retrospectiveOwnerUid,
        String dueState,
        Instant evaluatedAt) {

    public ChannelQualityRiskCaseCloseCheckContext {
        rootCauses = rootCauses == null ? List.of() : List.copyOf(rootCauses);
        actionReferences = actionReferences == null ? List.of() : List.copyOf(actionReferences);
        evidenceEntries = evidenceEntries == null ? List.of() : List.copyOf(evidenceEntries);
    }

    public record RiskCase(
            Long id,
            Long batchId,
            Integer domain,
            String triggerType,
            String status,
            Long ownerUid,
            Integer caseVersion,
            Integer openedCoordinationVersion,
            Long closeSnapshotId,
            Integer legacyClosedWithoutSnapshot,
            LocalDateTime createTime,
            LocalDateTime updateTime) {
    }

    public record Batch(
            Long id,
            Integer domain,
            String sourceType,
            String name,
            Long assigneeUid,
            LocalDateTime dueAt,
            LocalDateTime effectiveDueAt,
            Integer coordinationVersion,
            Integer candidateCount,
            LocalDateTime createTime) {
    }

    public record Governance(
            Long caseId,
            Long batchId,
            Integer domain,
            Integer governanceVersion,
            Long currentResolutionRevisionId,
            LocalDateTime createTime,
            LocalDateTime updateTime) {
    }

    public record ResolutionRevision(
            Long id,
            Integer revisionNo,
            String outcomeType,
            String contentRecoveryState,
            String recoveryScope,
            String residualRiskLevel,
            String summary,
            LocalDateTime createTime) {
    }

    public record RootCause(
            Long id,
            String causeRole,
            String category,
            Integer sequenceNo,
            String note) {
    }

    public record ActionReference(
            Long id,
            String referenceType,
            Integer observedVersion,
            LocalDateTime occurredAt,
            String summary,
            Long correctionOfReferenceId) {
    }

    public record EvidenceEntry(
            Long id,
            String evidenceType,
            String assertionType,
            String subjectType,
            String sourceType,
            Integer sourceVersion,
            LocalDateTime observedAt,
            String summary,
            Long correctionOfEntryId) {
    }
}
