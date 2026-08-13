package com.offerlab.community.analytics.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChannelQualityRiskGovernanceSnapshotQueryMapper {

    @Select("""
            SELECT c.id AS caseId,
                   c.batch_id AS batchId,
                   c.domain,
                   c.status AS caseStatus,
                   c.case_version AS caseVersion,
                   b.coordination_version AS coordinationVersion,
                   g.governance_version AS governanceFactVersion,
                   g.current_resolution_revision_id AS currentResolutionRevisionId,
                   r.outcome_type AS outcomeType,
                   r.content_recovery_state AS contentRecoveryState,
                   r.residual_risk_level AS residualRiskLevel,
                   c.v41_close_snapshot_id AS closeSnapshotId,
                   s.primary_root_cause AS snapshotPrimaryRootCause,
                   s.closed_case_version AS closedCaseVersion,
                   s.closed_coordination_version AS closedCoordinationVersion,
                   s.closed_governance_version AS closedGovernanceVersion,
                   s.payload_schema_version AS payloadSchemaVersion,
                   s.snapshot_digest AS snapshotDigest,
                   s.create_time AS closedAt,
                   rt.id AS retrospectiveId,
                   rt.status AS retrospectiveStatus,
                   rt.retrospective_version AS retrospectiveVersion
            FROM t_channel_quality_review_risk_case c
            INNER JOIN t_collab_content_maintenance_dispatch_batch b
                    ON b.id = c.batch_id
            INNER JOIN t_channel_quality_review_risk_case_governance g
                    ON g.case_id = c.id
                   AND g.batch_id = c.batch_id
                   AND g.domain = c.domain
            LEFT JOIN t_channel_quality_review_risk_case_resolution_revision r
                   ON r.id = g.current_resolution_revision_id
                  AND r.case_id = c.id
            LEFT JOIN t_channel_quality_review_risk_case_close_snapshot s
                   ON s.id = c.v41_close_snapshot_id
                  AND s.case_id = c.id
            LEFT JOIN t_channel_quality_review_risk_case_retrospective rt
                   ON rt.case_id = c.id
            WHERE c.id = #{caseId}
            """)
    ChannelQualityRiskGovernanceSnapshotBaseRow selectBase(@Param("caseId") Long caseId);

    @Select("""
            SELECT id,
                   case_id AS caseId,
                   resolution_revision_id AS resolutionRevisionId,
                   cause_role AS causeRole,
                   category,
                   sequence_no AS sequenceNo
            FROM t_channel_quality_review_risk_case_resolution_root_cause
            WHERE case_id = #{caseId}
              AND resolution_revision_id = #{resolutionRevisionId}
            ORDER BY CASE cause_role WHEN 'PRIMARY' THEN 0 ELSE 1 END,
                     sequence_no ASC,
                     id ASC
            """)
    List<ChannelQualityRiskGovernanceSnapshotRootCauseRow> listRootCauses(
            @Param("caseId") Long caseId,
            @Param("resolutionRevisionId") Long resolutionRevisionId);

    @Select("""
            SELECT id,
                   case_id AS caseId,
                   reference_type AS referenceType,
                   observed_version AS observedVersion,
                   occurred_at AS occurredAt,
                   summary
            FROM t_channel_quality_review_risk_case_action_reference
            WHERE case_id = #{caseId}
            ORDER BY id ASC
            """)
    List<ChannelQualityRiskGovernanceSnapshotActionRow> listActions(@Param("caseId") Long caseId);

    @Select("""
            SELECT id,
                   case_id AS caseId,
                   evidence_type AS evidenceType,
                   assertion_type AS assertionType,
                   subject_type AS subjectType,
                   source_type AS sourceType,
                   source_version AS sourceVersion,
                   observed_at AS observedAt,
                   summary
            FROM t_channel_quality_review_risk_case_evidence_entry
            WHERE case_id = #{caseId}
            ORDER BY id ASC
            """)
    List<ChannelQualityRiskGovernanceSnapshotEvidenceRow> listEvidence(@Param("caseId") Long caseId);

    @Select("""
            SELECT retrospective_id AS retrospectiveId,
                   learning_category AS learningCategory,
                   create_time AS occurredAt
            FROM t_channel_quality_review_risk_case_retrospective_event
            WHERE retrospective_id = #{retrospectiveId}
              AND event_type = 'RETROSPECTIVE_FINDING_RECORDED'
            ORDER BY id ASC
            """)
    List<ChannelQualityRiskGovernanceSnapshotLearningRow> listLearningCategories(
            @Param("retrospectiveId") Long retrospectiveId);

    @Select("""
            SELECT id,
                   current_case_id AS currentCaseId,
                   previous_case_id AS previousCaseId,
                   relation_type AS relationType,
                   root_cause_category AS rootCauseCategory,
                   command_fingerprint AS commandFingerprint,
                   create_time AS linkedAt
            FROM t_channel_quality_review_risk_case_recurrence_link
            WHERE current_case_id = #{caseId}
            ORDER BY id ASC
            """)
    List<ChannelQualityRiskGovernanceSnapshotRecurrenceRow> listRecurrenceLinks(
            @Param("caseId") Long caseId);
}
