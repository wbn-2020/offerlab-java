package com.offerlab.community.post.collaboration.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CollaborationActionQueryMapper {

    @Select("""
            <script>
            SELECT actionType,
                   sourceType,
                   sourceId,
                   sourceStatus,
                   title,
                   reason,
                   targetPath,
                   lastEventId,
                   updatedAt,
                   canAct
            FROM (
                SELECT 'NEED_SUBMIT' AS actionType,
                       'NEED' AS sourceType,
                       n.id AS sourceId,
                       n.need_status AS sourceStatus,
                       n.title AS title,
                       'NEED_READY_FOR_SUBMISSION' AS reason,
                       CONCAT('/collaboration/needs/', n.id) AS targetPath,
                       (SELECT e.id
                          FROM t_collab_content_need_event e
                         WHERE e.need_id = n.id
                         ORDER BY e.id DESC
                         LIMIT 1) AS lastEventId,
                       n.update_time AS updatedAt,
                       1 AS canAct
                  FROM t_collab_content_need n
                 WHERE n.moderation_hidden = 0
                   AND n.claimed_by_uid = #{uid}
                   AND n.need_status = 'CLAIMED'
                   AND n.reject_reason IS NULL

                UNION ALL

                SELECT 'NEED_REVISE' AS actionType,
                       'NEED' AS sourceType,
                       n.id AS sourceId,
                       n.need_status AS sourceStatus,
                       n.title AS title,
                       'NEED_SUBMISSION_RETURNED_FOR_REVISION' AS reason,
                       CONCAT('/collaboration/needs/', n.id) AS targetPath,
                       (SELECT e.id
                          FROM t_collab_content_need_event e
                         WHERE e.need_id = n.id
                         ORDER BY e.id DESC
                         LIMIT 1) AS lastEventId,
                       n.update_time AS updatedAt,
                       1 AS canAct
                  FROM t_collab_content_need n
                 WHERE n.moderation_hidden = 0
                   AND n.claimed_by_uid = #{uid}
                   AND n.need_status = 'CLAIMED'
                   AND n.reject_reason IS NOT NULL
                   AND (SELECT e.event_type
                          FROM t_collab_content_need_event e
                         WHERE e.need_id = n.id
                         ORDER BY e.id DESC
                         LIMIT 1) = 'REJECTED'

                UNION ALL

                SELECT 'NEED_REVIEW' AS actionType,
                       'NEED' AS sourceType,
                       n.id AS sourceId,
                       n.need_status AS sourceStatus,
                       n.title AS title,
                       'NEED_SUBMISSION_AWAITS_REVIEW' AS reason,
                       CONCAT('/collaboration/needs/', n.id) AS targetPath,
                       (SELECT e.id
                          FROM t_collab_content_need_event e
                         WHERE e.need_id = n.id
                         ORDER BY e.id DESC
                         LIMIT 1) AS lastEventId,
                       n.update_time AS updatedAt,
                       1 AS canAct
                  FROM t_collab_content_need n
                 WHERE n.moderation_hidden = 0
                   AND n.creator_uid = #{uid}
                   AND n.need_status = 'SUBMITTED'
                   AND (n.claimed_by_uid IS NULL OR n.claimed_by_uid &lt;&gt; #{uid})

                UNION ALL

                SELECT 'NEED_STALLED' AS actionType,
                       'NEED' AS sourceType,
                       n.id AS sourceId,
                       n.need_status AS sourceStatus,
                       n.title AS title,
                       'NEED_CLAIM_HAS_NO_RECENT_PROGRESS' AS reason,
                       CONCAT('/collaboration/needs/', n.id) AS targetPath,
                       (SELECT e.id
                          FROM t_collab_content_need_event e
                         WHERE e.need_id = n.id
                         ORDER BY e.id DESC
                         LIMIT 1) AS lastEventId,
                       n.update_time AS updatedAt,
                       1 AS canAct
                  FROM t_collab_content_need n
                 WHERE n.moderation_hidden = 0
                   AND n.claimed_by_uid = #{uid}
                   AND n.need_status = 'CLAIMED'
                   AND COALESCE(n.last_progress_at, n.claimed_at, n.update_time, n.create_time)
                       &lt; DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 14 DAY)

                UNION ALL

                SELECT 'OFFICE_HOUR_REVIEW' AS actionType,
                       'OFFICE_HOUR_RESERVATION' AS sourceType,
                       r.id AS sourceId,
                       r.reservation_status AS sourceStatus,
                       h.title AS title,
                       'OFFICE_HOUR_RESERVATION_AWAITS_REVIEW' AS reason,
                       CONCAT('/collaboration/office-hours/', h.id,
                              '/reservations/', r.id) AS targetPath,
                       CAST(NULL AS UNSIGNED) AS lastEventId,
                       r.update_time AS updatedAt,
                       1 AS canAct
                  FROM t_collab_office_hour_reservation r
                  JOIN t_collab_office_hour h ON h.id = r.office_hour_id
                 WHERE r.moderation_hidden = 0
                   AND h.moderation_hidden = 0
                   AND h.host_uid = #{uid}
                   AND r.reservation_status = 'PENDING'
            ) actionRows
            <where>
              <if test="actionType != null and actionType != ''">
                actionType = #{actionType}
              </if>
              <if test="cursorTime != null">
                AND (
                    updatedAt &lt; #{cursorTime}
                    OR (
                        updatedAt = #{cursorTime}
                        AND (
                            sourceId &lt; #{cursorId}
                            OR (
                                sourceId = #{cursorId}
                                AND actionType &gt; #{cursorActionType}
                            )
                        )
                    )
                )
              </if>
            </where>
            ORDER BY updatedAt DESC, sourceId DESC, actionType ASC
            LIMIT #{limit}
            </script>
            """)
    List<CollaborationActionQueryRows.ActionRow> listActions(
            @Param("uid") Long uid,
            @Param("actionType") String actionType,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("cursorActionType") String cursorActionType,
            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT actionType, COUNT(*) AS count
            FROM (
                SELECT 'NEED_SUBMIT' AS actionType
                  FROM t_collab_content_need n
                 WHERE n.moderation_hidden = 0
                   AND n.claimed_by_uid = #{uid}
                   AND n.need_status = 'CLAIMED'
                   AND n.reject_reason IS NULL
                UNION ALL
                SELECT 'NEED_REVISE' AS actionType
                  FROM t_collab_content_need n
                 WHERE n.moderation_hidden = 0
                   AND n.claimed_by_uid = #{uid}
                   AND n.need_status = 'CLAIMED'
                   AND n.reject_reason IS NOT NULL
                   AND (SELECT e.event_type
                          FROM t_collab_content_need_event e
                         WHERE e.need_id = n.id
                         ORDER BY e.id DESC
                         LIMIT 1) = 'REJECTED'
                UNION ALL
                SELECT 'NEED_REVIEW' AS actionType
                  FROM t_collab_content_need n
                 WHERE n.moderation_hidden = 0
                   AND n.creator_uid = #{uid}
                   AND n.need_status = 'SUBMITTED'
                   AND (n.claimed_by_uid IS NULL OR n.claimed_by_uid &lt;&gt; #{uid})
                UNION ALL
                SELECT 'NEED_STALLED' AS actionType
                  FROM t_collab_content_need n
                 WHERE n.moderation_hidden = 0
                   AND n.claimed_by_uid = #{uid}
                   AND n.need_status = 'CLAIMED'
                   AND COALESCE(n.last_progress_at, n.claimed_at, n.update_time, n.create_time)
                       &lt; DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 14 DAY)
                UNION ALL
                SELECT 'OFFICE_HOUR_REVIEW' AS actionType
                  FROM t_collab_office_hour_reservation r
                  JOIN t_collab_office_hour h ON h.id = r.office_hour_id
                 WHERE r.moderation_hidden = 0
                   AND h.moderation_hidden = 0
                   AND h.host_uid = #{uid}
                   AND r.reservation_status = 'PENDING'
            ) actionCounts
            GROUP BY actionType
            ORDER BY actionType ASC
            </script>
            """)
    List<CollaborationActionQueryRows.ActionCountRow> countActions(@Param("uid") Long uid);
}
