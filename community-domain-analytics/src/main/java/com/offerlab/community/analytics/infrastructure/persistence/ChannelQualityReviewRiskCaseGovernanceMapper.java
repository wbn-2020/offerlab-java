package com.offerlab.community.analytics.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChannelQualityReviewRiskCaseGovernanceMapper {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN (
                't_channel_quality_review_risk_case_governance',
                't_channel_quality_review_risk_case_resolution_revision',
                't_channel_quality_review_risk_case_resolution_root_cause',
                't_channel_quality_review_risk_case_action_reference',
                't_channel_quality_review_risk_case_evidence_entry',
                't_channel_quality_review_risk_case_close_snapshot',
                't_channel_quality_review_risk_case_close_check',
                't_channel_quality_review_risk_case_retrospective',
                't_channel_quality_review_risk_case_retrospective_event',
                't_channel_quality_review_risk_case_recurrence_link',
                't_channel_quality_review_risk_case_governance_milestone'
              )
            """)
    int v41TablesExist();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 't_channel_quality_review_risk_case'
              AND (
                (column_name = 'v41_close_snapshot_id'
                    AND data_type = 'bigint' AND is_nullable = 'YES')
                OR (column_name = 'legacy_closed_without_snapshot'
                    AND data_type = 'tinyint' AND is_nullable = 'NO')
              )
            """)
    int v41CaseColumnsExist();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.table_constraints
            WHERE table_schema = DATABASE()
              AND constraint_type = 'CHECK'
              AND constraint_name IN (
                'chk_quality_review_risk_case_v41_close_snapshot',
                'chk_quality_review_risk_case_governance_identity',
                'chk_quality_review_risk_case_resolution_identity',
                'chk_quality_review_risk_case_resolution_shape',
                'chk_quality_review_risk_case_root_cause_identity',
                'chk_quality_review_risk_case_action_identity',
                'chk_quality_review_risk_case_action_shape',
                'chk_quality_review_risk_case_evidence_identity',
                'chk_quality_review_risk_case_evidence_shape',
                'chk_quality_review_risk_case_close_snapshot_identity',
                'chk_quality_review_risk_case_close_snapshot_shape',
                'chk_quality_review_risk_case_close_check_identity',
                'chk_quality_review_risk_case_close_check_shape',
                'chk_quality_review_risk_case_retrospective_identity',
                'chk_quality_review_risk_case_retrospective_shape',
                'chk_quality_review_risk_case_retro_event_identity',
                'chk_quality_review_risk_case_retro_event_shape',
                'chk_quality_review_risk_case_recurrence_identity',
                'chk_quality_review_risk_case_recurrence_shape',
                'chk_quality_review_risk_case_milestone_identity',
                'chk_quality_review_risk_case_milestone_shape'
              )
            """)
    int v41ConstraintsExist();

    @Select("""
            SELECT COUNT(DISTINCT CONCAT(table_name, '|', index_name))
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND (
                (table_name = 't_channel_quality_review_risk_case'
                    AND index_name IN (
                        'uk_quality_review_risk_case_v41_close_snapshot',
                        'idx_quality_review_risk_case_v41_snapshot'
                    ))
                OR (table_name = 't_channel_quality_review_risk_case_governance'
                    AND index_name IN (
                        'idx_quality_review_risk_case_governance_domain',
                        'idx_quality_review_risk_case_governance_batch'
                    ))
                OR (table_name = 't_channel_quality_review_risk_case_resolution_revision'
                    AND index_name IN (
                        'uk_quality_review_risk_case_resolution_revision_no',
                        'uk_quality_review_risk_case_resolution_command',
                        'idx_quality_review_risk_case_resolution_timeline'
                    ))
                OR (table_name = 't_channel_quality_review_risk_case_resolution_root_cause'
                    AND index_name IN (
                        'uk_quality_review_risk_case_root_cause_sequence',
                        'idx_quality_review_risk_case_root_cause_case'
                    ))
                OR (table_name = 't_channel_quality_review_risk_case_action_reference'
                    AND index_name IN (
                        'uk_quality_review_risk_case_action_command',
                        'uk_quality_review_risk_case_action_fingerprint',
                        'uk_quality_review_risk_case_action_correction',
                        'idx_quality_review_risk_case_action_timeline'
                    ))
                OR (table_name = 't_channel_quality_review_risk_case_evidence_entry'
                    AND index_name IN (
                        'uk_quality_review_risk_case_evidence_command',
                        'uk_quality_review_risk_case_evidence_digest',
                        'uk_quality_review_risk_case_evidence_correction',
                        'idx_quality_review_risk_case_evidence_timeline'
                    ))
                OR (table_name = 't_channel_quality_review_risk_case_close_snapshot'
                    AND index_name IN (
                        'uk_quality_review_risk_case_close_snapshot_case',
                        'uk_quality_review_risk_case_close_snapshot_command',
                        'uk_quality_review_risk_case_close_snapshot_digest',
                        'idx_quality_review_risk_case_close_snapshot_domain'
                    ))
                OR (table_name = 't_channel_quality_review_risk_case_close_check'
                    AND index_name IN (
                        'uk_quality_review_risk_case_close_check_provider',
                        'idx_quality_review_risk_case_close_check_case'
                    ))
                OR (table_name = 't_channel_quality_review_risk_case_retrospective'
                    AND index_name IN (
                        'uk_quality_review_risk_case_retrospective_case',
                        'uk_quality_review_risk_case_retrospective_snapshot',
                        'idx_quality_review_risk_case_retrospective_workspace'
                    ))
                OR (table_name = 't_channel_quality_review_risk_case_retrospective_event'
                    AND index_name IN (
                        'uk_quality_review_risk_case_retro_event_command',
                        'idx_quality_review_risk_case_retro_event_timeline',
                        'idx_quality_review_risk_case_retro_event_case'
                    ))
                OR (table_name = 't_channel_quality_review_risk_case_recurrence_link'
                    AND index_name IN (
                        'uk_quality_review_risk_case_recurrence_pair',
                        'uk_quality_review_risk_case_recurrence_command',
                        'idx_quality_review_risk_case_recurrence_previous'
                    ))
                OR (table_name = 't_channel_quality_review_risk_case_governance_milestone'
                    AND index_name IN (
                        'uk_quality_review_risk_case_milestone_source',
                        'uk_quality_review_risk_case_milestone_close_snapshot',
                        'uk_quality_review_risk_case_milestone_retro_pending',
                        'uk_quality_review_risk_case_milestone_retro_completed',
                        'idx_quality_review_risk_case_milestone_v42',
                        'idx_quality_review_risk_case_milestone_owner'
                    ))
              )
            """)
    int v41IndexesExist();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.check_constraints check_constraint
            INNER JOIN information_schema.table_constraints table_constraint
                ON table_constraint.constraint_schema = check_constraint.constraint_schema
               AND table_constraint.constraint_name = check_constraint.constraint_name
            WHERE table_constraint.table_schema = DATABASE()
              AND table_constraint.table_name = 't_channel_quality_review_risk_case_governance_milestone'
              AND table_constraint.constraint_name = 'chk_quality_review_risk_case_milestone_shape'
              AND LOCATE('OWNER_ASSIGNED', check_constraint.check_clause) > 0
              AND LOCATE('OWNER_ACKNOWLEDGED', check_constraint.check_clause) > 0
              AND LOCATE('PLAN_RECORDED', check_constraint.check_clause) > 0
              AND LOCATE('CLOSE_SNAPSHOT_GENERATED', check_constraint.check_clause) > 0
              AND LOCATE('RETROSPECTIVE_PENDING', check_constraint.check_clause) > 0
              AND LOCATE('RETROSPECTIVE_OWNER_ASSIGNED', check_constraint.check_clause) > 0
              AND LOCATE('RETROSPECTIVE_COMPLETED', check_constraint.check_clause) > 0
              AND LOCATE('CASE_OPENED', check_constraint.check_clause) = 0
              AND LOCATE('RESOLUTION_SUBMITTED', check_constraint.check_clause) = 0
            """)
    int v41FactContractMatches();

    @Select("""
            SELECT id,
                   batch_id AS batchId,
                   domain,
                   trigger_type AS triggerType,
                   status,
                   owner_uid AS ownerUid,
                   case_version AS caseVersion,
                   opened_coordination_version AS openedCoordinationVersion,
                   v41_close_snapshot_id AS v41CloseSnapshotId,
                   legacy_closed_without_snapshot AS legacyClosedWithoutSnapshot,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_channel_quality_review_risk_case
            WHERE id = #{caseId}
            """)
    ChannelQualityReviewRiskCaseGovernanceCaseRow selectCaseById(@Param("caseId") Long caseId);

    @Select("""
            SELECT id,
                   batch_id AS batchId,
                   domain,
                   trigger_type AS triggerType,
                   status,
                   owner_uid AS ownerUid,
                   case_version AS caseVersion,
                   opened_coordination_version AS openedCoordinationVersion,
                   v41_close_snapshot_id AS v41CloseSnapshotId,
                   legacy_closed_without_snapshot AS legacyClosedWithoutSnapshot,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_channel_quality_review_risk_case
            WHERE id = #{caseId}
            FOR UPDATE
            """)
    ChannelQualityReviewRiskCaseGovernanceCaseRow lockCaseById(@Param("caseId") Long caseId);

    @Select("""
            SELECT case_id AS caseId,
                   batch_id AS batchId,
                   domain,
                   governance_version AS governanceVersion,
                   current_resolution_revision_id AS currentResolutionRevisionId,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_channel_quality_review_risk_case_governance
            WHERE case_id = #{caseId}
            """)
    ChannelQualityReviewRiskCaseGovernanceRow selectGovernance(@Param("caseId") Long caseId);

    @Select("""
            SELECT case_id AS caseId,
                   batch_id AS batchId,
                   domain,
                   governance_version AS governanceVersion,
                   current_resolution_revision_id AS currentResolutionRevisionId,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_channel_quality_review_risk_case_governance
            WHERE case_id = #{caseId}
            FOR UPDATE
            """)
    ChannelQualityReviewRiskCaseGovernanceRow lockGovernance(@Param("caseId") Long caseId);

    @Insert("""
            INSERT INTO t_channel_quality_review_risk_case_governance (
                case_id, batch_id, domain, governance_version, current_resolution_revision_id
            ) VALUES (
                #{caseId}, #{batchId}, #{domain}, 0, NULL
            )
            """)
    int insertGovernance(@Param("caseId") Long caseId,
                         @Param("batchId") Long batchId,
                         @Param("domain") Integer domain);

    @Update("""
            UPDATE t_channel_quality_review_risk_case_governance
            SET governance_version = governance_version + 1,
                current_resolution_revision_id = #{resolutionRevisionId},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE case_id = #{caseId}
              AND governance_version = #{expectedGovernanceVersion}
            """)
    int advanceGovernanceResolution(@Param("caseId") Long caseId,
                                    @Param("expectedGovernanceVersion") Integer expectedGovernanceVersion,
                                    @Param("resolutionRevisionId") Long resolutionRevisionId);

    @Update("""
            UPDATE t_channel_quality_review_risk_case_governance
            SET governance_version = governance_version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE case_id = #{caseId}
              AND governance_version = #{expectedGovernanceVersion}
            """)
    int advanceGovernanceVersion(@Param("caseId") Long caseId,
                                 @Param("expectedGovernanceVersion") Integer expectedGovernanceVersion);

    @Select("""
            SELECT COUNT(*)
            FROM t_channel_quality_review_risk_case_action_reference
            WHERE case_id = #{caseId}
            """)
    int countActionReferences(@Param("caseId") Long caseId);

    @Select("""
            SELECT COUNT(*)
            FROM t_channel_quality_review_risk_case_evidence_entry
            WHERE case_id = #{caseId}
            """)
    int countEvidenceEntries(@Param("caseId") Long caseId);

    @Select("""
            SELECT id,
                   case_id AS caseId,
                   revision_no AS revisionNo,
                   outcome_type AS outcomeType,
                   content_recovery_state AS contentRecoveryState,
                   recovery_scope AS recoveryScope,
                   residual_risk_level AS residualRiskLevel,
                   summary,
                   created_by_uid AS createdByUid,
                   command_id AS commandId,
                   command_fingerprint AS commandFingerprint,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_resolution_revision
            WHERE id = #{revisionId}
              AND case_id = #{caseId}
            """)
    ChannelQualityReviewRiskCaseResolutionRevisionRow selectResolutionRevision(
            @Param("caseId") Long caseId,
            @Param("revisionId") Long revisionId);

    @Select("""
            SELECT id,
                   case_id AS caseId,
                   revision_no AS revisionNo,
                   outcome_type AS outcomeType,
                   content_recovery_state AS contentRecoveryState,
                   recovery_scope AS recoveryScope,
                   residual_risk_level AS residualRiskLevel,
                   summary,
                   created_by_uid AS createdByUid,
                   command_id AS commandId,
                   command_fingerprint AS commandFingerprint,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_resolution_revision
            WHERE case_id = #{caseId}
              AND command_id = #{commandId}
            """)
    ChannelQualityReviewRiskCaseResolutionRevisionRow selectResolutionRevisionByCommand(
            @Param("caseId") Long caseId,
            @Param("commandId") String commandId);

    @Select("""
            <script>
            SELECT id,
                   case_id AS caseId,
                   revision_no AS revisionNo,
                   outcome_type AS outcomeType,
                   content_recovery_state AS contentRecoveryState,
                   recovery_scope AS recoveryScope,
                   residual_risk_level AS residualRiskLevel,
                   summary,
                   created_by_uid AS createdByUid,
                   command_id AS commandId,
                   command_fingerprint AS commandFingerprint,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_resolution_revision
            WHERE case_id = #{caseId}
              AND (#{cursor} = 0 OR id &lt; #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ChannelQualityReviewRiskCaseResolutionRevisionRow> listResolutionRevisions(
            @Param("caseId") Long caseId,
            @Param("cursor") long cursor,
            @Param("limit") int limit);

    @Select("""
            SELECT id,
                   case_id AS caseId,
                   resolution_revision_id AS resolutionRevisionId,
                   cause_role AS causeRole,
                   category,
                   sequence_no AS sequenceNo,
                   note,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_resolution_root_cause
            WHERE case_id = #{caseId}
              AND resolution_revision_id = #{revisionId}
            ORDER BY sequence_no ASC, id ASC
            """)
    List<ChannelQualityReviewRiskCaseResolutionRootCauseRow> listRootCauses(
            @Param("caseId") Long caseId,
            @Param("revisionId") Long revisionId);

    @Select("""
            SELECT COALESCE(MAX(revision_no), 0)
            FROM t_channel_quality_review_risk_case_resolution_revision
            WHERE case_id = #{caseId}
            """)
    int maxResolutionRevisionNo(@Param("caseId") Long caseId);

    @Insert("""
            INSERT INTO t_channel_quality_review_risk_case_resolution_revision (
                id, case_id, revision_no, outcome_type, content_recovery_state, recovery_scope,
                residual_risk_level, summary, created_by_uid, command_id, command_fingerprint
            ) VALUES (
                #{id}, #{caseId}, #{revisionNo}, #{outcomeType}, #{contentRecoveryState}, #{recoveryScope},
                #{residualRiskLevel}, #{summary}, #{createdByUid}, #{commandId}, #{commandFingerprint}
            )
            """)
    int insertResolutionRevision(@Param("id") Long id,
                                 @Param("caseId") Long caseId,
                                 @Param("revisionNo") Integer revisionNo,
                                 @Param("outcomeType") String outcomeType,
                                 @Param("contentRecoveryState") String contentRecoveryState,
                                 @Param("recoveryScope") String recoveryScope,
                                 @Param("residualRiskLevel") String residualRiskLevel,
                                 @Param("summary") String summary,
                                 @Param("createdByUid") Long createdByUid,
                                 @Param("commandId") String commandId,
                                 @Param("commandFingerprint") String commandFingerprint);

    @Insert("""
            INSERT INTO t_channel_quality_review_risk_case_resolution_root_cause (
                id, case_id, resolution_revision_id, cause_role, category, sequence_no, note
            ) VALUES (
                #{id}, #{caseId}, #{revisionId}, #{causeRole}, #{category}, #{sequenceNo}, #{note}
            )
            """)
    int insertRootCause(@Param("id") Long id,
                        @Param("caseId") Long caseId,
                        @Param("revisionId") Long revisionId,
                        @Param("causeRole") String causeRole,
                        @Param("category") String category,
                        @Param("sequenceNo") Integer sequenceNo,
                        @Param("note") String note);

    @Select("""
            SELECT id,
                   case_id AS caseId,
                   reference_type AS referenceType,
                   reference_key AS referenceKey,
                   observed_version AS observedVersion,
                   occurred_at AS occurredAt,
                   summary,
                   reference_fingerprint AS referenceFingerprint,
                   correction_of_reference_id AS correctionOfReferenceId,
                   created_by_uid AS createdByUid,
                   command_id AS commandId,
                   command_fingerprint AS commandFingerprint,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_action_reference
            WHERE id = #{id}
              AND case_id = #{caseId}
            """)
    ChannelQualityReviewRiskCaseActionReferenceRow selectActionReference(
            @Param("caseId") Long caseId,
            @Param("id") Long id);

    @Select("""
            SELECT id,
                   case_id AS caseId,
                   reference_type AS referenceType,
                   reference_key AS referenceKey,
                   observed_version AS observedVersion,
                   occurred_at AS occurredAt,
                   summary,
                   reference_fingerprint AS referenceFingerprint,
                   correction_of_reference_id AS correctionOfReferenceId,
                   created_by_uid AS createdByUid,
                   command_id AS commandId,
                   command_fingerprint AS commandFingerprint,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_action_reference
            WHERE case_id = #{caseId}
              AND command_id = #{commandId}
            """)
    ChannelQualityReviewRiskCaseActionReferenceRow selectActionReferenceByCommand(
            @Param("caseId") Long caseId,
            @Param("commandId") String commandId);

    @Select("""
            <script>
            SELECT id,
                   case_id AS caseId,
                   reference_type AS referenceType,
                   reference_key AS referenceKey,
                   observed_version AS observedVersion,
                   occurred_at AS occurredAt,
                   summary,
                   reference_fingerprint AS referenceFingerprint,
                   correction_of_reference_id AS correctionOfReferenceId,
                   created_by_uid AS createdByUid,
                   command_id AS commandId,
                   command_fingerprint AS commandFingerprint,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_action_reference
            WHERE case_id = #{caseId}
              AND (#{cursor} = 0 OR id &lt; #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ChannelQualityReviewRiskCaseActionReferenceRow> listActionReferences(
            @Param("caseId") Long caseId,
            @Param("cursor") long cursor,
            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT id,
                   case_id AS caseId,
                   reference_type AS referenceType,
                   reference_key AS referenceKey,
                   observed_version AS observedVersion,
                   occurred_at AS occurredAt,
                   summary,
                   reference_fingerprint AS referenceFingerprint,
                   correction_of_reference_id AS correctionOfReferenceId,
                   created_by_uid AS createdByUid,
                   command_id AS commandId,
                   command_fingerprint AS commandFingerprint,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_action_reference
            WHERE case_id = #{caseId}
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            ORDER BY id ASC
            </script>
            """)
    List<ChannelQualityReviewRiskCaseActionReferenceRow> listActionReferencesByIds(
            @Param("caseId") Long caseId,
            @Param("ids") List<Long> ids);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM t_channel_quality_review_risk_case_action_reference
            WHERE case_id = #{caseId}
              AND correction_of_reference_id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            </script>
            """)
    int countActionReferenceCorrections(@Param("caseId") Long caseId, @Param("ids") List<Long> ids);

    @Insert("""
            INSERT INTO t_channel_quality_review_risk_case_action_reference (
                id, case_id, reference_type, reference_key, observed_version, occurred_at, summary,
                reference_fingerprint, correction_of_reference_id, created_by_uid, command_id,
                command_fingerprint
            ) VALUES (
                #{id}, #{caseId}, #{referenceType}, #{referenceKey}, #{observedVersion}, #{occurredAt},
                #{summary}, #{referenceFingerprint}, #{correctionOfReferenceId}, #{createdByUid},
                #{commandId}, #{commandFingerprint}
            )
            """)
    int insertActionReference(@Param("id") Long id,
                              @Param("caseId") Long caseId,
                              @Param("referenceType") String referenceType,
                              @Param("referenceKey") String referenceKey,
                              @Param("observedVersion") Integer observedVersion,
                              @Param("occurredAt") LocalDateTime occurredAt,
                              @Param("summary") String summary,
                              @Param("referenceFingerprint") String referenceFingerprint,
                              @Param("correctionOfReferenceId") Long correctionOfReferenceId,
                              @Param("createdByUid") Long createdByUid,
                              @Param("commandId") String commandId,
                              @Param("commandFingerprint") String commandFingerprint);

    @Select("""
            SELECT id,
                   case_id AS caseId,
                   evidence_type AS evidenceType,
                   assertion_type AS assertionType,
                   subject_type AS subjectType,
                   subject_ref AS subjectRef,
                   source_type AS sourceType,
                   source_ref AS sourceRef,
                   source_version AS sourceVersion,
                   observed_at AS observedAt,
                   summary,
                   evidence_digest AS evidenceDigest,
                   correction_of_entry_id AS correctionOfEntryId,
                   created_by_uid AS createdByUid,
                   command_id AS commandId,
                   command_fingerprint AS commandFingerprint,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_evidence_entry
            WHERE id = #{id}
              AND case_id = #{caseId}
            """)
    ChannelQualityReviewRiskCaseEvidenceEntryRow selectEvidenceEntry(
            @Param("caseId") Long caseId,
            @Param("id") Long id);

    @Select("""
            SELECT id,
                   case_id AS caseId,
                   evidence_type AS evidenceType,
                   assertion_type AS assertionType,
                   subject_type AS subjectType,
                   subject_ref AS subjectRef,
                   source_type AS sourceType,
                   source_ref AS sourceRef,
                   source_version AS sourceVersion,
                   observed_at AS observedAt,
                   summary,
                   evidence_digest AS evidenceDigest,
                   correction_of_entry_id AS correctionOfEntryId,
                   created_by_uid AS createdByUid,
                   command_id AS commandId,
                   command_fingerprint AS commandFingerprint,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_evidence_entry
            WHERE case_id = #{caseId}
              AND command_id = #{commandId}
            """)
    ChannelQualityReviewRiskCaseEvidenceEntryRow selectEvidenceEntryByCommand(
            @Param("caseId") Long caseId,
            @Param("commandId") String commandId);

    @Select("""
            <script>
            SELECT id,
                   case_id AS caseId,
                   evidence_type AS evidenceType,
                   assertion_type AS assertionType,
                   subject_type AS subjectType,
                   subject_ref AS subjectRef,
                   source_type AS sourceType,
                   source_ref AS sourceRef,
                   source_version AS sourceVersion,
                   observed_at AS observedAt,
                   summary,
                   evidence_digest AS evidenceDigest,
                   correction_of_entry_id AS correctionOfEntryId,
                   created_by_uid AS createdByUid,
                   command_id AS commandId,
                   command_fingerprint AS commandFingerprint,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_evidence_entry
            WHERE case_id = #{caseId}
              AND (#{cursor} = 0 OR id &lt; #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ChannelQualityReviewRiskCaseEvidenceEntryRow> listEvidenceEntries(
            @Param("caseId") Long caseId,
            @Param("cursor") long cursor,
            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT id,
                   case_id AS caseId,
                   evidence_type AS evidenceType,
                   assertion_type AS assertionType,
                   subject_type AS subjectType,
                   subject_ref AS subjectRef,
                   source_type AS sourceType,
                   source_ref AS sourceRef,
                   source_version AS sourceVersion,
                   observed_at AS observedAt,
                   summary,
                   evidence_digest AS evidenceDigest,
                   correction_of_entry_id AS correctionOfEntryId,
                   created_by_uid AS createdByUid,
                   command_id AS commandId,
                   command_fingerprint AS commandFingerprint,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_evidence_entry
            WHERE case_id = #{caseId}
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            ORDER BY id ASC
            </script>
            """)
    List<ChannelQualityReviewRiskCaseEvidenceEntryRow> listEvidenceEntriesByIds(
            @Param("caseId") Long caseId,
            @Param("ids") List<Long> ids);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM t_channel_quality_review_risk_case_evidence_entry
            WHERE case_id = #{caseId}
              AND correction_of_entry_id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            </script>
            """)
    int countEvidenceEntryCorrections(@Param("caseId") Long caseId, @Param("ids") List<Long> ids);

    @Insert("""
            INSERT INTO t_channel_quality_review_risk_case_evidence_entry (
                id, case_id, evidence_type, assertion_type, subject_type, subject_ref, source_type,
                source_ref, source_version, observed_at, summary, evidence_digest, correction_of_entry_id,
                created_by_uid, command_id, command_fingerprint
            ) VALUES (
                #{id}, #{caseId}, #{evidenceType}, #{assertionType}, #{subjectType}, #{subjectRef},
                #{sourceType}, #{sourceRef}, #{sourceVersion}, #{observedAt}, #{summary},
                #{evidenceDigest}, #{correctionOfEntryId}, #{createdByUid}, #{commandId},
                #{commandFingerprint}
            )
            """)
    int insertEvidenceEntry(@Param("id") Long id,
                            @Param("caseId") Long caseId,
                            @Param("evidenceType") String evidenceType,
                            @Param("assertionType") String assertionType,
                            @Param("subjectType") String subjectType,
                            @Param("subjectRef") String subjectRef,
                            @Param("sourceType") String sourceType,
                            @Param("sourceRef") String sourceRef,
                            @Param("sourceVersion") Integer sourceVersion,
                            @Param("observedAt") LocalDateTime observedAt,
                            @Param("summary") String summary,
                            @Param("evidenceDigest") String evidenceDigest,
                            @Param("correctionOfEntryId") Long correctionOfEntryId,
                            @Param("createdByUid") Long createdByUid,
                            @Param("commandId") String commandId,
                            @Param("commandFingerprint") String commandFingerprint);

    @Select("""
            SELECT id,
                   case_id AS caseId,
                   batch_id AS batchId,
                   domain,
                   resolution_revision_id AS resolutionRevisionId,
                   closed_case_version AS closedCaseVersion,
                   closed_coordination_version AS closedCoordinationVersion,
                   closed_governance_version AS closedGovernanceVersion,
                   outcome_type AS outcomeType,
                   content_recovery_state AS contentRecoveryState,
                   primary_root_cause AS primaryRootCause,
                   residual_risk_level AS residualRiskLevel,
                   action_reference_count AS actionReferenceCount,
                   evidence_count AS evidenceCount,
                   payload_schema_version AS payloadSchemaVersion,
                   canonical_payload AS canonicalPayload,
                   snapshot_digest AS snapshotDigest,
                   closed_by_uid AS closedByUid,
                   command_id AS commandId,
                   command_fingerprint AS commandFingerprint,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_close_snapshot
            WHERE case_id = #{caseId}
            """)
    ChannelQualityReviewRiskCaseCloseSnapshotRow selectCloseSnapshotByCase(@Param("caseId") Long caseId);

    @Select("""
            SELECT id,
                   case_id AS caseId,
                   batch_id AS batchId,
                   domain,
                   resolution_revision_id AS resolutionRevisionId,
                   closed_case_version AS closedCaseVersion,
                   closed_coordination_version AS closedCoordinationVersion,
                   closed_governance_version AS closedGovernanceVersion,
                   outcome_type AS outcomeType,
                   content_recovery_state AS contentRecoveryState,
                   primary_root_cause AS primaryRootCause,
                   residual_risk_level AS residualRiskLevel,
                   action_reference_count AS actionReferenceCount,
                   evidence_count AS evidenceCount,
                   payload_schema_version AS payloadSchemaVersion,
                   canonical_payload AS canonicalPayload,
                   snapshot_digest AS snapshotDigest,
                   closed_by_uid AS closedByUid,
                   command_id AS commandId,
                   command_fingerprint AS commandFingerprint,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_close_snapshot
            WHERE case_id = #{caseId}
              AND command_id = #{commandId}
            """)
    ChannelQualityReviewRiskCaseCloseSnapshotRow selectCloseSnapshotByCommand(
            @Param("caseId") Long caseId,
            @Param("commandId") String commandId);

    @Insert("""
            INSERT INTO t_channel_quality_review_risk_case_close_snapshot (
                id, case_id, batch_id, domain, resolution_revision_id, closed_case_version,
                closed_coordination_version, closed_governance_version, outcome_type,
                content_recovery_state, primary_root_cause, residual_risk_level,
                action_reference_count, evidence_count, payload_schema_version, canonical_payload,
                snapshot_digest, closed_by_uid, command_id, command_fingerprint
            ) VALUES (
                #{id}, #{caseId}, #{batchId}, #{domain}, #{resolutionRevisionId}, #{closedCaseVersion},
                #{closedCoordinationVersion}, #{closedGovernanceVersion}, #{outcomeType},
                #{contentRecoveryState}, #{primaryRootCause}, #{residualRiskLevel},
                #{actionReferenceCount}, #{evidenceCount}, #{payloadSchemaVersion}, CAST(#{canonicalPayload} AS JSON),
                #{snapshotDigest}, #{closedByUid}, #{commandId}, #{commandFingerprint}
            )
            """)
    int insertCloseSnapshot(@Param("id") Long id,
                            @Param("caseId") Long caseId,
                            @Param("batchId") Long batchId,
                            @Param("domain") Integer domain,
                            @Param("resolutionRevisionId") Long resolutionRevisionId,
                            @Param("closedCaseVersion") Integer closedCaseVersion,
                            @Param("closedCoordinationVersion") Integer closedCoordinationVersion,
                            @Param("closedGovernanceVersion") Integer closedGovernanceVersion,
                            @Param("outcomeType") String outcomeType,
                            @Param("contentRecoveryState") String contentRecoveryState,
                            @Param("primaryRootCause") String primaryRootCause,
                            @Param("residualRiskLevel") String residualRiskLevel,
                            @Param("actionReferenceCount") Integer actionReferenceCount,
                            @Param("evidenceCount") Integer evidenceCount,
                            @Param("payloadSchemaVersion") Integer payloadSchemaVersion,
                            @Param("canonicalPayload") String canonicalPayload,
                            @Param("snapshotDigest") String snapshotDigest,
                            @Param("closedByUid") Long closedByUid,
                            @Param("commandId") String commandId,
                            @Param("commandFingerprint") String commandFingerprint);

    @Select("""
            SELECT id,
                   snapshot_id AS snapshotId,
                   case_id AS caseId,
                   provider_code AS providerCode,
                   provider_version AS providerVersion,
                   requirement_level AS requirementLevel,
                   result,
                   reason_code AS reasonCode,
                   summary,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_close_check
            WHERE snapshot_id = #{snapshotId}
            ORDER BY id ASC
            """)
    List<ChannelQualityReviewRiskCaseCloseCheckRow> listCloseChecks(@Param("snapshotId") Long snapshotId);

    @Insert("""
            INSERT INTO t_channel_quality_review_risk_case_close_check (
                id, snapshot_id, case_id, provider_code, provider_version, requirement_level,
                result, reason_code, summary
            ) VALUES (
                #{id}, #{snapshotId}, #{caseId}, #{providerCode}, #{providerVersion},
                #{requirementLevel}, #{result}, #{reasonCode}, #{summary}
            )
            """)
    int insertCloseCheck(@Param("id") Long id,
                         @Param("snapshotId") Long snapshotId,
                         @Param("caseId") Long caseId,
                         @Param("providerCode") String providerCode,
                         @Param("providerVersion") Integer providerVersion,
                         @Param("requirementLevel") String requirementLevel,
                         @Param("result") String result,
                         @Param("reasonCode") String reasonCode,
                         @Param("summary") String summary);

    @Update("""
            UPDATE t_channel_quality_review_risk_case
            SET status = 'CLOSED',
                case_version = case_version + 1,
                v41_close_snapshot_id = #{snapshotId},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{caseId}
              AND status = 'RESOLVED'
              AND case_version = #{expectedCaseVersion}
              AND v41_close_snapshot_id IS NULL
              AND legacy_closed_without_snapshot = 0
            """)
    int closeCaseWithSnapshot(@Param("caseId") Long caseId,
                              @Param("expectedCaseVersion") Integer expectedCaseVersion,
                              @Param("snapshotId") Long snapshotId);

    @Insert("""
            INSERT INTO t_channel_quality_review_risk_case_event (
                id, case_id, batch_id, operator_uid, event_type, previous_status, status, owner_uid,
                observed_coordination_version, case_version, note
            ) VALUES (
                #{id}, #{caseId}, #{batchId}, #{operatorUid}, 'CASE_CLOSED', 'RESOLVED', 'CLOSED', NULL,
                #{coordinationVersion}, #{caseVersion}, #{note}
            )
            """)
    int insertClosedCaseEvent(@Param("id") Long id,
                              @Param("caseId") Long caseId,
                              @Param("batchId") Long batchId,
                              @Param("operatorUid") Long operatorUid,
                              @Param("coordinationVersion") Integer coordinationVersion,
                              @Param("caseVersion") Integer caseVersion,
                              @Param("note") String note);

    @Select("""
            SELECT id,
                   case_id AS caseId,
                   close_snapshot_id AS closeSnapshotId,
                   domain,
                   status,
                   owner_uid AS ownerUid,
                   retrospective_version AS retrospectiveVersion,
                   legacy_baseline AS legacyBaseline,
                   started_at AS startedAt,
                   completed_at AS completedAt,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_channel_quality_review_risk_case_retrospective
            WHERE case_id = #{caseId}
            """)
    ChannelQualityReviewRiskCaseRetrospectiveRow selectRetrospective(@Param("caseId") Long caseId);

    @Select("""
            SELECT id,
                   case_id AS caseId,
                   close_snapshot_id AS closeSnapshotId,
                   domain,
                   status,
                   owner_uid AS ownerUid,
                   retrospective_version AS retrospectiveVersion,
                   legacy_baseline AS legacyBaseline,
                   started_at AS startedAt,
                   completed_at AS completedAt,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_channel_quality_review_risk_case_retrospective
            WHERE case_id = #{caseId}
            FOR UPDATE
            """)
    ChannelQualityReviewRiskCaseRetrospectiveRow lockRetrospective(@Param("caseId") Long caseId);

    @Insert("""
            INSERT INTO t_channel_quality_review_risk_case_retrospective (
                id, case_id, close_snapshot_id, domain, status, owner_uid, retrospective_version,
                legacy_baseline, started_at, completed_at
            ) VALUES (
                #{id}, #{caseId}, #{closeSnapshotId}, #{domain}, 'PENDING', #{ownerUid}, 1,
                #{legacyBaseline}, NULL, NULL
            )
            """)
    int insertRetrospective(@Param("id") Long id,
                            @Param("caseId") Long caseId,
                            @Param("closeSnapshotId") Long closeSnapshotId,
                            @Param("domain") Integer domain,
                            @Param("ownerUid") Long ownerUid,
                            @Param("legacyBaseline") Integer legacyBaseline);

    @Update("""
            UPDATE t_channel_quality_review_risk_case_retrospective
            SET status = #{status},
                owner_uid = #{ownerUid},
                started_at = #{startedAt},
                completed_at = #{completedAt},
                retrospective_version = retrospective_version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{retrospectiveId}
              AND retrospective_version = #{expectedRetrospectiveVersion}
            """)
    int advanceRetrospective(@Param("retrospectiveId") Long retrospectiveId,
                             @Param("expectedRetrospectiveVersion") Integer expectedRetrospectiveVersion,
                             @Param("status") String status,
                             @Param("ownerUid") Long ownerUid,
                             @Param("startedAt") LocalDateTime startedAt,
                             @Param("completedAt") LocalDateTime completedAt);

    @Select("""
            SELECT id,
                   retrospective_id AS retrospectiveId,
                   case_id AS caseId,
                   operator_uid AS operatorUid,
                   event_type AS eventType,
                   previous_status AS previousStatus,
                   status,
                   owner_uid AS ownerUid,
                   learning_category AS learningCategory,
                   finding_summary AS findingSummary,
                   prevention_action_summary AS preventionActionSummary,
                   retrospective_version AS retrospectiveVersion,
                   command_id AS commandId,
                   command_fingerprint AS commandFingerprint,
                   note,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_retrospective_event
            WHERE retrospective_id = #{retrospectiveId}
              AND command_id = #{commandId}
            """)
    ChannelQualityReviewRiskCaseRetrospectiveEventRow selectRetrospectiveEventByCommand(
            @Param("retrospectiveId") Long retrospectiveId,
            @Param("commandId") String commandId);

    @Select("""
            <script>
            SELECT id,
                   retrospective_id AS retrospectiveId,
                   case_id AS caseId,
                   operator_uid AS operatorUid,
                   event_type AS eventType,
                   previous_status AS previousStatus,
                   status,
                   owner_uid AS ownerUid,
                   learning_category AS learningCategory,
                   finding_summary AS findingSummary,
                   prevention_action_summary AS preventionActionSummary,
                   retrospective_version AS retrospectiveVersion,
                   command_id AS commandId,
                   command_fingerprint AS commandFingerprint,
                   note,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_retrospective_event
            WHERE retrospective_id = #{retrospectiveId}
              AND (#{cursor} = 0 OR id &lt; #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ChannelQualityReviewRiskCaseRetrospectiveEventRow> listRetrospectiveEvents(
            @Param("retrospectiveId") Long retrospectiveId,
            @Param("cursor") long cursor,
            @Param("limit") int limit);

    @Insert("""
            INSERT INTO t_channel_quality_review_risk_case_retrospective_event (
                id, retrospective_id, case_id, operator_uid, event_type, previous_status, status,
                owner_uid, learning_category, finding_summary, prevention_action_summary,
                retrospective_version, command_id, command_fingerprint, note
            ) VALUES (
                #{id}, #{retrospectiveId}, #{caseId}, #{operatorUid}, #{eventType}, #{previousStatus},
                #{status}, #{ownerUid}, #{learningCategory}, #{findingSummary},
                #{preventionActionSummary}, #{retrospectiveVersion}, #{commandId},
                #{commandFingerprint}, #{note}
            )
            """)
    int insertRetrospectiveEvent(@Param("id") Long id,
                                 @Param("retrospectiveId") Long retrospectiveId,
                                 @Param("caseId") Long caseId,
                                 @Param("operatorUid") Long operatorUid,
                                 @Param("eventType") String eventType,
                                 @Param("previousStatus") String previousStatus,
                                 @Param("status") String status,
                                 @Param("ownerUid") Long ownerUid,
                                 @Param("learningCategory") String learningCategory,
                                 @Param("findingSummary") String findingSummary,
                                 @Param("preventionActionSummary") String preventionActionSummary,
                                 @Param("retrospectiveVersion") Integer retrospectiveVersion,
                                 @Param("commandId") String commandId,
                                 @Param("commandFingerprint") String commandFingerprint,
                                 @Param("note") String note);

    @Select("""
            SELECT id,
                   current_case_id AS currentCaseId,
                   previous_case_id AS previousCaseId,
                   domain,
                   relation_type AS relationType,
                   root_cause_category AS rootCauseCategory,
                   note,
                   linked_by_uid AS linkedByUid,
                   command_id AS commandId,
                   command_fingerprint AS commandFingerprint,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_recurrence_link
            WHERE current_case_id = #{caseId}
              AND command_id = #{commandId}
            """)
    ChannelQualityReviewRiskCaseRecurrenceLinkRow selectRecurrenceLinkByCommand(
            @Param("caseId") Long caseId,
            @Param("commandId") String commandId);

    @Select("""
            <script>
            SELECT id,
                   current_case_id AS currentCaseId,
                   previous_case_id AS previousCaseId,
                   domain,
                   relation_type AS relationType,
                   root_cause_category AS rootCauseCategory,
                   note,
                   linked_by_uid AS linkedByUid,
                   command_id AS commandId,
                   command_fingerprint AS commandFingerprint,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_recurrence_link
            WHERE current_case_id = #{caseId}
              AND (#{cursor} = 0 OR id &lt; #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ChannelQualityReviewRiskCaseRecurrenceLinkRow> listRecurrenceLinks(
            @Param("caseId") Long caseId,
            @Param("cursor") long cursor,
            @Param("limit") int limit);

    @Insert("""
            INSERT INTO t_channel_quality_review_risk_case_recurrence_link (
                id, current_case_id, previous_case_id, domain, relation_type, root_cause_category,
                note, linked_by_uid, command_id, command_fingerprint
            ) VALUES (
                #{id}, #{currentCaseId}, #{previousCaseId}, #{domain}, #{relationType},
                #{rootCauseCategory}, #{note}, #{linkedByUid}, #{commandId}, #{commandFingerprint}
            )
            """)
    int insertRecurrenceLink(@Param("id") Long id,
                             @Param("currentCaseId") Long currentCaseId,
                             @Param("previousCaseId") Long previousCaseId,
                             @Param("domain") Integer domain,
                             @Param("relationType") String relationType,
                             @Param("rootCauseCategory") String rootCauseCategory,
                             @Param("note") String note,
                             @Param("linkedByUid") Long linkedByUid,
                             @Param("commandId") String commandId,
                             @Param("commandFingerprint") String commandFingerprint);

    @Select("""
            SELECT COUNT(*)
            FROM t_collab_content_maintenance_dispatch_batch_event
            WHERE id = #{eventId}
              AND batch_id = #{batchId}
            """)
    int coordinationEventExists(@Param("batchId") Long batchId, @Param("eventId") Long eventId);

    @Select("""
            SELECT COUNT(*)
            FROM t_channel_quality_review_risk_case_event
            WHERE id = #{eventId}
              AND case_id = #{caseId}
            """)
    int caseEventExists(@Param("caseId") Long caseId, @Param("eventId") Long eventId);

    @Select("""
            SELECT COUNT(*)
            FROM t_collab_content_maintenance_task
            WHERE id = #{taskId}
              AND dispatch_batch_id = #{batchId}
              AND source_type = 'CHANNEL_HEALTH'
            """)
    int maintenanceTaskExists(@Param("batchId") Long batchId, @Param("taskId") Long taskId);

    @Select("""
            <script>
            SELECT id,
                   case_id AS caseId,
                   batch_id AS batchId,
                   domain,
                   milestone_code AS milestoneCode,
                   occurred_at AS occurredAt,
                   source_type AS sourceType,
                   source_id AS sourceId,
                   owner_uid AS ownerUid,
                   responsibility_scope AS responsibilityScope,
                   responsibility_epoch AS responsibilityEpoch,
                   case_status_after AS caseStatusAfter,
                   required_action AS requiredAction,
                   retrospective_id AS retrospectiveId,
                   contract_version AS contractVersion,
                   fact_version AS factVersion,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_governance_milestone
            WHERE case_id = #{caseId}
              AND (#{cursor} = 0 OR id &lt; #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ChannelQualityReviewRiskCaseGovernanceMilestoneRow> listGovernanceMilestones(
            @Param("caseId") Long caseId,
            @Param("cursor") long cursor,
            @Param("limit") int limit);

    @Select("""
            SELECT COALESCE(MAX(responsibility_epoch), 0)
            FROM t_channel_quality_review_risk_case_governance_milestone
            WHERE case_id = #{caseId}
              AND responsibility_scope = #{responsibilityScope}
            """)
    int maxResponsibilityEpoch(@Param("caseId") Long caseId,
                               @Param("responsibilityScope") String responsibilityScope);

    @Select("""
            SELECT id,
                   case_id AS caseId,
                   batch_id AS batchId,
                   domain,
                   milestone_code AS milestoneCode,
                   occurred_at AS occurredAt,
                   source_type AS sourceType,
                   source_id AS sourceId,
                   owner_uid AS ownerUid,
                   responsibility_scope AS responsibilityScope,
                   responsibility_epoch AS responsibilityEpoch,
                   case_status_after AS caseStatusAfter,
                   required_action AS requiredAction,
                   retrospective_id AS retrospectiveId,
                   contract_version AS contractVersion,
                   fact_version AS factVersion,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_governance_milestone
            WHERE source_type = #{sourceType}
              AND source_id = #{sourceId}
              AND milestone_code = #{milestoneCode}
            """)
    ChannelQualityReviewRiskCaseGovernanceMilestoneRow selectGovernanceMilestoneBySource(
            @Param("sourceType") String sourceType,
            @Param("sourceId") Long sourceId,
            @Param("milestoneCode") String milestoneCode);

    @Insert("""
            INSERT INTO t_channel_quality_review_risk_case_governance_milestone (
                id, case_id, batch_id, domain, milestone_code, occurred_at, source_type, source_id,
                owner_uid, responsibility_scope, responsibility_epoch, case_status_after,
                required_action, retrospective_id, contract_version, fact_version
            ) VALUES (
                #{id}, #{caseId}, #{batchId}, #{domain}, #{milestoneCode}, #{occurredAt},
                #{sourceType}, #{sourceId}, #{ownerUid}, #{responsibilityScope},
                #{responsibilityEpoch}, #{caseStatusAfter}, #{requiredAction}, #{retrospectiveId},
                'V41_FACT_V1', 1
            )
            """)
    int insertGovernanceMilestone(@Param("id") Long id,
                                  @Param("caseId") Long caseId,
                                  @Param("batchId") Long batchId,
                                  @Param("domain") Integer domain,
                                  @Param("milestoneCode") String milestoneCode,
                                  @Param("occurredAt") LocalDateTime occurredAt,
                                  @Param("sourceType") String sourceType,
                                  @Param("sourceId") Long sourceId,
                                  @Param("ownerUid") Long ownerUid,
                                  @Param("responsibilityScope") String responsibilityScope,
                                  @Param("responsibilityEpoch") Integer responsibilityEpoch,
                                  @Param("caseStatusAfter") String caseStatusAfter,
                                  @Param("requiredAction") String requiredAction,
                                  @Param("retrospectiveId") Long retrospectiveId);

    @Select("""
            SELECT id,
                   case_id AS caseId,
                   batch_id AS batchId,
                   operator_uid AS operatorUid,
                   event_type AS eventType,
                   previous_status AS previousStatus,
                   status,
                   owner_uid AS ownerUid,
                   observed_coordination_version AS observedCoordinationVersion,
                   case_version AS caseVersion,
                   note,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_event
            WHERE id = #{eventId}
              AND case_id = #{caseId}
            """)
    ChannelQualityReviewRiskCaseGovernanceV40EventRow selectV40Event(
            @Param("caseId") Long caseId,
            @Param("eventId") Long eventId);
}
