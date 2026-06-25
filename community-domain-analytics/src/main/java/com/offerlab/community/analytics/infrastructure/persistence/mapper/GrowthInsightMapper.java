package com.offerlab.community.analytics.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface GrowthInsightMapper {

    @Select("""
            <script>
            SELECT COALESCE(e.domain, 1) AS domain,
                   COUNT(*) AS postCount,
                   SUM(CASE WHEN COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.featured')), 'false') IN ('true', '1') THEN 1 ELSE 0 END) AS featuredCount,
                   COALESCE(SUM(c.like_count), 0) AS likeCount,
                   COALESCE(SUM(c.favorite_count), 0) AS favoriteCount,
                   COALESCE(SUM(c.comment_count), 0) AS commentCount,
                   COALESCE(SUM(c.view_count), 0) AS viewCount,
                   COUNT(DISTINCT DATE(p.create_time)) AS activeDays,
                   COALESCE(AVG(CHAR_LENGTH(COALESCE(p.content, ''))), 0) AS avgContentLength
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            LEFT JOIN t_post_counter c ON c.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.author_id = #{authorId}
              AND p.create_time &gt;= #{since}
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%E2E%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%SMOKE%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%CODEX%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%TESTDATA%'
            GROUP BY COALESCE(e.domain, 1)
            ORDER BY postCount DESC, domain ASC
            </script>
            """)
    List<Map<String, Object>> selectAuthorDomainStats(@Param("authorId") Long authorId,
                                                      @Param("since") LocalDateTime since);

    @Select("""
            <script>
            SELECT COALESCE(e.domain, 1) AS domain,
                   COUNT(*) AS postCount
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.author_id = #{authorId}
              AND p.create_time &gt;= #{since}
              AND p.create_time &lt; #{until}
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%E2E%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%SMOKE%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%CODEX%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%TESTDATA%'
            GROUP BY COALESCE(e.domain, 1)
            ORDER BY postCount DESC, domain ASC
            </script>
            """)
    List<Map<String, Object>> selectPreviousAuthorDomainStats(@Param("authorId") Long authorId,
                                                              @Param("since") LocalDateTime since,
                                                              @Param("until") LocalDateTime until);

    @Select("""
            <script>
            SELECT p.id AS postId,
                   p.title AS title,
                   COALESCE(e.domain, 1) AS domain,
                   CASE WHEN COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.featured')), 'false') IN ('true', '1') THEN 1 ELSE 0 END AS featured,
                   CAST(COALESCE(c.like_count, 0) + COALESCE(c.favorite_count, 0) + COALESCE(c.comment_count, 0) AS SIGNED) AS interactionCount
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            LEFT JOIN t_post_counter c ON c.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.author_id = #{authorId}
              AND p.create_time &gt;= #{since}
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%E2E%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%SMOKE%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%CODEX%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%TESTDATA%'
            ORDER BY featured DESC, interactionCount DESC, p.create_time DESC, p.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> selectRepresentativePosts(@Param("authorId") Long authorId,
                                                        @Param("since") LocalDateTime since,
                                                        @Param("limit") int limit);
}
