package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.TagPO;
import com.offerlab.community.post.infrastructure.persistence.projection.PostTagView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.util.Collection;
import java.util.List;

@Mapper
public interface TagMapper extends BaseMapper<TagPO> {

    @Select("""
            <script>
            SELECT id, tag_name, tag_type, use_count, is_official, tag_status, recommended, synonyms, merge_target_id, create_time, update_time, is_deleted
            FROM t_tag
            WHERE is_deleted = 0
              AND tag_status = 1
            ORDER BY is_official DESC, use_count DESC, id ASC
            </script>
            """)
    List<TagPO> selectActiveTags();

    @Select("""
            <script>
            SELECT id, tag_name, tag_type, use_count, is_official, create_time, update_time, is_deleted
            FROM t_tag
            WHERE is_deleted = 0
            ORDER BY is_official DESC, use_count DESC, id ASC
            </script>
            """)
    List<TagPO> selectActiveTagsCompat();

    @Select("""
            <script>
            SELECT id, tag_name, tag_type, use_count, is_official, tag_status, recommended, synonyms, merge_target_id, create_time, update_time, is_deleted
            FROM t_tag
            WHERE is_deleted = 0
            <if test="status != null">
              AND tag_status = #{status}
            </if>
            <if test="recommended != null">
              AND recommended = #{recommended}
            </if>
            <if test="keyword != null and keyword != ''">
              AND (tag_name LIKE CONCAT('%', #{keyword}, '%')
                   OR synonyms LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY recommended DESC, is_official DESC, use_count DESC, update_time DESC, id ASC
            LIMIT #{limit}
            </script>
            """)
    List<TagPO> selectGovernanceTags(@Param("status") Integer status,
                                     @Param("recommended") Integer recommended,
                                     @Param("keyword") String keyword,
                                     @Param("limit") int limit);

    @Select("""
            <script>
            SELECT post_id, id, tag_name, tag_type, use_count, is_official, tag_status, recommended, synonyms, merge_target_id
            FROM (
                SELECT r.post_id, t.id, t.tag_name, t.tag_type, t.use_count, t.is_official, t.tag_status, t.recommended, t.synonyms, t.merge_target_id
                FROM t_post_tag_ref r
                JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
                WHERE r.post_id IN
                <foreach collection="postIds" item="postId" open="(" separator="," close=")">
                    #{postId}
                </foreach>
            ) x
            ORDER BY post_id ASC, id ASC
            </script>
            """)
    List<PostTagView> selectTagsByPostIds(@Param("postIds") Collection<Long> postIds);

    @Select("""
            <script>
            SELECT post_id, id, tag_name, tag_type, use_count, is_official
            FROM (
                SELECT r.post_id, t.id, t.tag_name, t.tag_type, t.use_count, t.is_official
                FROM t_post_tag_ref r
                JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
                WHERE r.post_id IN
                <foreach collection="postIds" item="postId" open="(" separator="," close=")">
                    #{postId}
                </foreach>
            ) x
            ORDER BY post_id ASC, id ASC
            </script>
            """)
    List<PostTagView> selectTagsByPostIdsCompat(@Param("postIds") Collection<Long> postIds);

    @Select("""
            <script>
            SELECT id, tag_name, tag_type, use_count, is_official, tag_status, recommended, synonyms, merge_target_id, create_time, update_time, is_deleted
            FROM t_tag
            WHERE is_deleted = 0
              AND tag_status = 1
              AND tag_name IN
              <foreach collection="names" item="name" open="(" separator="," close=")">
                  #{name}
              </foreach>
            </script>
            """)
    List<TagPO> selectByNames(@Param("names") Collection<String> names);

    @Select("""
            <script>
            SELECT id, tag_name, tag_type, use_count, is_official, create_time, update_time, is_deleted
            FROM t_tag
            WHERE is_deleted = 0
              AND tag_name IN
              <foreach collection="names" item="name" open="(" separator="," close=")">
                  #{name}
              </foreach>
            </script>
            """)
    List<TagPO> selectByNamesCompat(@Param("names") Collection<String> names);

    @Select("""
            <script>
            SELECT id, tag_name, tag_type, use_count, is_official, tag_status, recommended, synonyms, merge_target_id, create_time, update_time, is_deleted
            FROM t_tag
            WHERE is_deleted = 0
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                  #{id}
              </foreach>
            </script>
            """)
    List<TagPO> selectByIds(@Param("ids") Collection<Long> ids);

    @Select("""
            <script>
            SELECT id, tag_name, tag_type, use_count, is_official, create_time, update_time, is_deleted
            FROM t_tag
            WHERE is_deleted = 0
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                  #{id}
              </foreach>
            </script>
            """)
    List<TagPO> selectByIdsCompat(@Param("ids") Collection<Long> ids);

    @Insert("""
            INSERT IGNORE INTO t_tag(id, tag_name, tag_type, use_count, is_official, tag_status, recommended, synonyms, merge_target_id, is_deleted)
            VALUES (#{id}, #{name}, #{tagType}, 0, 0, 1, 0, NULL, NULL, 0)
            """)
    int insertIgnoreName(@Param("id") Long id, @Param("name") String name, @Param("tagType") int tagType);

