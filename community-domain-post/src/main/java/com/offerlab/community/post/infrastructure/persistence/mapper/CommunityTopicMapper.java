package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.CommunityTopicPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CommunityTopicMapper extends BaseMapper<CommunityTopicPO> {

    @Select("""
            <script>
            SELECT *
            FROM t_community_topic
            WHERE is_deleted = 0
              <if test="onlineOnly">
              AND topic_status = 1
              </if>
              <if test="featured != null">
              AND featured = CASE WHEN #{featured} THEN 1 ELSE 0 END
              </if>
              <if test="status != null">
              AND topic_status = #{status}
              </if>
              <if test="keyword != null and keyword != ''">
              AND (
                    topic_name LIKE CONCAT('%', #{keyword}, '%')
                    OR slug LIKE CONCAT('%', #{keyword}, '%')
                    OR description LIKE CONCAT('%', #{keyword}, '%')
                  )
              </if>
            ORDER BY sort_order DESC, featured DESC, update_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CommunityTopicPO> selectTopics(@Param("onlineOnly") boolean onlineOnly,
                                        @Param("featured") Boolean featured,
                                        @Param("status") Integer status,
                                        @Param("keyword") String keyword,
                                        @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM t_community_topic
            WHERE is_deleted = 0
              AND slug = #{slug}
            LIMIT 1
            """)
    CommunityTopicPO selectBySlug(@Param("slug") String slug);

    @Select("""
            SELECT *
            FROM t_community_topic
            WHERE is_deleted = 0
              AND (
                    slug = #{slug}
                    OR LOWER(slug) = LOWER(#{slug})
                    OR topic_name = #{name}
                    OR LOWER(topic_name) = LOWER(#{name})
                  )
            ORDER BY
              CASE
                WHEN slug = #{slug} THEN 0
                WHEN LOWER(slug) = LOWER(#{slug}) THEN 1
                WHEN topic_name = #{name} THEN 2
                ELSE 3
              END,
              topic_status DESC,
              sort_order DESC,
              update_time DESC
            LIMIT 1
            """)
    CommunityTopicPO selectBySlugOrName(@Param("slug") String slug, @Param("name") String name);

    @Select("""
            <script>
            SELECT DISTINCT t.*
            FROM t_community_topic t
            JOIN t_community_topic_tag tt ON tt.topic_id = t.id
            WHERE t.is_deleted = 0
              AND t.topic_status = 1
              AND tt.tag_id IN
              <foreach collection="tagIds" item="tagId" open="(" separator="," close=")">
                #{tagId}
              </foreach>
            ORDER BY t.featured DESC, t.sort_order DESC, t.update_time DESC, t.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CommunityTopicPO> selectOnlineTopicsByTagIds(@Param("tagIds") Collection<Long> tagIds,
                                                      @Param("limit") int limit);

    @Select("""
            SELECT COUNT(DISTINCT p.id)
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND (
                    EXISTS (
                        SELECT 1
                        FROM t_community_topic_tag tt
                        JOIN t_post_tag_ref ptr ON ptr.tag_id = tt.tag_id AND ptr.post_id = p.id
                        WHERE tt.topic_id = #{topicId}
                    )
                    OR p.title LIKE CONCAT('%', #{keyword}, '%')
                    OR p.content LIKE CONCAT('%', #{keyword}, '%')
                    OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.scenario')) LIKE CONCAT('%', #{keyword}, '%')
                    OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.techStacks')) LIKE CONCAT('%', #{keyword}, '%')
                  )
            """)
    long countPublicPosts(@Param("topicId") Long topicId, @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT t.id AS topicId,
                   (
                       SELECT COUNT(DISTINCT p.id)
                       FROM t_post_main p
                       LEFT JOIN t_post_extension e ON e.post_id = p.id
                       WHERE p.is_deleted = 0
                         AND p.post_status = 1
                         AND p.visibility = 1
                         AND (
                               EXISTS (
                                   SELECT 1
                                   FROM t_community_topic_tag tt
                                   JOIN t_post_tag_ref ptr ON ptr.tag_id = tt.tag_id AND ptr.post_id = p.id
                                   WHERE tt.topic_id = t.id
                               )
                               OR p.title LIKE CONCAT('%', COALESCE(t.topic_name, t.slug), '%')
                               OR p.content LIKE CONCAT('%', COALESCE(t.topic_name, t.slug), '%')
                               OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.scenario')) LIKE CONCAT('%', COALESCE(t.topic_name, t.slug), '%')
                               OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.techStacks')) LIKE CONCAT('%', COALESCE(t.topic_name, t.slug), '%')
                             )
                   ) AS postCount
            FROM t_community_topic t
            WHERE t.is_deleted = 0
              AND t.id IN
              <foreach collection="topicIds" item="topicId" open="(" separator="," close=")">
                #{topicId}
              </foreach>
            </script>
            """)
    List<java.util.Map<String, Object>> countPublicPostsByTopicIds(@Param("topicIds") Collection<Long> topicIds);

    @Select("""
            <script>
            SELECT topic_id AS topicId, COUNT(*) AS followerCount
            FROM t_community_topic_follow
            WHERE is_deleted = 0
              AND topic_id IN
              <foreach collection="topicIds" item="topicId" open="(" separator="," close=")">
                #{topicId}
              </foreach>
            GROUP BY topic_id
            </script>
            """)
    List<java.util.Map<String, Object>> countFollowersByTopicIds(@Param("topicIds") Collection<Long> topicIds);

    @Update("""
            UPDATE t_community_topic
            SET topic_status = #{status}, updated_by = #{operatorUid}
            WHERE id = #{topicId}
              AND is_deleted = 0
            """)
    int updateStatus(@Param("topicId") Long topicId,
                     @Param("status") Integer status,
                     @Param("operatorUid") Long operatorUid);
}
