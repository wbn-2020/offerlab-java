package com.offerlab.community.analytics.infrastructure.persistence;

import com.offerlab.community.post.api.ContentMaintenanceTaskRevisionKey;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface ChannelQualityReviewCandidateDispositionMapper {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_collab_content_maintenance_candidate_disposition'
            """)
    int tableExists();

    @Select("""
            <script>
            SELECT id,
                   domain,
                   source_type AS sourceType,
                   source_post_id AS sourcePostId,
                   source_ref_id AS sourceRefId,
                   disposition_state AS state,
                   reason_code AS reasonCode,
                   snoozed_until AS snoozedUntil,
                   updated_by_uid AS updatedByUid,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_maintenance_candidate_disposition
            WHERE source_type = #{sourceType}
              AND disposition_state IN ('DISMISSED', 'SNOOZED')
              AND (
                disposition_state = 'DISMISSED'
                OR snoozed_until > CURRENT_TIMESTAMP(3)
              )
              AND (
                <foreach collection="keys" item="key" separator=" OR ">
                  (source_post_id = #{key.sourcePostId}
                   AND source_ref_id = #{key.sourceRefId})
                </foreach>
              )
            </script>
            """)
    List<ChannelQualityReviewCandidateDispositionRow> listActiveBySourceRevision(
            @Param("sourceType") String sourceType,
            @Param("keys") Collection<ContentMaintenanceTaskRevisionKey> keys);

    @Select("""
            SELECT id,
                   domain,
                   source_type AS sourceType,
                   source_post_id AS sourcePostId,
                   source_ref_id AS sourceRefId,
                   disposition_state AS state,
                   reason_code AS reasonCode,
                   snoozed_until AS snoozedUntil,
                   updated_by_uid AS updatedByUid,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_maintenance_candidate_disposition
            WHERE source_type = #{sourceType}
              AND source_post_id = #{sourcePostId}
              AND source_ref_id = #{sourceRefId}
            """)
    ChannelQualityReviewCandidateDispositionRow selectBySourceRevision(
            @Param("sourceType") String sourceType,
            @Param("sourcePostId") Long sourcePostId,
            @Param("sourceRefId") Long sourceRefId);

    @Insert("""
            INSERT INTO t_collab_content_maintenance_candidate_disposition (
                id, domain, source_type, source_post_id, source_ref_id,
                disposition_state, reason_code, snoozed_until, updated_by_uid
            ) VALUES (
                #{id}, #{domain}, #{sourceType}, #{sourcePostId}, #{sourceRefId},
                #{state}, #{reasonCode}, #{snoozedUntil}, #{updatedByUid}
            )
            ON DUPLICATE KEY UPDATE
                domain = VALUES(domain),
                disposition_state = VALUES(disposition_state),
                reason_code = VALUES(reason_code),
                snoozed_until = VALUES(snoozed_until),
                updated_by_uid = VALUES(updated_by_uid),
                update_time = CURRENT_TIMESTAMP(3)
            """)
    int upsert(@Param("id") Long id,
               @Param("domain") Integer domain,
               @Param("sourceType") String sourceType,
               @Param("sourcePostId") Long sourcePostId,
               @Param("sourceRefId") Long sourceRefId,
               @Param("state") String state,
               @Param("reasonCode") String reasonCode,
               @Param("snoozedUntil") LocalDateTime snoozedUntil,
               @Param("updatedByUid") Long updatedByUid);

    @Insert("""
            INSERT INTO t_collab_content_maintenance_revision_gate (
                source_type, source_post_id, source_ref_id
            ) VALUES (
                #{sourceType}, #{sourcePostId}, #{sourceRefId}
            )
            ON DUPLICATE KEY UPDATE
                update_time = CURRENT_TIMESTAMP(3)
            """)
    int acquireSourceRevisionGate(@Param("sourceType") String sourceType,
                                  @Param("sourcePostId") Long sourcePostId,
                                  @Param("sourceRefId") Long sourceRefId);
}
