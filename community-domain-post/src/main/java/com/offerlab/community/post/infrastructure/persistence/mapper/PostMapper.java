package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface PostMapper extends BaseMapper<PostPO> {

    @Select("""
            SELECT *
            FROM t_post_main
            WHERE id = #{id}
              AND is_deleted = 0
            FOR UPDATE
            """)
    PostPO selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            <script>
            SELECT DATE(p.create_time) AS label, COUNT(*) AS count
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.create_time >= #{since}
              <if test="domain != null">
              AND e.domain = #{domain}
              </if>
            GROUP BY DATE(p.create_time)
            ORDER BY DATE(p.create_time) ASC
            </script>
            """)
    List<Map<String, Object>> countPublishedByDate(@Param("since") LocalDateTime since, @Param("domain") Integer domain);

    @Select("""
            <script>
            SELECT x.company AS name, COUNT(*) AS count
            FROM (
                SELECT COALESCE(
                    NULLIF(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.techStacks[0]')), ''),
                    NULLIF(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.company')), '')
                ) AS company
                FROM t_post_main p
                JOIN t_post_extension e ON e.post_id = p.id
                WHERE p.is_deleted = 0
                  AND p.post_status = 1
                  AND p.visibility = 1
                  AND p.create_time >= #{since}
                  <if test="domain != null">
                  AND e.domain = #{domain}
                  </if>
            ) x
            WHERE x.company IS NOT NULL
            GROUP BY x.company
            ORDER BY COUNT(*) DESC, x.company ASC
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> countCompanies(@Param("since") LocalDateTime since, @Param("limit") int limit, @Param("domain") Integer domain);

    default List<Map<String, Object>> countCompanies(@Param("since") LocalDateTime since, @Param("limit") int limit) {
        return countCompanies(since, limit, null);
    }

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
            <script>
            SELECT x.position AS name, COUNT(*) AS count
            FROM (
                SELECT COALESCE(
                    NULLIF(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.scenario')), ''),
                    NULLIF(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.position')), '')
                ) AS position
                FROM t_post_main p
                JOIN t_post_extension e ON e.post_id = p.id
                WHERE p.is_deleted = 0
                  AND p.post_status = 1
                  AND p.visibility = 1
                  AND p.create_time >= #{since}
                  <if test="domain != null">
                  AND e.domain = #{domain}
                  </if>
            ) x
            WHERE x.position IS NOT NULL
            GROUP BY x.position
            ORDER BY COUNT(*) DESC, x.position ASC
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> countPositions(@Param("since") LocalDateTime since, @Param("limit") int limit, @Param("domain") Integer domain);

    default List<Map<String, Object>> countPositions(@Param("since") LocalDateTime since, @Param("limit") int limit) {
        return countPositions(since, limit, null);
    }

    @Select("""
            <script>
            SELECT x.result AS name, COUNT(*) AS count
            FROM (
                SELECT NULLIF(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.interviewResult')), '') AS result
                FROM t_post_main p
                JOIN t_post_extension e ON e.post_id = p.id
                WHERE p.is_deleted = 0
                  AND p.post_status = 1
                  AND p.visibility = 1
                  AND p.create_time >= #{since}
                  <if test="domain != null">
                  AND e.domain = #{domain}
                  </if>
            ) x
            WHERE x.result IS NOT NULL
            GROUP BY x.result
            ORDER BY COUNT(*) DESC, x.result ASC
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> countInterviewResults(@Param("since") LocalDateTime since, @Param("limit") int limit, @Param("domain") Integer domain);

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
            <script>
            SELECT COUNT(*)
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.create_time >= #{since}
              <if test="domain != null">
              AND e.domain = #{domain}
              </if>
            </script>
            """)
    long countPublishedSince(@Param("since") LocalDateTime since, @Param("domain") Integer domain);

    @Select("""
            SELECT
              COUNT(*) AS postCount,
              SUM(CASE WHEN COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.featured')), 'false') IN ('true', '1') THEN 1 ELSE 0 END) AS featuredCount,
              COALESCE(SUM(c.like_count), 0) AS likeCount,
              COALESCE(SUM(c.favorite_count), 0) AS favoriteCount,
              COALESCE(SUM(c.comment_count), 0) AS commentCount,
              COALESCE(SUM(c.view_count), 0) AS viewCount
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            LEFT JOIN t_post_counter c ON c.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.author_id = #{authorId}
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%E2E%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%SMOKE%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%CODEX%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%TESTDATA%'
            """)
    Map<String, Object> aggregatePublicContributionByAuthor(@Param("authorId") Long authorId);

    @Select("""
            <script>
            SELECT
              p.author_id AS authorId,
              COUNT(*) AS postCount
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.author_id IN
              <foreach collection="authorIds" item="authorId" open="(" separator="," close=")">
                #{authorId}
              </foreach>
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%E2E%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%SMOKE%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%CODEX%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%TESTDATA%'
            GROUP BY p.author_id
            </script>
            """)
    List<Map<String, Object>> countPublicPublishedPostsByAuthors(@Param("authorIds") Collection<Long> authorIds);

    @Select("""
            SELECT p.*
            FROM t_post_main p
            LEFT JOIN t_post_counter c ON c.post_id = p.id
            LEFT JOIN t_post_extension e_domain ON e_domain.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND (#{domain} IS NULL
                   OR e_domain.domain = #{domain})
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
                                @Param("domain") Integer domain,
                                @Param("limit") int limit);

    @Select("""
            <script>
            SELECT p.*
            FROM t_post_main p
            <if test="featured != null">
            LEFT JOIN t_post_extension e_featured ON e_featured.post_id = p.id
            </if>
            <if test="domain != null">
            LEFT JOIN t_post_extension e_domain ON e_domain.post_id = p.id
            </if>
            <if test="tagId != null">
            JOIN t_post_tag_ref r ON r.post_id = p.id AND r.tag_id = #{tagId}
            JOIN t_tag t_filter ON t_filter.id = r.tag_id
                AND t_filter.is_deleted = 0
                AND t_filter.tag_status = 1
                AND t_filter.merge_target_id IS NULL
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
              <if test="featured != null and featured == true">
              AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e_featured.ext_json, '$.featured')), 'false') IN ('true', '1')
              </if>
              <if test="featured != null and featured == false">
              AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e_featured.ext_json, '$.featured')), 'false') NOT IN ('true', '1')
              </if>
              <if test="domain != null">
              AND e_domain.domain = #{domain}
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
                                   @Param("featured") Boolean featured,
                                   @Param("domain") Integer domain,
                                   @Param("cursorTime") LocalDateTime cursorTime,
                                   @Param("cursorId") Long cursorId,
                                   @Param("limit") int limit);

    @Select("""
            <script>
            SELECT p.*
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            LEFT JOIN t_post_counter c ON c.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              <if test="domain != null">
              AND e.domain = #{domain}
              </if>
              <if test="postType != null">
              AND p.post_type = #{postType}
              </if>
              <if test="keyword != null and keyword != ''">
              AND (
                    p.title LIKE CONCAT('%', #{keyword}, '%')
                    OR p.content LIKE CONCAT('%', #{keyword}, '%')
                    OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.summary')) LIKE CONCAT('%', #{keyword}, '%')
                    OR EXISTS (
                        SELECT 1
                        FROM t_post_tag_ref r
                        JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
                        WHERE r.post_id = p.id
                          AND t.tag_name LIKE CONCAT('%', #{keyword}, '%')
                    )
                  )
              </if>
            ORDER BY
              (COALESCE(c.favorite_count, 0) * 4
               + COALESCE(c.comment_count, 0) * 3
               + COALESCE(c.like_count, 0) * 2
               + COALESCE(c.view_count, 0) * 0.1) DESC,
              p.create_time DESC,
              p.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<PostPO> selectOperationCandidates(@Param("keyword") String keyword,
                                           @Param("domain") Integer domain,
                                           @Param("postType") Integer postType,
                                           @Param("limit") int limit);

    @Select("""
            <script>
            SELECT DISTINCT p.*
            FROM t_post_main p
            LEFT JOIN t_post_extension e_topic ON e_topic.post_id = p.id
            <if test="featured != null">
            LEFT JOIN t_post_extension e_featured ON e_featured.post_id = p.id
            </if>
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              <if test="postType != null">
              AND p.post_type = #{postType}
              </if>
              <if test="featured != null and featured == true">
              AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e_featured.ext_json, '$.featured')), 'false') IN ('true', '1')
              </if>
              <if test="featured != null and featured == false">
              AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e_featured.ext_json, '$.featured')), 'false') NOT IN ('true', '1')
              </if>
              <if test="cursorTime != null">
              AND (p.create_time &lt; #{cursorTime}
                   OR (p.create_time = #{cursorTime} AND p.id &lt; #{cursorId}))
              </if>
              AND (
                    <if test="tagIds != null and tagIds.size() > 0">
                    EXISTS (
                        SELECT 1
                        FROM t_post_tag_ref ptr
                        WHERE ptr.post_id = p.id
                          AND ptr.tag_id IN
                          <foreach collection="tagIds" item="tagId" open="(" separator="," close=")">
                              #{tagId}
                          </foreach>
                    )
                    OR
                    </if>
                    p.title LIKE CONCAT('%', #{keyword}, '%')
                    OR p.content LIKE CONCAT('%', #{keyword}, '%')
                    OR JSON_UNQUOTE(JSON_EXTRACT(e_topic.ext_json, '$.contextTopicId')) = CAST(#{topicId} AS CHAR)
                    OR JSON_CONTAINS(
                        JSON_EXTRACT(e_topic.ext_json, '$.topicNames'),
                        JSON_QUOTE(#{keyword})
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM JSON_TABLE(
                            COALESCE(JSON_EXTRACT(e_topic.ext_json, '$.topicNames'), JSON_ARRAY()),
                            '$[*]' COLUMNS(topic_name VARCHAR(128) PATH '$')
                        ) AS topic_item
                        WHERE LOWER(topic_item.topic_name) = LOWER(#{keyword})
                    )
                    OR JSON_UNQUOTE(JSON_EXTRACT(e_topic.ext_json, '$.scenario')) LIKE CONCAT('%', #{keyword}, '%')
                    OR JSON_UNQUOTE(JSON_EXTRACT(e_topic.ext_json, '$.summary')) LIKE CONCAT('%', #{keyword}, '%')
                    OR JSON_UNQUOTE(JSON_EXTRACT(e_topic.ext_json, '$.techStacks')) LIKE CONCAT('%', #{keyword}, '%')
                    OR EXISTS (
                        SELECT 1
                        FROM t_post_tag_ref r
                        JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
                        WHERE r.post_id = p.id
                          AND t.tag_name LIKE CONCAT('%', #{keyword}, '%')
                    )
                  )
            ORDER BY p.create_time DESC, p.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<PostPO> selectPublicPostsByTopic(@Param("topicId") Long topicId,
                                          @Param("tagIds") Collection<Long> tagIds,
                                          @Param("keyword") String keyword,
                                          @Param("postType") Integer postType,
                                          @Param("featured") Boolean featured,
                                          @Param("cursorTime") LocalDateTime cursorTime,
                                          @Param("cursorId") Long cursorId,
                                          @Param("limit") int limit);

    @Select("""
            SELECT p.*
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%E2E%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%SMOKE%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%CODEX%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%TESTDATA%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%DEMO%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%FIXTURE%'
              AND p.id > #{lastId}
            ORDER BY p.id ASC
            LIMIT #{limit}
            """)
    List<PostPO> selectPublicPostsForIndexAfterId(@Param("lastId") Long lastId,
                                                  @Param("limit") int limit);

    @Select("""
            SELECT p.*
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%E2E%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%SMOKE%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%CODEX%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%TESTDATA%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%DEMO%'
              AND UPPER(CONCAT_WS(' ', COALESCE(p.title, ''), COALESCE(p.content, ''), COALESCE(e.ext_json, ''))) NOT LIKE '%FIXTURE%'
              AND p.id > #{lastId}
            ORDER BY p.id ASC
            LIMIT #{limit}
            """)
    List<PostPO> selectPublicSeoPosts(@Param("lastId") Long lastId,
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
                    <if test="keywordPostId != null">
                    OR p.id = #{keywordPostId}
                    </if>
                    OR p.content LIKE CONCAT('%', #{keyword}, '%')
                    OR e.company LIKE CONCAT('%', #{keyword}, '%')
                    OR e.position LIKE CONCAT('%', #{keyword}, '%')
                    OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.scenario')) LIKE CONCAT('%', #{keyword}, '%')
                    OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.summary')) LIKE CONCAT('%', #{keyword}, '%')
                    OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.techStacks')) LIKE CONCAT('%', #{keyword}, '%')
                    OR EXISTS (
                        SELECT 1
                        FROM t_post_tag_ref r
                        JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
                          AND t.tag_status = 1
                          AND t.merge_target_id IS NULL
                        WHERE r.post_id = p.id
                          AND (
                                t.tag_name LIKE CONCAT('%', #{keyword}, '%')
                                OR t.synonyms LIKE CONCAT('%', #{keyword}, '%')
                              )
                    )
                  )
              </if>
              <if test="company != null and company != ''">
              AND (
                    e.company LIKE CONCAT('%', #{company}, '%')
                    OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.techStacks')) LIKE CONCAT('%', #{company}, '%')
                    OR EXISTS (
                        SELECT 1
                        FROM t_post_tag_ref r
                        JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
                          AND t.tag_status = 1
                          AND t.merge_target_id IS NULL
                        WHERE r.post_id = p.id
                          AND (
                                t.tag_name LIKE CONCAT('%', #{company}, '%')
                                OR t.synonyms LIKE CONCAT('%', #{company}, '%')
                              )
                    )
                  )
              </if>
              <if test="position != null and position != ''">
              AND (
                    e.position = #{position}
                    OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.scenario')) = #{position}
                    OR EXISTS (
                        SELECT 1
                        FROM t_post_tag_ref r
                        JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
                          AND t.tag_status = 1
                          AND t.merge_target_id IS NULL
                        WHERE r.post_id = p.id
                          AND (
                                t.tag_name LIKE CONCAT('%', #{position}, '%')
                                OR t.synonyms LIKE CONCAT('%', #{position}, '%')
                              )
                    )
                  )
              </if>
              <if test="type != null">
              AND p.post_type = #{type}
              </if>
              <if test="domain != null">
              AND e.domain = #{domain}
              </if>
              <if test="cursorTime != null">
              AND (p.create_time &lt; #{cursorTime}
                   OR (p.create_time = #{cursorTime} AND p.id &lt; #{cursorId}))
              </if>
            ORDER BY p.create_time DESC, p.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<PostPO> searchPublicPostsFallback(@Param("keyword") String keyword,
                                           @Param("keywordPostId") Long keywordPostId,
                                           @Param("company") String company,
                                           @Param("position") String position,
                                           @Param("type") Integer type,
                                           @Param("domain") Integer domain,
                                           @Param("cursorTime") LocalDateTime cursorTime,
                                           @Param("cursorId") Long cursorId,
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
                    <if test="keywordPostId != null">
                    OR p.id = #{keywordPostId}
                    </if>
                    OR p.content LIKE CONCAT('%', #{keyword}, '%')
                    OR e.company LIKE CONCAT('%', #{keyword}, '%')
                    OR e.position LIKE CONCAT('%', #{keyword}, '%')
                    OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.scenario')) LIKE CONCAT('%', #{keyword}, '%')
                    OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.summary')) LIKE CONCAT('%', #{keyword}, '%')
                    OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.techStacks')) LIKE CONCAT('%', #{keyword}, '%')
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
              AND (
                    e.company LIKE CONCAT('%', #{company}, '%')
                    OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.techStacks')) LIKE CONCAT('%', #{company}, '%')
                    OR EXISTS (
                        SELECT 1
                        FROM t_post_tag_ref r
                        JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
                        WHERE r.post_id = p.id
                          AND t.tag_name LIKE CONCAT('%', #{company}, '%')
                    )
                  )
              </if>
              <if test="position != null and position != ''">
              AND (
                    e.position = #{position}
                    OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.scenario')) = #{position}
                    OR EXISTS (
                        SELECT 1
                        FROM t_post_tag_ref r
                        JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
                        WHERE r.post_id = p.id
                          AND t.tag_name LIKE CONCAT('%', #{position}, '%')
                    )
                  )
              </if>
              <if test="type != null">
              AND p.post_type = #{type}
              </if>
              <if test="domain != null">
              AND e.domain = #{domain}
              </if>
              <if test="cursorTime != null">
              AND (p.create_time &lt; #{cursorTime}
                   OR (p.create_time = #{cursorTime} AND p.id &lt; #{cursorId}))
              </if>
            ORDER BY p.create_time DESC, p.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<PostPO> searchPublicPostsFallbackCompat(@Param("keyword") String keyword,
                                                 @Param("keywordPostId") Long keywordPostId,
                                                 @Param("company") String company,
                                                 @Param("position") String position,
                                                 @Param("type") Integer type,
                                                 @Param("domain") Integer domain,
                                                 @Param("cursorTime") LocalDateTime cursorTime,
                                                 @Param("cursorId") Long cursorId,
                                                 @Param("limit") int limit);

    @Select("""
            <script>
            SELECT p.post_type AS name, COUNT(*) AS count
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.create_time >= #{since}
              <if test="domain != null">
              AND e.domain = #{domain}
              </if>
            GROUP BY p.post_type
            ORDER BY COUNT(*) DESC, p.post_type ASC
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> countPostTypes(@Param("since") LocalDateTime since, @Param("limit") int limit, @Param("domain") Integer domain);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM t_post_main p
            JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.create_time >= #{since}
              AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.featured')), 'false') IN ('true', '1')
              <if test="domain != null">
              AND e.domain = #{domain}
              </if>
            </script>
            """)
    long countFeaturedPostsSince(@Param("since") LocalDateTime since, @Param("domain") Integer domain);

    @Select("""
            <script>
            SELECT COUNT(DISTINCT p.author_id)
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.create_time >= #{since}
              <if test="domain != null">
              AND e.domain = #{domain}
              </if>
            </script>
            """)
    long countActiveAuthorsSince(@Param("since") LocalDateTime since, @Param("domain") Integer domain);

    @Select("""
            <script>
            SELECT p.title AS name,
                   CAST(COALESCE(c.like_count, 0) + COALESCE(c.favorite_count, 0) + COALESCE(c.comment_count, 0) AS SIGNED) AS count
            FROM t_post_main p
            JOIN t_post_extension e ON e.post_id = p.id
            LEFT JOIN t_post_counter c ON c.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.create_time >= #{since}
              AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.featured')), 'false') IN ('true', '1')
              <if test="domain != null">
              AND e.domain = #{domain}
              </if>
            ORDER BY count DESC, p.create_time DESC, p.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> listFeaturedContent(@Param("since") LocalDateTime since, @Param("limit") int limit, @Param("domain") Integer domain);

    @Select("""
            SELECT e.domain AS name,
                   COUNT(*) AS count
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.create_time >= #{since}
            GROUP BY e.domain
            ORDER BY COUNT(*) DESC, name ASC
            """)
    List<Map<String, Object>> countDomainDistribution(@Param("since") LocalDateTime since);

    @Select("""
            SELECT e.domain AS domain,
                   COUNT(*) AS postCount,
                   SUM(CASE WHEN COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.featured')), 'false') IN ('true', '1') THEN 1 ELSE 0 END) AS featuredCount,
                   COUNT(DISTINCT p.author_id) AS activeAuthors
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.create_time >= #{since}
            GROUP BY e.domain
            ORDER BY domain ASC
            """)
    List<Map<String, Object>> listDomainComparisonStats(@Param("since") LocalDateTime since);

    @Select("""
            <script>
            SELECT p.title AS name,
                   CAST(
                       COALESCE(c.like_count, 0) * 3
                       + COALESCE(c.favorite_count, 0) * 4
                       + COALESCE(c.comment_count, 0) * 5
                       + COALESCE(c.view_count, 0) * 0.2
                   AS SIGNED) AS count
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            LEFT JOIN t_post_counter c ON c.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.create_time >= #{since}
              <if test="domain != null">
              AND e.domain = #{domain}
              </if>
            ORDER BY count DESC, p.create_time DESC, p.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> listDomainHotContent(@Param("since") LocalDateTime since, @Param("limit") int limit, @Param("domain") Integer domain);

    @Select("""
            SELECT domain, name, count
            FROM (
              SELECT grouped.*,
                     ROW_NUMBER() OVER (PARTITION BY grouped.domain ORDER BY grouped.count DESC, grouped.createTime DESC, grouped.postId DESC) AS rn
              FROM (
                SELECT e.domain AS domain,
                       p.id AS postId,
                       p.create_time AS createTime,
                       p.title AS name,
                       CAST(
                           COALESCE(c.like_count, 0) * 3
                           + COALESCE(c.favorite_count, 0) * 4
                           + COALESCE(c.comment_count, 0) * 5
                           + COALESCE(c.view_count, 0) * 0.2
                       AS SIGNED) AS count
                FROM t_post_main p
                LEFT JOIN t_post_extension e ON e.post_id = p.id
                LEFT JOIN t_post_counter c ON c.post_id = p.id
                WHERE p.is_deleted = 0
                  AND p.post_status = 1
                  AND p.visibility = 1
                  AND p.create_time >= #{since}
              ) grouped
            ) ranked
            WHERE ranked.rn <= #{limitPerDomain}
            ORDER BY ranked.domain ASC, ranked.rn ASC
            """)
    List<Map<String, Object>> listDomainHotContentByDomain(@Param("since") LocalDateTime since, @Param("limitPerDomain") int limitPerDomain);

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
                    OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.scenario')) LIKE CONCAT('%', #{prefix}, '%')
                    OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.techStacks')) LIKE CONCAT('%', #{prefix}, '%')
                    OR EXISTS (
                        SELECT 1
                        FROM t_post_tag_ref r
                        JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
                          AND t.tag_status = 1
                          AND t.merge_target_id IS NULL
                        WHERE r.post_id = p.id
                          AND (
                                t.tag_name LIKE CONCAT('%', #{prefix}, '%')
                                OR t.synonyms LIKE CONCAT('%', #{prefix}, '%')
                              )
                    )
                  )
            ORDER BY p.create_time DESC, p.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<PostPO> suggestPublicPostsFallback(@Param("prefix") String prefix, @Param("limit") int limit);

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
                    OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.scenario')) LIKE CONCAT('%', #{prefix}, '%')
                    OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.techStacks')) LIKE CONCAT('%', #{prefix}, '%')
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
    List<PostPO> suggestPublicPostsFallbackCompat(@Param("prefix") String prefix, @Param("limit") int limit);

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

    @Select("""
            SELECT p.*
            FROM t_content_series_post sp
            JOIN t_post_main p
              ON p.id = sp.post_id
            LEFT JOIN t_content_series_post cursor_sp
              ON cursor_sp.id = #{cursor}
             AND cursor_sp.series_id = #{seriesId}
             AND cursor_sp.is_deleted = 0
            WHERE sp.series_id = #{seriesId}
              AND sp.is_deleted = 0
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND (#{cursor} = 0
                   OR cursor_sp.id IS NULL
                   OR sp.sort_order > cursor_sp.sort_order
                   OR (sp.sort_order = cursor_sp.sort_order AND sp.id > cursor_sp.id))
            ORDER BY sp.sort_order ASC, sp.id ASC
            LIMIT #{limit}
            """)
    List<PostPO> selectPublicPostsByContentSeries(@Param("seriesId") Long seriesId,
                                                  @Param("cursor") long cursor,
                                                  @Param("limit") int limit);
}