    @Insert("""
            INSERT IGNORE INTO t_tag(id, tag_name, tag_type, use_count, is_official, is_deleted)
            VALUES (#{id}, #{name}, #{tagType}, 0, 0, 0)
            """)
    int insertIgnoreNameCompat(@Param("id") Long id, @Param("name") String name, @Param("tagType") int tagType);

    @Update("""
            UPDATE t_tag
            SET tag_name = #{name},
                tag_type = #{tagType},
                tag_status = #{status},
                recommended = #{recommended},
                synonyms = #{synonyms},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    int updateGovernance(@Param("id") Long id,
                         @Param("name") String name,
                         @Param("tagType") int tagType,
                         @Param("status") int status,
                         @Param("recommended") int recommended,
                         @Param("synonyms") String synonyms);

    @Update("""
            UPDATE t_tag
            SET tag_status = #{status},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    int updateStatus(@Param("id") Long id, @Param("status") int status);

    @Update("""
            UPDATE t_tag
            SET recommended = #{recommended},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    int updateRecommended(@Param("id") Long id, @Param("recommended") int recommended);

    @Update("""
            UPDATE t_tag
            SET synonyms = #{synonyms},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    int updateSynonyms(@Param("id") Long id, @Param("synonyms") String synonyms);

    @Delete("""
            DELETE source
            FROM t_post_tag_ref source
            JOIN t_post_tag_ref target
              ON target.post_id = source.post_id
             AND target.tag_id = #{targetTagId}
            WHERE source.tag_id = #{sourceTagId}
            """)
    int deleteDuplicatePostTagRefsForMerge(@Param("sourceTagId") Long sourceTagId,
                                           @Param("targetTagId") Long targetTagId);

    @Update("""
            UPDATE t_post_tag_ref
            SET tag_id = #{targetTagId}
            WHERE tag_id = #{sourceTagId}
            """)
    int updatePostTagRefsToTarget(@Param("sourceTagId") Long sourceTagId,
                                  @Param("targetTagId") Long targetTagId);

    @Delete("""
            DELETE source
            FROM t_community_topic_tag source
            JOIN t_community_topic_tag target
              ON target.topic_id = source.topic_id
             AND target.tag_id = #{targetTagId}
            WHERE source.tag_id = #{sourceTagId}
            """)
    int deleteDuplicateTopicTagRefsForMerge(@Param("sourceTagId") Long sourceTagId,
                                            @Param("targetTagId") Long targetTagId);

    @Update("""
            UPDATE t_community_topic_tag
            SET tag_id = #{targetTagId}
            WHERE tag_id = #{sourceTagId}
            """)
    int updateTopicTagRefsToTarget(@Param("sourceTagId") Long sourceTagId,
                                   @Param("targetTagId") Long targetTagId);

    @Update("""
            UPDATE t_tag
            SET tag_status = 0,
                merge_target_id = #{targetTagId},
                recommended = 0,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{sourceTagId}
              AND is_deleted = 0
            """)
    int markMerged(@Param("sourceTagId") Long sourceTagId, @Param("targetTagId") Long targetTagId);

    @Select("""
            <script>
            SELECT t.tag_name AS name, COUNT(*) AS count
            FROM t_post_tag_ref r
            JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
            JOIN t_post_main p ON p.id = r.post_id
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.create_time >= #{since}
              <if test="domain != null">
              AND COALESCE(e.domain, 1) = #{domain}
              </if>
            GROUP BY t.id, t.tag_name
            ORDER BY COUNT(*) DESC, t.use_count DESC, t.id ASC
            LIMIT #{limit}
            </script>
            """)
    List<java.util.Map<String, Object>> countTopTags(@Param("since") java.time.LocalDateTime since,
                                                     @Param("limit") int limit,
                                                     @Param("domain") Integer domain);

    @Select("""
            SELECT domain, name, count
            FROM (
              SELECT grouped.*,
                     ROW_NUMBER() OVER (PARTITION BY grouped.domain ORDER BY grouped.count DESC, grouped.useCount DESC, grouped.tagId ASC) AS rn
              FROM (
                SELECT COALESCE(e.domain, 1) AS domain,
                       t.id AS tagId,
                       t.tag_name AS name,
                       t.use_count AS useCount,
                       COUNT(*) AS count
                FROM t_post_tag_ref r
                JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
                JOIN t_post_main p ON p.id = r.post_id
                LEFT JOIN t_post_extension e ON e.post_id = p.id
                WHERE p.is_deleted = 0
                  AND p.post_status = 1
                  AND p.visibility = 1
                  AND p.create_time >= #{since}
                GROUP BY COALESCE(e.domain, 1), t.id, t.tag_name, t.use_count
              ) grouped
            ) ranked
            WHERE ranked.rn <= #{limitPerDomain}
            ORDER BY ranked.domain ASC, ranked.rn ASC
            """)
    List<java.util.Map<String, Object>> countTopTagsByDomain(@Param("since") java.time.LocalDateTime since,
                                                             @Param("limitPerDomain") int limitPerDomain);

    default List<java.util.Map<String, Object>> countTopTags(@Param("since") java.time.LocalDateTime since,
                                                             @Param("limit") int limit) {
        return countTopTags(since, limit, null);
    }

}
