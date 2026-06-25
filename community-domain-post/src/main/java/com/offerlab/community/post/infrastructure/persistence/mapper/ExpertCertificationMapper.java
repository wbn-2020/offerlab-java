package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.ExpertCertificationApplicationPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExpertCertificationMapper extends BaseMapper<ExpertCertificationApplicationPO> {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_expert_cert_application'
            """)
    int tableExists();

    @Select("""
            SELECT id,
                   applicant_uid AS applicantUid,
                   domain,
                   status,
                   evidence_summary AS evidenceSummary,
                   evidence_links_json AS evidenceLinksJson,
                   eligibility_passed AS eligibilityPassed,
                   eligibility_summary AS eligibilitySummary,
                   eligibility_snapshot_json AS eligibilitySnapshotJson,
                   risk_acknowledged AS riskAcknowledged,
                   risk_warning AS riskWarning,
                   reviewer_uid AS reviewerUid,
                   review_note AS reviewNote,
                   review_time AS reviewTime,
                   revoked_by AS revokedBy,
                   revoke_note AS revokeNote,
                   revoked_time AS revokedTime,
                   create_time AS createTime,
                   update_time AS updateTime,
                   is_deleted AS isDeleted
            FROM t_expert_cert_application
            WHERE applicant_uid = #{applicantUid}
              AND domain = #{domain}
              AND is_deleted = 0
              AND status IN (10, 20)
            ORDER BY update_time DESC, id DESC
            LIMIT 1
            """)
    ExpertCertificationApplicationPO selectActiveByApplicantAndDomain(@Param("applicantUid") Long applicantUid,
                                                                      @Param("domain") Integer domain);

    @Select("""
            <script>
            SELECT id,
                   applicant_uid AS applicantUid,
                   domain,
                   status,
                   evidence_summary AS evidenceSummary,
                   evidence_links_json AS evidenceLinksJson,
                   eligibility_passed AS eligibilityPassed,
                   eligibility_summary AS eligibilitySummary,
                   eligibility_snapshot_json AS eligibilitySnapshotJson,
                   risk_acknowledged AS riskAcknowledged,
                   risk_warning AS riskWarning,
                   reviewer_uid AS reviewerUid,
                   review_note AS reviewNote,
                   review_time AS reviewTime,
                   revoked_by AS revokedBy,
                   revoke_note AS revokeNote,
                   revoked_time AS revokedTime,
                   create_time AS createTime,
                   update_time AS updateTime,
                   is_deleted AS isDeleted
            FROM t_expert_cert_application
            WHERE applicant_uid = #{applicantUid}
              AND is_deleted = 0
              <if test="domain != null">
              AND domain = #{domain}
              </if>
            ORDER BY update_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ExpertCertificationApplicationPO> selectMine(@Param("applicantUid") Long applicantUid,
                                                      @Param("domain") Integer domain,
                                                      @Param("limit") Integer limit);

    @Select("""
            <script>
            SELECT id,
                   applicant_uid AS applicantUid,
                   domain,
                   status,
                   evidence_summary AS evidenceSummary,
                   evidence_links_json AS evidenceLinksJson,
                   eligibility_passed AS eligibilityPassed,
                   eligibility_summary AS eligibilitySummary,
                   eligibility_snapshot_json AS eligibilitySnapshotJson,
                   risk_acknowledged AS riskAcknowledged,
                   risk_warning AS riskWarning,
                   reviewer_uid AS reviewerUid,
                   review_note AS reviewNote,
                   review_time AS reviewTime,
                   revoked_by AS revokedBy,
                   revoke_note AS revokeNote,
                   revoked_time AS revokedTime,
                   create_time AS createTime,
                   update_time AS updateTime,
                   is_deleted AS isDeleted
            FROM t_expert_cert_application
            WHERE is_deleted = 0
              <if test="domain != null">
              AND domain = #{domain}
              </if>
              <if test="status != null">
              AND status = #{status}
              </if>
            ORDER BY update_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ExpertCertificationApplicationPO> selectReviewQueue(@Param("domain") Integer domain,
                                                             @Param("status") Integer status,
                                                             @Param("limit") Integer limit);

    @Select("SELECT COALESCE(GET_LOCK(#{lockName}, #{timeoutSeconds}), 0)")
    Integer acquireNamedLock(@Param("lockName") String lockName, @Param("timeoutSeconds") int timeoutSeconds);

    @Select("SELECT COALESCE(RELEASE_LOCK(#{lockName}), 0)")
    Integer releaseNamedLock(@Param("lockName") String lockName);
}
