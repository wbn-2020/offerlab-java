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
            SELECT p.id AS postId,
                   p.author_id AS authorId,
                   e.domain AS domain
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.id = #{postId}
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
            LIMIT 1
            """)
    Map<String, Object> selectPublicPostForEffectiveRead(@Param("postId") Long postId);

    @Select("""
            WITH public_posts AS (
                SELECT p.id AS postId,
                       p.post_type AS postType
                FROM t_post_main p
                LEFT JOIN t_post_extension e ON e.post_id = p.id
                WHERE p.is_deleted = 0
                  AND p.post_status = 1
                  AND p.visibility = 1
                  AND p.author_id = #{authorId}
                  AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false') NOT IN ('true', '1')
                  AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%E2E%'
                  AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%SMOKE%'
                  AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%CODEX%'
                  AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%TESTDATA%'
            )
            SELECT
                (
                    SELECT COUNT(*)
                    FROM t_int_content_suggestion s
                    JOIN public_posts p ON p.postId = s.post_id
                    WHERE s.decision IS NULL
                ) AS pendingSuggestions,
                (
                    SELECT COUNT(*)
                    FROM t_int_post_trust_state s
                    JOIN public_posts p ON p.postId = s.post_id
                    WHERE s.freshness_status = 'AWAITING_AUTHOR_CONFIRMATION'
                ) AS freshnessAwaitingConfirmation,
                (
                    SELECT COUNT(*)
                    FROM public_posts p
                    LEFT JOIN t_int_post_trust_state s ON s.post_id = p.postId
                    WHERE p.postType = #{questionPostType}
                      AND COALESCE(s.question_status, 'OPEN') IN ('OPEN', 'ANSWERED')
                ) AS unresolvedQuestions,
                (
                    SELECT COUNT(*)
                    FROM t_int_post_useful_feedback f
                    JOIN public_posts p ON p.postId = f.post_id
                    WHERE f.create_time &gt;= #{since7}
                ) AS usefulFeedback7Days,
                (
                    SELECT COUNT(*)
                    FROM t_int_post_useful_feedback f
                    JOIN public_posts p ON p.postId = f.post_id
                    WHERE f.create_time &gt;= #{since30}
                ) AS usefulFeedback30Days,
                (
                    SELECT COUNT(*)
                    FROM t_growth_event g
                    JOIN public_posts p ON p.postId = g.content_id
                    WHERE g.event_type = 'EFFECTIVE_READ'
                      AND g.create_time &gt;= #{since7}
                ) AS effectiveReads7Days,
                (
                    SELECT COUNT(*)
                    FROM t_growth_event g
                    JOIN public_posts p ON p.postId = g.content_id
                    WHERE g.event_type = 'EFFECTIVE_READ'
                      AND g.create_time &gt;= #{since30}
                ) AS effectiveReads30Days
            """)
    Map<String, Object> selectTrustedContentSummary(@Param("authorId") Long authorId,
                                                    @Param("questionPostType") int questionPostType,
                                                    @Param("since7") LocalDateTime since7,
                                                    @Param("since30") LocalDateTime since30);

    @Select("""
            <script>
            SELECT s.id AS suggestionId,
                   p.id AS postId,
                   p.title AS postTitle,
                   'PENDING' AS status,
                   s.create_time AS createdAt,
                   s.update_time AS updatedAt
            FROM t_int_content_suggestion s
            JOIN t_post_main p ON p.id = s.post_id
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE s.post_author_id = #{authorId}
              AND s.decision IS NULL
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false') NOT IN ('true', '1')
            ORDER BY s.update_time DESC, s.id DESC
            LIMIT 5
            </script>
            """)
    List<Map<String, Object>> selectPendingSuggestionItems(@Param("authorId") Long authorId);

    @Select("""
            <script>
            SELECT p.id AS postId,
                   p.title AS postTitle,
                   s.freshness_status AS status,
                   s.create_time AS createdAt,
                   s.update_time AS updatedAt
            FROM t_int_post_trust_state s
            JOIN t_post_main p ON p.id = s.post_id
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.author_id = #{authorId}
              AND s.freshness_status = 'AWAITING_AUTHOR_CONFIRMATION'
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false') NOT IN ('true', '1')
            ORDER BY s.update_time DESC, s.post_id DESC
            LIMIT 5
            </script>
            """)
    List<Map<String, Object>> selectFreshnessItems(@Param("authorId") Long authorId);

    @Select("""
            <script>
            SELECT p.id AS postId,
                   p.title AS postTitle,
                   COALESCE(s.question_status, 'OPEN') AS status,
                   p.create_time AS createdAt,
                   COALESCE(s.update_time, p.update_time) AS updatedAt
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            LEFT JOIN t_int_post_trust_state s ON s.post_id = p.id
            WHERE p.author_id = #{authorId}
              AND p.post_type = #{questionPostType}
              AND COALESCE(s.question_status, 'OPEN') IN ('OPEN', 'ANSWERED')
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false') NOT IN ('true', '1')
            ORDER BY COALESCE(s.update_time, p.update_time) DESC, p.id DESC
            LIMIT 5
            </script>
            """)
    List<Map<String, Object>> selectPendingQuestionItems(@Param("authorId") Long authorId,
                                                         @Param("questionPostType") int questionPostType);

    @Select("""
            <script>
            SELECT e.domain AS domain,
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
              AND e.domain IS NOT NULL
              AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false') NOT IN ('true', '1')
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%E2E%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%SMOKE%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%CODEX%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%TESTDATA%'
            GROUP BY e.domain
            ORDER BY postCount DESC, domain ASC
            </script>
            """)
    List<Map<String, Object>> selectAuthorDomainStats(@Param("authorId") Long authorId,
                                                      @Param("since") LocalDateTime since);

    @Select("""
            <script>
            SELECT e.domain AS domain,
                   COUNT(*) AS postCount
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.author_id = #{authorId}
              AND p.create_time &gt;= #{since}
              AND p.create_time &lt; #{until}
              AND e.domain IS NOT NULL
              AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false') NOT IN ('true', '1')
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%E2E%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%SMOKE%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%CODEX%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%TESTDATA%'
            GROUP BY e.domain
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
                   e.domain AS domain,
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
              AND e.domain IS NOT NULL
              AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false') NOT IN ('true', '1')
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

    @Select("""
            <script>
            SELECT p.id AS postId,
                   p.title AS title,
                   e.domain AS domain,
                   CASE WHEN COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.featured')), 'false') IN ('true', '1') THEN 1 ELSE 0 END AS featured,
                   CAST(COALESCE(c.like_count, 0) + COALESCE(c.favorite_count, 0) + COALESCE(c.comment_count, 0) AS SIGNED) AS interactionCount
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            LEFT JOIN t_post_counter c ON c.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.author_id = #{authorId}
              AND e.domain IS NOT NULL
              AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false') NOT IN ('true', '1')
              AND p.id IN
              <foreach collection="postIds" item="postId" open="(" separator="," close=")">
                #{postId}
              </foreach>
            ORDER BY FIELD(p.id
              <foreach collection="postIds" item="postId" separator=",">
                , #{postId}
              </foreach>
            )
            </script>
            """)
    List<Map<String, Object>> selectRepresentativePostsByIds(@Param("authorId") Long authorId,
                                                             @Param("postIds") List<Long> postIds);

    @Select("""
            <script>
            SELECT c.id AS commentId,
                   p.id AS postId,
                   p.title AS postTitle,
                   LEFT(COALESCE(c.content, ''), 120) AS commentExcerpt,
                   COALESCE(c.like_count, 0) AS likeCount,
                   c.create_time AS createTime
            FROM t_int_comment c
            JOIN t_post_main p
              ON p.id = c.post_id
            LEFT JOIN t_post_extension e
              ON e.post_id = p.id
            WHERE c.is_deleted = 0
              AND c.comment_status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.author_id = #{authorId}
              AND (c.author_id IS NULL OR c.author_id != #{authorId})
              AND c.create_time &gt;= #{since}
              AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false') NOT IN ('true', '1')
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%E2E%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%SMOKE%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%CODEX%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%TESTDATA%'
            ORDER BY COALESCE(c.like_count, 0) DESC, c.create_time DESC, c.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> selectCreatorReplyOpportunities(@Param("authorId") Long authorId,
                                                              @Param("since") LocalDateTime since,
                                                              @Param("limit") int limit);
}
