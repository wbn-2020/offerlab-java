package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface PostMapper extends BaseMapper<PostPO> {

    @Select("""
            SELECT DATE(create_time) AS label, COUNT(*) AS count
            FROM t_post_main
            WHERE is_deleted = 0
              AND post_status = 1
              AND visibility = 1
              AND create_time >= #{since}
            GROUP BY DATE(create_time)
            ORDER BY DATE(create_time) ASC
            """)
    List<Map<String, Object>> countPublishedByDate(@Param("since") LocalDateTime since);

    @Select("""
            SELECT x.company AS name, COUNT(*) AS count
            FROM (
                SELECT NULLIF(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.company')), '') AS company
                FROM t_post_main p
                JOIN t_post_extension e ON e.post_id = p.id
                WHERE p.is_deleted = 0
                  AND p.post_status = 1
                  AND p.visibility = 1
                  AND p.create_time >= #{since}
            ) x
            WHERE x.company IS NOT NULL
            GROUP BY x.company
            ORDER BY COUNT(*) DESC, x.company ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> countCompanies(@Param("since") LocalDateTime since, @Param("limit") int limit);

    @Select("""
            SELECT x.company AS name, COUNT(*) AS count
            FROM (
                SELECT NULLIF(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.company')), '') AS company
                FROM t_post_main p
                JOIN t_post_extension e ON e.post_id = p.id
                WHERE p.is_deleted = 0
                  AND p.post_status = 1
                  AND p.visibility = 1
                  AND p.post_type = 1
            ) x
            WHERE x.company IS NOT NULL
            GROUP BY x.company
            ORDER BY COUNT(*) DESC, x.company ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> countInterviewCompaniesForAliasCandidates(@Param("limit") int limit);

    @Select("""
            SELECT x.position AS name, COUNT(*) AS count
            FROM (
                SELECT NULLIF(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.position')), '') AS position
                FROM t_post_main p
                JOIN t_post_extension e ON e.post_id = p.id
                WHERE p.is_deleted = 0
                  AND p.post_status = 1
                  AND p.visibility = 1
                  AND p.create_time >= #{since}
            ) x
            WHERE x.position IS NOT NULL
            GROUP BY x.position
            ORDER BY COUNT(*) DESC, x.position ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> countPositions(@Param("since") LocalDateTime since, @Param("limit") int limit);

    @Select("""
            SELECT x.result AS name, COUNT(*) AS count
            FROM (
                SELECT NULLIF(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.interviewResult')), '') AS result
                FROM t_post_main p
                JOIN t_post_extension e ON e.post_id = p.id
                WHERE p.is_deleted = 0
                  AND p.post_status = 1
                  AND p.visibility = 1
                  AND p.create_time >= #{since}
            ) x
            WHERE x.result IS NOT NULL
            GROUP BY x.result
            ORDER BY COUNT(*) DESC, x.result ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> countInterviewResults(@Param("since") LocalDateTime since, @Param("limit") int limit);

    @Select("""
            SELECT x.result AS name, COUNT(*) AS count
            FROM (
                SELECT COALESCE(e.interview_result, 0) AS result
                FROM t_post_main p
                JOIN t_post_extension e ON e.post_id = p.id
                WHERE p.is_deleted = 0
                  AND p.post_status = 1
                  AND p.visibility = 1
                  AND p.post_type = 1
                  AND e.company = #{company}
                  AND (#{since} IS NULL OR p.create_time >= #{since})
            ) x
            GROUP BY x.result
            ORDER BY COUNT(*) DESC, x.result ASC
            """)
    List<Map<String, Object>> countInterviewResultsByCompany(@Param("company") String company,
                                                             @Param("since") LocalDateTime since);

    @Select("""
            SELECT COUNT(*)
            FROM t_post_main
            WHERE is_deleted = 0
              AND post_status = 1
              AND visibility = 1
              AND create_time >= #{since}
            """)
    long countPublishedSince(@Param("since") LocalDateTime since);

    @Select("""
            SELECT p.*
            FROM t_post_main p
            LEFT JOIN t_post_counter c ON c.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND (
                #{cursorScore} IS NULL
                OR (
                  (
                    COALESCE(c.like_count, 0) * 3
                    + COALESCE(c.favorite_count, 0) * 4
                    + COALESCE(c.comment_count, 0) * 5
                    + COALESCE(c.view_count, 0) * 0.2
                    + GREATEST(0, 72 - TIMESTAMPDIFF(HOUR, p.create_time, NOW()))
                  ) < #{cursorScore}
                )
                OR (
                  (
                    COALESCE(c.like_count, 0) * 3
                    + COALESCE(c.favorite_count, 0) * 4
                    + COALESCE(c.comment_count, 0) * 5
                    + COALESCE(c.view_count, 0) * 0.2
                    + GREATEST(0, 72 - TIMESTAMPDIFF(HOUR, p.create_time, NOW()))
                  ) = #{cursorScore}
                  AND p.create_time < #{cursorTime}
                )
                OR (
                  (
                    COALESCE(c.like_count, 0) * 3
                    + COALESCE(c.favorite_count, 0) * 4
                    + COALESCE(c.comment_count, 0) * 5
                    + COALESCE(c.view_count, 0) * 0.2
                    + GREATEST(0, 72 - TIMESTAMPDIFF(HOUR, p.create_time, NOW()))
                  ) = #{cursorScore}
                  AND p.create_time = #{cursorTime}
                  AND p.id < #{cursorId}
                )
              )
            ORDER BY (
                COALESCE(c.like_count, 0) * 3
                + COALESCE(c.favorite_count, 0) * 4
                + COALESCE(c.comment_count, 0) * 5
                + COALESCE(c.view_count, 0) * 0.2
                + GREATEST(0, 72 - TIMESTAMPDIFF(HOUR, p.create_time, NOW()))
            ) DESC,
            p.create_time DESC,
            p.id DESC
            LIMIT #{limit}
            """)
    List<PostPO> selectHotPosts(@Param("cursorScore") Double cursorScore,
                                @Param("cursorTime") LocalDateTime cursorTime,
                                @Param("cursorId") Long cursorId,
                                @Param("limit") int limit);

    @Select("""
            <script>
            SELECT p.*
            FROM t_post_main p
            <if test="tagId != null">
            JOIN t_post_tag_ref r ON r.post_id = p.id AND r.tag_id = #{tagId}
            </if>
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              <if test="authorId != null">
              AND p.author_id = #{authorId}
              </if>
              <if test="postType != null">
              AND p.post_type = #{postType}
              </if>
              <if test="cursorTime != null">
              AND (p.create_time &lt; #{cursorTime}
                   OR (p.create_time = #{cursorTime} AND p.id &lt; #{cursorId}))
              </if>
            ORDER BY p.create_time DESC, p.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<PostPO> selectPublicPosts(@Param("authorId") Long authorId,
                                   @Param("tagId") Long tagId,
                                   @Param("postType") Integer postType,
                                   @Param("cursorTime") LocalDateTime cursorTime,
                                   @Param("cursorId") Long cursorId,
                                   @Param("limit") int limit);

    @Select("""
            SELECT p.*
            FROM t_post_main p
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.id > #{lastId}
            ORDER BY p.id ASC
            LIMIT #{limit}
            """)
    List<PostPO> selectPublicPostsForIndexAfterId(@Param("lastId") Long lastId,
                                                  @Param("limit") int limit);

    @Select("""
            <script>
            SELECT p.*
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              <if test="keyword != null and keyword != ''">
              AND (
                    p.title LIKE CONCAT('%', #{keyword}, '%')
                    OR p.content LIKE CONCAT('%', #{keyword}, '%')
                    OR e.company LIKE CONCAT('%', #{keyword}, '%')
                    OR e.position LIKE CONCAT('%', #{keyword}, '%')
                    OR EXISTS (
                        SELECT 1
                        FROM t_post_tag_ref r
                        JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
                        WHERE r.post_id = p.id
                          AND t.tag_name LIKE CONCAT('%', #{keyword}, '%')
                    )
                  )
              </if>
              <if test="company != null and company != ''">
              AND e.company LIKE CONCAT('%', #{company}, '%')
              </if>
              <if test="position != null and position != ''">
              AND e.position = #{position}
              </if>
              <if test="type != null">
              AND p.post_type = #{type}
              </if>
              <if test="cursorTime != null">
              AND p.create_time &lt; #{cursorTime}
              </if>
            ORDER BY p.create_time DESC, p.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<PostPO> searchPublicPostsFallback(@Param("keyword") String keyword,
                                           @Param("company") String company,
                                           @Param("position") String position,
                                           @Param("type") Integer type,
                                           @Param("cursorTime") LocalDateTime cursorTime,
                                           @Param("limit") int limit);

    @Select("""
            <script>
            SELECT p.*
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND (
                    p.title LIKE CONCAT('%', #{prefix}, '%')
                    OR e.company LIKE CONCAT('%', #{prefix}, '%')
                    OR e.position LIKE CONCAT('%', #{prefix}, '%')
                    OR EXISTS (
                        SELECT 1
                        FROM t_post_tag_ref r
                        JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
                        WHERE r.post_id = p.id
                          AND t.tag_name LIKE CONCAT('%', #{prefix}, '%')
                    )
                  )
            ORDER BY p.create_time DESC, p.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<PostPO> suggestPublicPostsFallback(@Param("prefix") String prefix, @Param("limit") int limit);

    @Select("""
            SELECT p.*
            FROM t_post_main p
            JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.post_type = 1
              AND e.company = #{company}
            ORDER BY p.create_time DESC, p.id DESC
            LIMIT #{limit}
            """)
    List<PostPO> selectRecentInterviewPostsByCompany(@Param("company") String company, @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) AS sampleCount,
                   MAX(p.update_time) AS updatedAt
            FROM t_post_main p
            JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.post_type = 1
              AND e.company = #{company}
            """)
    Map<String, Object> summarizeInterviewPostsByCompany(@Param("company") String company);

    @Select("""
            SELECT id
            FROM t_post_main
            WHERE is_deleted = 0
              AND post_status = 1
              AND visibility = 1
              AND post_type = 1
            ORDER BY create_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<Long> selectRecentPublicInterviewPostIds(@Param("limit") int limit);
}
