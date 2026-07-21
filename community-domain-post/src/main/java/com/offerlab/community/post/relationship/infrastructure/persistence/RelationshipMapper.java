package com.offerlab.community.post.relationship.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

import static com.offerlab.community.post.relationship.infrastructure.persistence.RelationshipRows.RelationshipCountRow;
import static com.offerlab.community.post.relationship.infrastructure.persistence.RelationshipRows.RelationshipRow;

@Mapper
public interface RelationshipMapper {

    @Select("""
            <script>
            SELECT sourceType,
                   sourceId,
                   relationId,
                   title,
                   summary,
                   targetPath,
                   relationStatus,
                   sourceStatus,
                   lastPublicUpdateAt,
                   relationTime,
                   COALESCE(pref.delivery_mode, 'IMMEDIATE') AS deliveryMode,
                   pref.expires_at AS expiresAt
            FROM (
                SELECT 'TOPIC' AS sourceType,
                       t.id AS sourceId,
                       f.id AS relationId,
                       t.topic_name AS title,
                       t.description AS summary,
                       CONCAT('/topics/', t.slug) AS targetPath,
                       'FOLLOWING' AS relationStatus,
                       CAST(t.topic_status AS CHAR) AS sourceStatus,
                       t.update_time AS lastPublicUpdateAt,
                       f.create_time AS relationTime
                  FROM t_community_topic_follow f
                  JOIN t_community_topic t
                    ON t.id = f.topic_id
                   AND t.is_deleted = 0
                   AND t.topic_status = 1
                 WHERE f.uid = #{uid}
                   AND f.is_deleted = 0

                UNION ALL

                SELECT 'DISCUSSION' AS sourceType,
                       p.id AS sourceId,
                       f.id AS relationId,
                       p.title AS title,
                       LEFT(p.content, 160) AS summary,
                       CONCAT('/post/', p.id) AS targetPath,
                       'FOLLOWING' AS relationStatus,
                       CAST(p.post_status AS CHAR) AS sourceStatus,
                       p.update_time AS lastPublicUpdateAt,
                       f.update_time AS relationTime
                  FROM t_int_discussion_follow f
                  JOIN t_post_main p
                    ON p.id = f.post_id
                   AND p.is_deleted = 0
                   AND p.post_status = 1
                   AND p.visibility = 1
                 WHERE f.uid = #{uid}
                   AND f.follow_status = 1
                   AND f.is_deleted = 0

                UNION ALL

                SELECT 'NEED' AS sourceType,
                       n.id AS sourceId,
                       f.id AS relationId,
                       n.title AS title,
                       n.description AS summary,
                       CONCAT('/collaboration/needs/', n.id) AS targetPath,
                       'FOLLOWING' AS relationStatus,
                       n.need_status AS sourceStatus,
                       n.update_time AS lastPublicUpdateAt,
                       f.update_time AS relationTime
                  FROM t_collab_content_need_follow f
                  JOIN t_collab_content_need n
                    ON n.id = f.need_id
                   AND n.moderation_hidden = 0
                 WHERE f.uid = #{uid}
                   AND f.active = 1

                UNION ALL

                SELECT 'SERIES' AS sourceType,
                       s.id AS sourceId,
                       m.id AS relationId,
                       s.title AS title,
                       s.description AS summary,
                       CONCAT('/collaboration/series/', s.id) AS targetPath,
                       'MEMBER' AS relationStatus,
                       s.series_status AS sourceStatus,
                       s.update_time AS lastPublicUpdateAt,
                       m.update_time AS relationTime
                  FROM t_collab_series_member m
                  JOIN t_collab_series s
                    ON s.id = m.series_id
                   AND s.moderation_hidden = 0
                 WHERE m.uid = #{uid}
                   AND m.member_status = 'ACTIVE'
            ) relationshipRows
            LEFT JOIN t_user_subscription_preference pref
                   ON pref.uid = #{uid}
                  AND pref.source_type = relationshipRows.sourceType
                  AND pref.source_id = relationshipRows.sourceId
                  AND pref.is_deleted = 0
                  AND (pref.expires_at IS NULL OR pref.expires_at > CURRENT_TIMESTAMP(3))
            <where>
              <if test="sourceType != null and sourceType != ''">
                sourceType = #{sourceType}
              </if>
              <if test="mode == 'ACTIVE'">
                AND COALESCE(pref.delivery_mode, 'IMMEDIATE') != 'MUTED'
              </if>
              <if test="mode == 'MUTED'">
                AND COALESCE(pref.delivery_mode, 'IMMEDIATE') = 'MUTED'
              </if>
              <if test="cursorTime != null">
                AND (
                    relationTime &lt; #{cursorTime}
                    OR (
                        relationTime = #{cursorTime}
                        AND (
                            relationId &lt; #{cursorId}
                            OR (
                                relationId = #{cursorId}
                                AND sourceType &gt; #{cursorSourceType}
                            )
                        )
                    )
                )
              </if>
            </where>
            ORDER BY relationTime DESC, relationId DESC, sourceType ASC
            LIMIT #{limit}
            </script>
            """)
    List<RelationshipRow> listPostRelationships(@Param("uid") Long uid,
                                                @Param("sourceType") String sourceType,
                                                @Param("cursorTime") LocalDateTime cursorTime,
                                                @Param("cursorId") Long cursorId,
                                                @Param("cursorSourceType") String cursorSourceType,
                                                @Param("mode") String mode,
                                                @Param("limit") int limit);

