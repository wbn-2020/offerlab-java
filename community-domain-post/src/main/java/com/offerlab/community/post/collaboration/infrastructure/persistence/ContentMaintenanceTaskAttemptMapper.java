package com.offerlab.community.post.collaboration.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Persistence boundary for append-only delivery attempts. The task mapper
 * inherits this interface so the existing single MyBatis mapper bean remains
 * the only injected collaboration mapper.
 */
public interface ContentMaintenanceTaskAttemptMapper {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_collab_content_maintenance_task_attempt'
            """)
    int attemptTableExists();

    @Insert("""
            INSERT INTO t_collab_content_maintenance_task_attempt (
                id, task_id, attempt_no, delivery_type, delivery_ref_id,
                delivery_post_id, note, submitted_by_uid, submitted_at,
                decision, reason_code, reviewed_by_uid, reviewed_at,
                review_note
            ) VALUES (
                #{id}, #{taskId}, #{attemptNo}, #{deliveryType}, #{deliveryRefId},
                #{deliveryPostId}, #{note}, #{submittedByUid}, CURRENT_TIMESTAMP(3),
                NULL, NULL, NULL, NULL, NULL
            )
            """)
    int insertAttempt(@Param("id") Long id,
                      @Param("taskId") Long taskId,
                      @Param("attemptNo") Integer attemptNo,
                      @Param("deliveryType") String deliveryType,
                      @Param("deliveryRefId") Long deliveryRefId,
                      @Param("deliveryPostId") Long deliveryPostId,
                      @Param("note") String note,
                      @Param("submittedByUid") Long submittedByUid);

    @Insert("""
            INSERT INTO t_collab_content_maintenance_task_attempt (
                id, task_id, attempt_no, delivery_type, delivery_ref_id,
                delivery_post_id, note, submitted_by_uid, submitted_at,
                decision, reason_code, reviewed_by_uid, reviewed_at,
                review_note
            ) VALUES (
                #{id}, #{taskId}, #{attemptNo}, #{deliveryType}, #{deliveryRefId},
                #{deliveryPostId}, #{note}, #{submittedByUid}, #{submittedAt},
                NULL, NULL, NULL, NULL, NULL
            )
            """)
    int insertHistoricalAttempt(@Param("id") Long id,
                                @Param("taskId") Long taskId,
                                @Param("attemptNo") Integer attemptNo,
                                @Param("deliveryType") String deliveryType,
                                @Param("deliveryRefId") Long deliveryRefId,
                                @Param("deliveryPostId") Long deliveryPostId,
                                @Param("note") String note,
                                @Param("submittedByUid") Long submittedByUid,
                                @Param("submittedAt") LocalDateTime submittedAt);

    @Select("""
            SELECT id,
                   task_id AS taskId,
                   attempt_no AS attemptNo,
                   delivery_type AS deliveryType,
                   delivery_ref_id AS deliveryRefId,
                   delivery_post_id AS deliveryPostId,
                   note,
                   submitted_by_uid AS submittedByUid,
                   submitted_at AS submittedAt,
                   decision,
                   reason_code AS reasonCode,
                   reviewed_by_uid AS reviewedByUid,
                   reviewed_at AS reviewedAt,
                   review_note AS reviewNote,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_maintenance_task_attempt
            WHERE task_id = #{taskId}
              AND attempt_no = #{attemptNo}
            FOR UPDATE
            """)
    ContentMaintenanceTaskAttemptRow lockAttempt(
            @Param("taskId") Long taskId,
            @Param("attemptNo") Integer attemptNo);

    @Select("""
            SELECT id,
                   task_id AS taskId,
                   attempt_no AS attemptNo,
                   delivery_type AS deliveryType,
                   delivery_ref_id AS deliveryRefId,
                   delivery_post_id AS deliveryPostId,
                   note,
                   submitted_by_uid AS submittedByUid,
                   submitted_at AS submittedAt,
                   decision,
                   reason_code AS reasonCode,
                   reviewed_by_uid AS reviewedByUid,
                   reviewed_at AS reviewedAt,
                   review_note AS reviewNote,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_maintenance_task_attempt
            WHERE task_id = #{taskId}
            ORDER BY attempt_no DESC
            """)
    List<ContentMaintenanceTaskAttemptRow> listAttempts(@Param("taskId") Long taskId);

    @Update("""
            UPDATE t_collab_content_maintenance_task_attempt
            SET decision = #{decision},
                reason_code = #{reasonCode},
                reviewed_by_uid = #{reviewerUid},
                reviewed_at = CURRENT_TIMESTAMP(3),
                review_note = #{reviewNote},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND task_id = #{taskId}
              AND decision IS NULL
            """)
    int decideAttempt(@Param("id") Long id,
                      @Param("taskId") Long taskId,
                      @Param("decision") String decision,
                      @Param("reasonCode") String reasonCode,
                      @Param("reviewerUid") Long reviewerUid,
                      @Param("reviewNote") String reviewNote);
}
