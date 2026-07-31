package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.offerlab.community.post.infrastructure.persistence.CommunitySpaceRows;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CommunitySpaceMapper {

    @Select("""
            SELECT slug
            FROM t_community_topic
            WHERE id = #{topicId}
              AND topic_status = 1
              AND is_deleted = 0
            LIMIT 1
            """)
    String selectPublicTopicSlug(@Param("topicId") Long topicId);

    @Select("""
            SELECT COUNT(*)
            FROM t_community_topic t
            JOIN t_post_main p
              ON p.id = #{postId}
             AND p.is_deleted = 0
             AND p.post_status = 1
             AND p.visibility = 1
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE t.slug = #{topicSlug}
              AND t.topic_status = 1
              AND t.is_deleted = 0
              AND (
                    EXISTS (
                        SELECT 1
                        FROM t_community_topic_tag tt
                        JOIN t_post_tag_ref ptr
                          ON ptr.tag_id = tt.tag_id
                         AND ptr.post_id = p.id
                        WHERE tt.topic_id = t.id
                    )
                    OR JSON_UNQUOTE(JSON_EXTRACT(
                        IF(JSON_VALID(e.ext_json), e.ext_json, '{}'), '$.contextTopicId'
                    )) = CAST(t.id AS CHAR)
                    OR EXISTS (
                        SELECT 1
                        FROM JSON_TABLE(
                            COALESCE(
                                JSON_EXTRACT(
                                    IF(JSON_VALID(e.ext_json), e.ext_json, '{}'),
                                    '$.topicNames'
                                ),
                                JSON_ARRAY()
                            ),
                            '$[*]' COLUMNS(topic_name VARCHAR(128) PATH '$')
                        ) AS topic_item
                        WHERE LOWER(topic_item.topic_name) = LOWER(t.topic_name)
                           OR LOWER(topic_item.topic_name) = LOWER(t.slug)
                    )
                  )
            """)
    int publicTopicContainsPost(@Param("topicSlug") String topicSlug,
                                @Param("postId") Long postId);

    @Select("""
            SELECT s.id,
                   s.domain,
                   s.title,
                   s.description,
                   s.series_status AS status,
                   s.update_time AS updateTime
            FROM t_collab_series s
            WHERE s.id = #{seriesId}
              AND s.moderation_hidden = 0
              AND s.series_status IN ('OPEN', 'CLOSED')
            LIMIT 1
            """)
    CommunitySpaceRows.CollaborationSeriesRow selectPublicCollaborationSeries(
            @Param("seriesId") Long seriesId);

    @Select("""
            <script>
            SELECT n.id,
                   n.source_type AS sourceType,
                   n.source_ref_id AS sourceRefId,
                   n.content_format AS contentFormat,
                   n.title,
                   n.description,
                   n.need_status AS status,
                   n.update_time AS updateTime
            FROM t_collab_content_need n
            WHERE n.moderation_hidden = 0
              AND n.source_type = #{sourceType}
              AND n.source_ref_id = #{sourceRefId}
              AND n.need_status IN ('OPEN', 'CLAIMED', 'SUBMITTED', 'COMPLETED', 'CLOSED', 'MERGED')
              <if test="cursor != null and cursor &gt; 0">
              AND n.id &lt; #{cursor}
              </if>
            ORDER BY n.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CommunitySpaceRows.NeedRow> listPublicNeeds(@Param("sourceType") String sourceType,
                                                     @Param("sourceRefId") Long sourceRefId,
                                                     @Param("cursor") Long cursor,
                                                     @Param("limit") int limit);

    @Select("""
            <script>
            SELECT n.id,
                   n.source_type AS sourceType,
                   n.source_ref_id AS sourceRefId,
                   n.content_format AS contentFormat,
                   n.title,
                   n.description,
                   n.need_status AS status,
                   n.update_time AS updateTime
            FROM t_collab_content_need n
            JOIN t_collab_series s
              ON s.id = n.resolution_id
             AND s.moderation_hidden = 0
             AND s.series_status IN ('OPEN', 'CLOSED')
            WHERE n.moderation_hidden = 0
              AND n.resolution_type = 'SERIES'
              AND n.resolution_id = #{seriesId}
              AND n.need_status IN ('COMPLETED', 'CLOSED')
              <if test="cursor != null and cursor &gt; 0">
              AND n.id &lt; #{cursor}
              </if>
            ORDER BY n.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CommunitySpaceRows.NeedRow> listPublicNeedsByCollaborationSeries(
            @Param("seriesId") Long seriesId,
            @Param("cursor") Long cursor,
            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT n.id,
                   n.source_type AS sourceType,
                   n.source_ref_id AS sourceRefId,
                   n.content_format AS contentFormat,
                   n.title,
                   n.description,
                   n.need_status AS status,
                   n.update_time AS updateTime
            FROM t_collab_content_need n
            JOIN t_post_main p
              ON p.id = n.source_ref_id
             AND p.is_deleted = 0
             AND p.post_status = 1
             AND p.visibility = 1
            WHERE n.moderation_hidden = 0
              AND n.source_type = 'POST'
              AND n.source_ref_id IN
              <foreach collection="postIds" item="postId" open="(" separator="," close=")">
                #{postId}
              </foreach>
              AND n.need_status IN ('OPEN', 'CLAIMED', 'SUBMITTED', 'COMPLETED', 'CLOSED', 'MERGED')
              <if test="cursor != null and cursor &gt; 0">
              AND n.id &lt; #{cursor}
              </if>
            ORDER BY n.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CommunitySpaceRows.NeedRow> listPublicNeedsByPosts(
            @Param("postIds") Collection<Long> postIds,
            @Param("cursor") Long cursor,
            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT DISTINCT
                   c.id AS contributionId,
                   c.series_id AS seriesId,
                   c.post_id AS postId,
                   c.contributor_uid AS contributorUid,
                   c.contribution_type AS contributionType,
                   p.title AS title,
                   c.create_time AS createTime
            FROM t_collab_series_contribution c
            JOIN t_collab_series cs
              ON cs.id = c.series_id
             AND cs.moderation_hidden = 0
             AND cs.series_status IN ('OPEN', 'CLOSED')
            JOIN t_post_main p
              ON p.id = c.post_id
             AND p.is_deleted = 0
             AND p.post_status = 1
             AND p.visibility = 1
            WHERE c.post_id IN
            <foreach collection="postIds" item="postId" open="(" separator="," close=")">
              #{postId}
            </foreach>
            ORDER BY c.create_time DESC, c.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CommunitySpaceRows.ContributionRow> listPublicContributions(@Param("postIds") Collection<Long> postIds,
                                                                      @Param("limit") int limit);

    @Select("""
            <script>
            SELECT c.id AS contributionId,
                   c.series_id AS seriesId,
                   c.post_id AS postId,
                   c.contributor_uid AS contributorUid,
                   c.contribution_type AS contributionType,
                   p.title AS title,
                   c.create_time AS createTime
            FROM t_collab_series_contribution c
            JOIN t_collab_series cs
              ON cs.id = c.series_id
             AND cs.moderation_hidden = 0
             AND cs.series_status IN ('OPEN', 'CLOSED')
            JOIN t_post_main p
              ON p.id = c.post_id
             AND p.is_deleted = 0
             AND p.post_status = 1
             AND p.visibility = 1
            WHERE c.series_id = #{seriesId}
              <if test="cursor != null and cursor &gt; 0">
              AND c.id &lt; #{cursor}
              </if>
            ORDER BY c.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CommunitySpaceRows.ContributionRow> listPublicSeriesContributions(@Param("seriesId") Long seriesId,
                                                                            @Param("cursor") Long cursor,
                                                                            @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM t_content_series_post sp
            JOIN t_content_series s
              ON s.id = sp.series_id
             AND s.visibility = 1
             AND s.is_deleted = 0
            JOIN t_post_main p
              ON p.id = sp.post_id
             AND p.is_deleted = 0
             AND p.post_status = 1
             AND p.visibility = 1
            WHERE sp.series_id = #{seriesId}
              AND sp.post_id = #{postId}
              AND sp.is_deleted = 0
            """)
    int publicContentSeriesContainsPost(@Param("seriesId") Long seriesId,
                                        @Param("postId") Long postId);

    @Select("""
            SELECT COUNT(*)
            FROM t_collab_series_contribution c
            JOIN t_collab_series s
              ON s.id = c.series_id
             AND s.moderation_hidden = 0
             AND s.series_status IN ('OPEN', 'CLOSED')
            JOIN t_post_main p
              ON p.id = c.post_id
             AND p.is_deleted = 0
             AND p.post_status = 1
             AND p.visibility = 1
            WHERE c.series_id = #{seriesId}
              AND c.post_id = #{postId}
            """)
    int publicCollaborationSeriesContainsPost(@Param("seriesId") Long seriesId,
                                              @Param("postId") Long postId);

    @Select("""
            <script>
            SELECT COUNT(*) AS publicMaintenanceCount
            FROM t_collab_content_maintenance_task task
            WHERE task.task_status IN ('OPEN', 'CLAIMED', 'SUBMITTED')
              AND (
                    task.source_post_id IN
                    <foreach collection="postIds" item="postId" open="(" separator="," close=")">
                      #{postId}
                    </foreach>
                    OR task.delivery_post_id IN
                    <foreach collection="postIds" item="postId" open="(" separator="," close=")">
                      #{postId}
                    </foreach>
                  )
              AND (
                    task.source_post_id IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM t_post_main source_post
                        WHERE source_post.id = task.source_post_id
                          AND source_post.is_deleted = 0
                          AND source_post.post_status = 1
                          AND source_post.visibility = 1
                    )
                  )
              AND (
                    task.delivery_post_id IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM t_post_main delivery_post
                        WHERE delivery_post.id = task.delivery_post_id
                          AND delivery_post.is_deleted = 0
                          AND delivery_post.post_status = 1
                          AND delivery_post.visibility = 1
                    )
                  )
            </script>
            """)
    CommunitySpaceRows.MaintenanceSummaryRow publicMaintenanceForPosts(@Param("postIds") Collection<Long> postIds);
}
