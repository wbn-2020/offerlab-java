package com.offerlab.community.interaction.infrastructure.persistence.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface KnowledgeActionMapper {

    @Select("""
            SELECT s.id, s.post_id AS postId, 'SUGGESTION_RESPONSE' AS actionType,
                   'PENDING' AS actionStatus, s.update_time AS updatedAt
            FROM t_int_content_suggestion s
            JOIN t_post_main p ON p.id = s.post_id
            WHERE s.post_author_id = #{uid}
              AND s.resolution = 'PENDING'
              AND (s.base_version IS NULL OR s.base_version >= p.version)
            ORDER BY s.update_time DESC, s.id DESC
            LIMIT #{limit}
            """)
    List<ActionRow> listSuggestionActions(@Param("uid") Long uid, @Param("limit") int limit);

    @Select("""
            SELECT s.id, s.post_id AS postId, 'SUGGESTION_RESPONSE' AS actionType,
                   'PENDING' AS actionStatus, s.update_time AS updatedAt
            FROM t_int_content_suggestion s
            JOIN t_post_main p
              ON p.id = s.post_id
             AND p.author_id = #{uid}
             AND p.is_deleted = 0
            WHERE s.post_author_id = #{uid}
              AND s.resolution = 'PENDING'
              AND (s.base_version IS NULL OR s.base_version >= p.version)
              AND (
                    #{cursorTime} IS NULL
                    OR s.update_time < #{cursorTime}
                    OR (s.update_time = #{cursorTime} AND s.id < #{cursorId})
                  )
            ORDER BY s.update_time DESC, s.id DESC
            LIMIT #{limit}
            """)
    List<ActionRow> listSuggestionActionsAfter(@Param("uid") Long uid,
                                               @Param("cursorTime") LocalDateTime cursorTime,
                                               @Param("cursorId") Long cursorId,
                                               @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM t_int_content_suggestion s
            JOIN t_post_main p
              ON p.id = s.post_id
             AND p.author_id = #{uid}
             AND p.is_deleted = 0
            WHERE s.post_author_id = #{uid}
              AND s.resolution = 'PENDING'
              AND (s.base_version IS NULL OR s.base_version >= p.version)
            """)
    long countSuggestionActions(@Param("uid") Long uid);

    @Select("""
            SELECT s.id, s.post_id AS postId, 'STALE_SUGGESTION' AS actionType,
                   'PENDING' AS actionStatus, s.update_time AS updatedAt
            FROM t_int_content_suggestion s
            JOIN t_post_main p ON p.id = s.post_id
            WHERE s.post_author_id = #{uid}
              AND s.resolution = 'PENDING'
              AND s.base_version IS NOT NULL
              AND s.base_version < p.version
            ORDER BY s.update_time DESC, s.id DESC
            LIMIT #{limit}
            """)
    List<ActionRow> listStaleSuggestionActions(@Param("uid") Long uid, @Param("limit") int limit);

    @Select("""
            SELECT s.id, s.post_id AS postId, 'STALE_SUGGESTION' AS actionType,
                   'PENDING' AS actionStatus, s.update_time AS updatedAt
            FROM t_int_content_suggestion s
            JOIN t_post_main p
              ON p.id = s.post_id
             AND p.author_id = #{uid}
             AND p.is_deleted = 0
            WHERE s.post_author_id = #{uid}
              AND s.resolution = 'PENDING'
              AND s.base_version IS NOT NULL
              AND s.base_version < p.version
              AND (
                    #{cursorTime} IS NULL
                    OR s.update_time < #{cursorTime}
                    OR (s.update_time = #{cursorTime} AND s.id < #{cursorId})
                  )
            ORDER BY s.update_time DESC, s.id DESC
            LIMIT #{limit}
            """)
    List<ActionRow> listStaleSuggestionActionsAfter(@Param("uid") Long uid,
                                                    @Param("cursorTime") LocalDateTime cursorTime,
                                                    @Param("cursorId") Long cursorId,
                                                    @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM t_int_content_suggestion s
            JOIN t_post_main p
              ON p.id = s.post_id
             AND p.author_id = #{uid}
             AND p.is_deleted = 0
            WHERE s.post_author_id = #{uid}
              AND s.resolution = 'PENDING'
              AND s.base_version IS NOT NULL
              AND s.base_version < p.version
            """)
    long countStaleSuggestionActions(@Param("uid") Long uid);

    @Select("""
            SELECT s.post_id AS id, s.post_id AS postId, 'FRESHNESS_CONFIRMATION' AS actionType,
                   s.freshness_status AS actionStatus, s.update_time AS updatedAt
            FROM t_int_post_trust_state s
            JOIN t_post_main p ON p.id = s.post_id
            WHERE p.author_id = #{uid}
              AND p.is_deleted = 0
              AND s.freshness_status = 'AWAITING_AUTHOR_CONFIRMATION'
            ORDER BY s.update_time DESC, s.post_id DESC
            LIMIT #{limit}
            """)
    List<ActionRow> listFreshnessActions(@Param("uid") Long uid, @Param("limit") int limit);

    @Select("""
            SELECT s.post_id AS id, s.post_id AS postId, 'FRESHNESS_CONFIRMATION' AS actionType,
                   s.freshness_status AS actionStatus, s.update_time AS updatedAt
            FROM t_int_post_trust_state s
            JOIN t_post_main p
              ON p.id = s.post_id
             AND p.author_id = #{uid}
             AND p.is_deleted = 0
            WHERE s.freshness_status = 'AWAITING_AUTHOR_CONFIRMATION'
              AND (
                    #{cursorTime} IS NULL
                    OR s.update_time < #{cursorTime}
                    OR (s.update_time = #{cursorTime} AND s.post_id < #{cursorId})
                  )
            ORDER BY s.update_time DESC, s.post_id DESC
            LIMIT #{limit}
            """)
    List<ActionRow> listFreshnessActionsAfter(@Param("uid") Long uid,
                                              @Param("cursorTime") LocalDateTime cursorTime,
                                              @Param("cursorId") Long cursorId,
                                              @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM t_int_post_trust_state s
            JOIN t_post_main p
              ON p.id = s.post_id
             AND p.author_id = #{uid}
             AND p.is_deleted = 0
            WHERE s.freshness_status = 'AWAITING_AUTHOR_CONFIRMATION'
            """)
    long countFreshnessActions(@Param("uid") Long uid);

    @Select("""
            SELECT r.id, o.post_id AS postId, 'OUTCOME_REVISIT' AS actionType,
                   r.revisit_status AS actionStatus, r.update_time AS updatedAt
            FROM t_int_user_revisit_item r
            JOIN t_int_post_outcome o
              ON o.id = CAST(r.source_id AS UNSIGNED)
             AND r.source_type = 'POST_OUTCOME'
            WHERE r.uid = #{uid}
              AND r.revisit_status IN ('OPEN', 'SNOOZED')
              AND o.uid = #{uid}
              AND o.outcome_status = 'ACTIVE'
              AND o.is_deleted = 0
              AND COALESCE(r.snoozed_until, r.due_at) <= CURRENT_TIMESTAMP(3)
            ORDER BY r.update_time DESC, r.id DESC
            LIMIT #{limit}
            """)
    List<ActionRow> listOutcomeRevisitActions(@Param("uid") Long uid, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT r.id, o.post_id AS postId, 'OUTCOME_REVISIT' AS actionType,
                   r.revisit_status AS actionStatus, r.update_time AS updatedAt
            FROM t_int_user_revisit_item r
            JOIN t_int_post_outcome o
              ON o.id = CAST(r.source_id AS UNSIGNED)
             AND r.source_type = 'POST_OUTCOME'
            WHERE r.uid = #{uid}
              AND r.revisit_status IN ('OPEN', 'SNOOZED')
              <if test="status != null and status != ''">
              AND r.revisit_status = #{status}
              </if>
              AND o.uid = #{uid}
              AND o.outcome_status = 'ACTIVE'
              AND o.is_deleted = 0
              AND COALESCE(r.snoozed_until, r.due_at) &lt;= CURRENT_TIMESTAMP(3)
              AND (
                    #{cursorTime} IS NULL
                    OR r.update_time &lt; #{cursorTime}
                    OR (r.update_time = #{cursorTime} AND r.id &lt; #{cursorId})
                  )
            ORDER BY r.update_time DESC, r.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ActionRow> listOutcomeRevisitActionsAfter(@Param("uid") Long uid,
                                                   @Param("status") String status,
                                                   @Param("cursorTime") LocalDateTime cursorTime,
                                                   @Param("cursorId") Long cursorId,
                                                   @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM t_int_user_revisit_item r
            JOIN t_int_post_outcome o
              ON o.id = CAST(r.source_id AS UNSIGNED)
             AND r.source_type = 'POST_OUTCOME'
            WHERE r.uid = #{uid}
              AND r.revisit_status IN ('OPEN', 'SNOOZED')
              <if test="status != null and status != ''">
              AND r.revisit_status = #{status}
              </if>
              AND o.uid = #{uid}
              AND o.outcome_status = 'ACTIVE'
              AND o.is_deleted = 0
              AND COALESCE(r.snoozed_until, r.due_at) &lt;= CURRENT_TIMESTAMP(3)
            </script>
            """)
    long countOutcomeRevisitActions(@Param("uid") Long uid,
                                    @Param("status") String status);

    @Data
    class ActionRow {
        private Long id;
        private Long postId;
        private String actionType;
        private String actionStatus;
        private LocalDateTime updatedAt;
    }
}