    default List<RelationshipRow> listPostRelationships(Long uid,
                                                        String sourceType,
                                                        LocalDateTime cursorTime,
                                                        Long cursorId,
                                                        String cursorSourceType,
                                                        int limit) {
        return listPostRelationships(uid, sourceType, cursorTime, cursorId, cursorSourceType, "ALL", limit);
    }

    @Select("""
            <script>
            SELECT relationshipCounts.sourceType AS sourceType,
                   COALESCE(pref.delivery_mode, 'IMMEDIATE') AS deliveryMode,
                   COUNT(*) AS count
            FROM (
                SELECT 'TOPIC' AS sourceType,
                       t.id AS sourceId
                  FROM t_community_topic_follow f
                  JOIN t_community_topic t
                    ON t.id = f.topic_id
                   AND t.is_deleted = 0
                   AND t.topic_status = 1
                 WHERE f.uid = #{uid}
                   AND f.is_deleted = 0

                UNION ALL

                SELECT 'DISCUSSION' AS sourceType,
                       p.id AS sourceId
                  FROM t_int_discussion_follow f
                  JOIN t_post_main p
                    ON p.id = f.post_id
                   AND p.is_deleted = 0
                   AND p.post_status = 1
                   AND p.visibility = 1
                 WHERE f.uid = #{uid}
                   AND f.follow_status = 1
                   AND f.is_deleted = 0

                UNION ALL

                SELECT 'NEED' AS sourceType,
                       n.id AS sourceId
                  FROM t_collab_content_need_follow f
                  JOIN t_collab_content_need n
                    ON n.id = f.need_id
                   AND n.moderation_hidden = 0
                 WHERE f.uid = #{uid}
                   AND f.active = 1

                UNION ALL

                SELECT 'SERIES' AS sourceType,
                       s.id AS sourceId
                  FROM t_collab_series_member m
                  JOIN t_collab_series s
                    ON s.id = m.series_id
                   AND s.moderation_hidden = 0
                 WHERE m.uid = #{uid}
                   AND m.member_status = 'ACTIVE'
            ) relationshipCounts
            LEFT JOIN t_user_subscription_preference pref
                   ON pref.uid = #{uid}
                  AND pref.source_type = relationshipCounts.sourceType
                  AND pref.source_id = relationshipCounts.sourceId
                  AND pref.is_deleted = 0
                  AND (pref.expires_at IS NULL OR pref.expires_at > CURRENT_TIMESTAMP(3))
            GROUP BY relationshipCounts.sourceType, COALESCE(pref.delivery_mode, 'IMMEDIATE')
            ORDER BY relationshipCounts.sourceType ASC, deliveryMode ASC
            </script>
            """)
    List<RelationshipCountRow> countPostRelationships(@Param("uid") Long uid);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM (
                SELECT 'TOPIC' AS sourceType,
                       t.id AS sourceId
                  FROM t_community_topic_follow f
                  JOIN t_community_topic t
                    ON t.id = f.topic_id
                   AND t.is_deleted = 0
                   AND t.topic_status = 1
                 WHERE f.uid = #{uid}
                   AND f.is_deleted = 0

                UNION ALL

                SELECT 'DISCUSSION' AS sourceType,
                       p.id AS sourceId
                  FROM t_int_discussion_follow f
                  JOIN t_post_main p
                    ON p.id = f.post_id
                   AND p.is_deleted = 0
                   AND p.post_status = 1
                   AND p.visibility = 1
                 WHERE f.uid = #{uid}
                   AND f.follow_status = 1
                   AND f.is_deleted = 0

                UNION ALL

                SELECT 'NEED' AS sourceType,
                       n.id AS sourceId
                  FROM t_collab_content_need_follow f
                  JOIN t_collab_content_need n
                    ON n.id = f.need_id
                   AND n.moderation_hidden = 0
                 WHERE f.uid = #{uid}
                   AND f.active = 1

                UNION ALL

                SELECT 'SERIES' AS sourceType,
                       s.id AS sourceId
                  FROM t_collab_series_member m
                  JOIN t_collab_series s
                    ON s.id = m.series_id
                   AND s.moderation_hidden = 0
                 WHERE m.uid = #{uid}
                   AND m.member_status = 'ACTIVE'
            ) relationshipRows
            WHERE sourceType = #{sourceType}
              AND sourceId = #{sourceId}
            </script>
            """)
    int existsPostRelationship(@Param("uid") Long uid,
                               @Param("sourceType") String sourceType,
                               @Param("sourceId") Long sourceId);
}
