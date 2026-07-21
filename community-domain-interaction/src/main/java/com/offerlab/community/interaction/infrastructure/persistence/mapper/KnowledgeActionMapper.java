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
            """)
    List<ActionRow> listSuggestionActions(@Param("uid") Long uid, @Param("limit") int limit);

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
            """)
    List<ActionRow> listStaleSuggestionActions(@Param("uid") Long uid, @Param("limit") int limit);

    @Select("""
            SELECT post_id AS id, post_id AS postId, 'FRESHNESS_CONFIRMATION' AS actionType,
                   freshness_status AS actionStatus, update_time AS updatedAt
            FROM t_int_post_trust_state
            WHERE freshness_status = 'AWAITING_AUTHOR_CONFIRMATION'
            ORDER BY update_time DESC, post_id DESC
            """)
    List<ActionRow> listFreshnessActions(@Param("limit") int limit);

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
            """)
    List<ActionRow> listOutcomeRevisitActions(@Param("uid") Long uid, @Param("limit") int limit);

    @Data
    class ActionRow {
        private Long id;
        private Long postId;
        private String actionType;
        private String actionStatus;
        private LocalDateTime updatedAt;
    }
}
