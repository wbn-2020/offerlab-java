package com.offerlab.community.analytics.api;

import java.time.Instant;
import java.util.List;

/**
 * Stable V41 read boundary for V43 and V44.
 *
 * <p>Consumers must use this semantic contract instead of querying V41 storage.</p>
 */
public interface ChannelQualityRiskGovernanceSnapshotQueryFacade {

    V41GovernanceSnapshot getSnapshot(Long caseId);

    record V41GovernanceSnapshot(
            String schemaVersion,
            Long caseId,
            Long batchId,
            Integer domain,
            String caseStatus,
            Integer caseVersion,
            Integer coordinationVersion,
            Integer governanceFactVersion,
            String governanceSnapshotEtag,
            Long currentResolutionRevisionId,
            String outcomeType,
            String contentRecoveryState,
            String residualRiskLevel,
            String primaryRootCauseCategory,
            List<String> rootCauseCategories,
            List<ActionRef> actionRefs,
            List<EvidenceRef> evidenceRefs,
            Long closeSnapshotId,
            SnapshotTime resolutionSubmittedAt,
            SnapshotTime closedAt,
            Integer payloadSchemaVersion,
            String snapshotDigest,
            Long retrospectiveId,
            String retrospectiveStatus,
            Integer retrospectiveVersion,
            List<String> learningCategories,
            List<RecurrenceLink> recurrenceLinks,
            CloseCheckExtensionAvailability closeCheckExtensionAvailability) {

        public V41GovernanceSnapshot {
            rootCauseCategories = rootCauseCategories == null
                    ? List.of() : List.copyOf(rootCauseCategories);
            actionRefs = actionRefs == null ? List.of() : List.copyOf(actionRefs);
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
            learningCategories = learningCategories == null
                    ? List.of() : List.copyOf(learningCategories);
            recurrenceLinks = recurrenceLinks == null
                    ? List.of() : List.copyOf(recurrenceLinks);
        }
    }

    record SnapshotTime(String availability, Instant value, String reasonCode) {

        public static SnapshotTime available(Instant value) {
            return new SnapshotTime("AVAILABLE", value, null);
        }

        public static SnapshotTime unavailable(String reasonCode) {
            return new SnapshotTime("UNAVAILABLE", null, reasonCode);
        }
    }

    record ActionRef(
            Long actionId,
            String referenceType,
            Integer observedVersion,
            SnapshotTime occurredAt,
            String summaryDigest) {
    }

    record EvidenceRef(
            Long evidenceId,
            String evidenceType,
            String assertionType,
            String subjectType,
            String sourceType,
            Integer sourceVersion,
            SnapshotTime observedAt,
            String summaryDigest) {
    }

    record RecurrenceLink(
            Long linkId,
            Long previousCaseId,
            String relationType,
            String rootCauseCategory,
            SnapshotTime linkedAt,
            String linkDigest) {
    }

    record CloseCheckExtensionAvailability(
            String availability,
            boolean failClosed,
            List<ProviderContract> providers) {

        public CloseCheckExtensionAvailability {
            providers = providers == null ? List.of() : List.copyOf(providers);
        }
    }

    record ProviderContract(String code, int contractVersion, int order) {
    }
}
