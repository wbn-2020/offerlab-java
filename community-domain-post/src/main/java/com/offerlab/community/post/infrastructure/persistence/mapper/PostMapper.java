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
              AND (#{cursorTime} IS NULL OR p.create_time < #{cursorTime})
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
    List<PostPO> selectHotPosts(@Param("cursorTime") LocalDateTime cursorTime, @Param("limit") int limit);

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
