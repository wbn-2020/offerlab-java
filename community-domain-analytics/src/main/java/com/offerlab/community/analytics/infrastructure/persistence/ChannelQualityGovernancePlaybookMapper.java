package com.offerlab.community.analytics.infrastructure.persistence;

import com.offerlab.community.analytics.api.dto.ChannelQualityGovernancePlaybookDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ChannelQualityGovernancePlaybookMapper {
    @Select("""
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = DATABASE() AND table_name IN (
              't_channel_quality_governance_playbook',
              't_channel_quality_governance_playbook_version',
              't_channel_quality_review_risk_case_playbook',
              't_channel_quality_review_risk_case_playbook_check',
              't_channel_quality_governance_playbook_command')
            """)
    int tableCount();

    @Select("""
            SELECT id, playbook_code AS playbookCode, latest_version_no AS latestVersionNo,
                   playbook_version AS playbookVersion, create_time AS createTime, update_time AS updateTime
            FROM t_channel_quality_governance_playbook WHERE id = #{id}
            """)
    ChannelQualityGovernancePlaybookDTO selectPlaybook(@Param("id") Long id);

    @Select("""
            SELECT id, playbook_code AS playbookCode, latest_version_no AS latestVersionNo,
                   playbook_version AS playbookVersion, create_time AS createTime, update_time AS updateTime
            FROM t_channel_quality_governance_playbook ORDER BY update_time DESC, id DESC
            """)
    List<ChannelQualityGovernancePlaybookDTO> listPlaybooks();

    @Select("""
            SELECT id, playbook_code AS playbookCode, latest_version_no AS latestVersionNo,
                   playbook_version AS playbookVersion, create_time AS createTime, update_time AS updateTime
            FROM t_channel_quality_governance_playbook WHERE id = #{id} FOR UPDATE
            """)
    ChannelQualityGovernancePlaybookDTO lockPlaybook(@Param("id") Long id);

    @Select("""
            SELECT id, playbook_id AS playbookId, version_no AS versionNo, status,
                   content_schema_version AS contentSchemaVersion, content_summary AS contentSummary,
                   canonical_content_json AS canonicalContentJson, content_hash AS contentHash,
                   published_at AS publishedAt, retired_at AS retiredAt, created_by_uid AS createdByUid,
                   create_time AS createTime
            FROM t_channel_quality_governance_playbook_version WHERE id = #{id}
            """)
    ChannelQualityGovernancePlaybookDTO.VersionDTO selectVersion(@Param("id") Long id);

    @Select("""
            SELECT id, playbook_id AS playbookId, version_no AS versionNo, status,
                   content_schema_version AS contentSchemaVersion, content_summary AS contentSummary,
                   canonical_content_json AS canonicalContentJson, content_hash AS contentHash,
                   published_at AS publishedAt, retired_at AS retiredAt, created_by_uid AS createdByUid,
                   create_time AS createTime
            FROM t_channel_quality_governance_playbook_version WHERE id = #{id} FOR UPDATE
            """)
    ChannelQualityGovernancePlaybookDTO.VersionDTO lockVersion(@Param("id") Long id);

    @Select("""
            SELECT id, playbook_id AS playbookId, version_no AS versionNo, status,
                   content_schema_version AS contentSchemaVersion, content_summary AS contentSummary,
                   canonical_content_json AS canonicalContentJson, content_hash AS contentHash,
                   published_at AS publishedAt, retired_at AS retiredAt, created_by_uid AS createdByUid,
                   create_time AS createTime
            FROM t_channel_quality_governance_playbook_version
            WHERE status = 'PUBLISHED' ORDER BY playbook_id ASC, version_no DESC
            """)
    List<ChannelQualityGovernancePlaybookDTO.VersionDTO> listPublishedVersions();

    @Select("""
            SELECT id, playbook_id AS playbookId, version_no AS versionNo, status,
                   content_schema_version AS contentSchemaVersion, content_summary AS contentSummary,
                   canonical_content_json AS canonicalContentJson, content_hash AS contentHash,
                   published_at AS publishedAt, retired_at AS retiredAt, created_by_uid AS createdByUid,
                   create_time AS createTime
            FROM t_channel_quality_governance_playbook_version
            WHERE playbook_id = #{playbookId} ORDER BY version_no DESC
            """)
    List<ChannelQualityGovernancePlaybookDTO.VersionDTO> listVersions(@Param("playbookId") Long playbookId);

    @Insert("""
            INSERT INTO t_channel_quality_governance_playbook
              (id, playbook_code, latest_version_no, playbook_version)
            VALUES (#{id}, #{code}, 1, 1)
            """)
    int insertPlaybook(@Param("id") Long id, @Param("code") String code);

    @Insert("""
            INSERT INTO t_channel_quality_governance_playbook_version
              (id, playbook_id, version_no, status, content_schema_version, content_summary,
               canonical_content_json, content_hash, created_by_uid)
            VALUES (#{id}, #{playbookId}, #{versionNo}, 'DRAFT', #{schemaVersion}, #{summary},
                    CAST(#{canonicalJson} AS JSON), #{contentHash}, #{operatorUid})
            """)
    int insertDraftVersion(@Param("id") Long id, @Param("playbookId") Long playbookId,
                           @Param("versionNo") Integer versionNo, @Param("schemaVersion") String schemaVersion,
                           @Param("summary") String summary, @Param("canonicalJson") String canonicalJson,
                           @Param("contentHash") String contentHash, @Param("operatorUid") Long operatorUid);

    @Update("""
            UPDATE t_channel_quality_governance_playbook
            SET latest_version_no = #{nextVersionNo}, playbook_version = playbook_version + 1
            WHERE id = #{playbookId} AND playbook_version = #{expectedPlaybookVersion}
            """)
    int advancePlaybookVersion(@Param("playbookId") Long playbookId,
                               @Param("expectedPlaybookVersion") Integer expectedPlaybookVersion,
                               @Param("nextVersionNo") Integer nextVersionNo);

    @Update("""
            UPDATE t_channel_quality_governance_playbook_version
            SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{versionId} AND status = 'DRAFT' AND content_hash = #{contentHash}
            """)
    int publishVersion(@Param("versionId") Long versionId, @Param("contentHash") String contentHash);

    @Update("""
            UPDATE t_channel_quality_governance_playbook_version
            SET status = 'RETIRED', retired_at = CURRENT_TIMESTAMP(3)
            WHERE playbook_id = #{playbookId} AND status = 'PUBLISHED' AND id <> #{exceptVersionId}
            """)
    int retirePriorPublished(@Param("playbookId") Long playbookId, @Param("exceptVersionId") Long exceptVersionId);

    @Update("""
            UPDATE t_channel_quality_governance_playbook_version
            SET status = 'RETIRED', retired_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{versionId} AND status IN ('DRAFT', 'PUBLISHED') AND content_hash = #{contentHash}
            """)
    int retireVersion(@Param("versionId") Long versionId, @Param("contentHash") String contentHash);

    @Select("""
            SELECT id, case_id AS caseId, playbook_id AS playbookId, playbook_version_id AS playbookVersionId,
                   source_content_hash AS sourceContentHash, snapshot_summary AS snapshotSummary,
                   snapshot_content_json AS snapshotContentJson, snapshot_hash AS snapshotHash,
                   applicability_result AS applicabilityResult,
                   applicability_reason_codes AS applicabilityReasonCodesJson,
                   observed_case_version AS observedCaseVersion,
                   observed_governance_fact_version AS observedGovernanceFactVersion,
                   observed_governance_snapshot_etag AS observedGovernanceSnapshotEtag,
                   binding_status AS bindingStatus, completion_evaluation_hash AS completionEvaluationHash,
                   binding_version AS bindingVersion, create_time AS createTime, update_time AS updateTime
            FROM t_channel_quality_review_risk_case_playbook WHERE id = #{id}
            """)
    ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO selectCasePlaybook(@Param("id") Long id);

    @Select("""
            SELECT id, case_id AS caseId, playbook_id AS playbookId, playbook_version_id AS playbookVersionId,
                   source_content_hash AS sourceContentHash, snapshot_summary AS snapshotSummary,
                   snapshot_content_json AS snapshotContentJson, snapshot_hash AS snapshotHash,
                   applicability_result AS applicabilityResult,
                   applicability_reason_codes AS applicabilityReasonCodesJson,
                   observed_case_version AS observedCaseVersion,
                   observed_governance_fact_version AS observedGovernanceFactVersion,
                   observed_governance_snapshot_etag AS observedGovernanceSnapshotEtag,
                   binding_status AS bindingStatus, completion_evaluation_hash AS completionEvaluationHash,
                   binding_version AS bindingVersion, create_time AS createTime, update_time AS updateTime
            FROM t_channel_quality_review_risk_case_playbook WHERE id = #{id} FOR UPDATE
            """)
    ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO lockCasePlaybook(@Param("id") Long id);

    @Select("""
            SELECT id, case_id AS caseId, playbook_id AS playbookId, playbook_version_id AS playbookVersionId,
                   source_content_hash AS sourceContentHash, snapshot_summary AS snapshotSummary,
                   snapshot_content_json AS snapshotContentJson, snapshot_hash AS snapshotHash,
                   applicability_result AS applicabilityResult,
                   applicability_reason_codes AS applicabilityReasonCodesJson,
                   observed_case_version AS observedCaseVersion,
                   observed_governance_fact_version AS observedGovernanceFactVersion,
                   observed_governance_snapshot_etag AS observedGovernanceSnapshotEtag,
                   binding_status AS bindingStatus, completion_evaluation_hash AS completionEvaluationHash,
                   binding_version AS bindingVersion, create_time AS createTime, update_time AS updateTime
            FROM t_channel_quality_review_risk_case_playbook WHERE case_id = #{caseId} ORDER BY id DESC
            """)
    List<ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO> listCasePlaybooks(@Param("caseId") Long caseId);

    @Insert("""
            INSERT INTO t_channel_quality_review_risk_case_playbook
              (id, case_id, playbook_id, playbook_version_id, source_content_hash, snapshot_summary,
               snapshot_content_json, snapshot_hash, applicability_result, applicability_reason_codes,
               observed_case_version, observed_governance_fact_version, observed_governance_snapshot_etag,
               binding_status, binding_version)
            VALUES (#{id}, #{caseId}, #{playbookId}, #{playbookVersionId}, #{sourceContentHash}, #{summary},
                    CAST(#{snapshotJson} AS JSON), #{snapshotHash}, #{applicabilityResult},
                    CAST(#{reasonCodesJson} AS JSON), #{caseVersion}, #{factVersion}, #{snapshotEtag},
                    'PROPOSED', 0)
            """)
    int insertCasePlaybook(@Param("id") Long id, @Param("caseId") Long caseId,
                           @Param("playbookId") Long playbookId, @Param("playbookVersionId") Long playbookVersionId,
                           @Param("sourceContentHash") String sourceContentHash, @Param("summary") String summary,
                           @Param("snapshotJson") String snapshotJson, @Param("snapshotHash") String snapshotHash,
                           @Param("applicabilityResult") String applicabilityResult,
                           @Param("reasonCodesJson") String reasonCodesJson, @Param("caseVersion") Integer caseVersion,
                           @Param("factVersion") Integer factVersion, @Param("snapshotEtag") String snapshotEtag);

    @Update("""
            UPDATE t_channel_quality_review_risk_case_playbook
            SET binding_status = #{targetStatus}, binding_version = binding_version + 1,
                completion_evaluation_hash = #{evaluationHash}
            WHERE id = #{id} AND binding_status = #{expectedStatus} AND binding_version = #{expectedBindingVersion}
            """)
    int transitionCasePlaybook(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
                               @Param("targetStatus") String targetStatus,
                               @Param("expectedBindingVersion") Integer expectedBindingVersion,
                               @Param("evaluationHash") String evaluationHash);

    @Select("""
            SELECT id, case_playbook_id AS casePlaybookId, check_key AS checkKey, title, instruction,
                   evidence_requirement AS evidenceRequirement, state, observed_fact_version AS observedFactVersion,
                   observed_snapshot_etag AS observedSnapshotEtag, verified_by_uid AS verifiedByUid,
                   verification_note AS verificationNote, waiver_reason AS waiverReason,
                   check_version AS checkVersion, create_time AS createTime, update_time AS updateTime
            FROM t_channel_quality_review_risk_case_playbook_check
            WHERE case_playbook_id = #{casePlaybookId} ORDER BY id ASC
            """)
    List<ChannelQualityGovernancePlaybookDTO.CheckDTO> listChecks(@Param("casePlaybookId") Long casePlaybookId);

    @Select("""
            SELECT id, case_playbook_id AS casePlaybookId, check_key AS checkKey, title, instruction,
                   evidence_requirement AS evidenceRequirement, state, observed_fact_version AS observedFactVersion,
                   observed_snapshot_etag AS observedSnapshotEtag, verified_by_uid AS verifiedByUid,
                   verification_note AS verificationNote, waiver_reason AS waiverReason,
                   check_version AS checkVersion, create_time AS createTime, update_time AS updateTime
            FROM t_channel_quality_review_risk_case_playbook_check
            WHERE case_playbook_id = #{casePlaybookId} AND check_key = #{checkKey} FOR UPDATE
            """)
    ChannelQualityGovernancePlaybookDTO.CheckDTO lockCheck(@Param("casePlaybookId") Long casePlaybookId,
                                                           @Param("checkKey") String checkKey);

    @Insert("""
            INSERT INTO t_channel_quality_review_risk_case_playbook_check
              (id, case_playbook_id, check_key, title, instruction, evidence_requirement, state, check_version)
            VALUES (#{id}, #{casePlaybookId}, #{checkKey}, #{title}, #{instruction}, #{evidenceRequirement},
                    'PENDING', 0)
            """)
    int insertCheck(@Param("id") Long id, @Param("casePlaybookId") Long casePlaybookId,
                    @Param("checkKey") String checkKey, @Param("title") String title,
                    @Param("instruction") String instruction, @Param("evidenceRequirement") String evidenceRequirement);

    @Update("""
            UPDATE t_channel_quality_review_risk_case_playbook_check
            SET state = #{targetState}, observed_fact_version = #{factVersion}, observed_snapshot_etag = #{etag},
                verified_by_uid = #{operatorUid}, verification_note = #{note}, waiver_reason = #{waiverReason},
                check_version = check_version + 1
            WHERE id = #{checkId} AND state = 'PENDING' AND check_version = #{expectedCheckVersion}
            """)
    int transitionCheck(@Param("checkId") Long checkId, @Param("targetState") String targetState,
                        @Param("factVersion") Integer factVersion, @Param("etag") String etag,
                        @Param("operatorUid") Long operatorUid, @Param("note") String note,
                        @Param("waiverReason") String waiverReason,
                        @Param("expectedCheckVersion") Integer expectedCheckVersion);

    @Insert("""
            INSERT INTO t_channel_quality_review_risk_case_playbook_check_evidence
              (check_id, evidence_id, created_by_uid)
            VALUES (#{checkId}, #{evidenceId}, #{operatorUid})
            """)
    int insertCheckEvidence(@Param("checkId") Long checkId,
                             @Param("evidenceId") Long evidenceId,
                             @Param("operatorUid") Long operatorUid);

    @Insert("""
            INSERT INTO t_channel_quality_review_risk_case_playbook_event
              (id, case_playbook_id, case_id, event_type, previous_status, status, operator_uid, idempotency_key, reason)
            VALUES (#{id}, #{casePlaybookId}, #{caseId}, #{eventType}, #{previousStatus}, #{status},
                    #{operatorUid}, #{idempotencyKey}, #{reason})
            """)
    int insertCaseEvent(@Param("id") Long id, @Param("casePlaybookId") Long casePlaybookId,
                        @Param("caseId") Long caseId, @Param("eventType") String eventType,
                        @Param("previousStatus") String previousStatus, @Param("status") String status,
                        @Param("operatorUid") Long operatorUid, @Param("idempotencyKey") String idempotencyKey,
                        @Param("reason") String reason);

    @Insert("""
            INSERT INTO t_channel_quality_governance_playbook_event
              (id, playbook_id, playbook_version_id, event_type, operator_uid, reason)
            VALUES (#{id}, #{playbookId}, #{playbookVersionId}, #{eventType}, #{operatorUid}, #{reason})
            """)
    int insertPlaybookEvent(@Param("id") Long id, @Param("playbookId") Long playbookId,
                            @Param("playbookVersionId") Long playbookVersionId, @Param("eventType") String eventType,
                            @Param("operatorUid") Long operatorUid, @Param("reason") String reason);

    @Select("""
            SELECT request_fingerprint AS requestFingerprint, result_json AS resultJson
            FROM t_channel_quality_governance_playbook_command
            WHERE operator_uid = #{operatorUid} AND idempotency_key = #{idempotencyKey}
            """)
    Map<String, Object> selectCommand(@Param("operatorUid") Long operatorUid,
                                      @Param("idempotencyKey") String idempotencyKey);

    @Insert("""
            INSERT INTO t_channel_quality_governance_playbook_command
              (operator_uid, idempotency_key, request_fingerprint, result_json, completed_at)
            VALUES (#{operatorUid}, #{idempotencyKey}, #{fingerprint}, CAST(#{resultJson} AS JSON), CURRENT_TIMESTAMP(3))
            """)
    int insertCommand(@Param("operatorUid") Long operatorUid, @Param("idempotencyKey") String idempotencyKey,
                      @Param("fingerprint") String fingerprint, @Param("resultJson") String resultJson);
}
