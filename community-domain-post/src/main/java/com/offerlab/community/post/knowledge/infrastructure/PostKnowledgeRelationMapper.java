package com.offerlab.community.post.knowledge.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PostKnowledgeRelationMapper {

    @Insert("""
            INSERT INTO t_post_knowledge_relation (
                id, source_post_id, target_post_id, relation_type, reason_text,
                proposer_uid, review_status, visibility_status, risk_level, is_deleted
            ) VALUES (
                #{id}, #{sourcePostId}, #{targetPostId}, #{relationType}, #{reasonText},
                #{proposerUid}, #{reviewStatus}, #{visibilityStatus}, #{riskLevel}, 0
            )
            """)
    int insert(PostKnowledgeRelationRow row);

    @Select("""
            SELECT *
            FROM t_post_knowledge_relation
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    PostKnowledgeRelationRow findActiveById(@Param("id") Long id);

    @Select("""
            SELECT *
            FROM t_post_knowledge_relation
            WHERE source_post_id = #{sourcePostId}
              AND target_post_id = #{targetPostId}
              AND relation_type = #{relationType}
              AND review_status IN ('PENDING', 'APPROVED')
              AND is_deleted = 0
            LIMIT 1
            """)
    PostKnowledgeRelationRow findEffective(@Param("sourcePostId") Long sourcePostId,
                                           @Param("targetPostId") Long targetPostId,
                                           @Param("relationType") String relationType);

    @Select("""
            SELECT *
            FROM t_post_knowledge_relation
            WHERE proposer_uid = #{uid}
              AND review_status IN ('PENDING', 'REJECTED')
              AND is_deleted = 0
            ORDER BY update_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<PostKnowledgeRelationRow> listOwnedActions(@Param("uid") Long uid,
                                                    @Param("limit") int limit);

    @Select("""
            <script>
            SELECT *
            FROM t_post_knowledge_relation
            WHERE proposer_uid = #{uid}
              AND review_status IN ('PENDING', 'REJECTED')
              <if test="status != null and status != ''">
              AND review_status = #{status}
              </if>
              AND is_deleted = 0
              AND (
                    #{cursorTime} IS NULL
                    OR update_time &lt; #{cursorTime}
                    OR (update_time = #{cursorTime} AND id &lt; #{cursorId})
                  )
            ORDER BY update_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<PostKnowledgeRelationRow> listOwnedActionsAfter(
            @Param("uid") Long uid,
            @Param("status") String status,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM t_post_knowledge_relation
            WHERE proposer_uid = #{uid}
              AND review_status IN ('PENDING', 'REJECTED')
              <if test="status != null and status != ''">
              AND review_status = #{status}
              </if>
              AND is_deleted = 0
            </script>
            """)
    long countOwnedActions(@Param("uid") Long uid,
                           @Param("status") String status);

    @Select("""
            SELECT *
            FROM t_post_knowledge_relation
            WHERE review_status = 'PENDING'
              AND is_deleted = 0
            ORDER BY update_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<PostKnowledgeRelationRow> listPendingReviewActions(@Param("limit") int limit);

    @Select("""
            <script>
            SELECT r.*
            FROM t_post_knowledge_relation r
            JOIN t_post_main source_post
              ON source_post.id = r.source_post_id
             AND source_post.is_deleted = 0
            JOIN t_post_extension source_extension
              ON source_extension.post_id = source_post.id
             AND source_extension.domain IN
             <foreach collection="domains" item="domain" open="(" separator="," close=")">
               #{domain}
             </foreach>
            WHERE r.review_status = 'PENDING'
              <if test="status != null and status != ''">
              AND r.review_status = #{status}
              </if>
              AND r.proposer_uid &lt;&gt; #{uid}
              AND r.is_deleted = 0
              AND (
                    #{cursorTime} IS NULL
                    OR r.update_time &lt; #{cursorTime}
                    OR (r.update_time = #{cursorTime} AND r.id &lt; #{cursorId})
                  )
            ORDER BY r.update_time DESC, r.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<PostKnowledgeRelationRow> listPendingReviewActionsForDomainsAfter(
            @Param("uid") Long uid,
            @Param("domains") List<Integer> domains,
            @Param("status") String status,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM t_post_knowledge_relation r
            JOIN t_post_main source_post
              ON source_post.id = r.source_post_id
             AND source_post.is_deleted = 0
            JOIN t_post_extension source_extension
              ON source_extension.post_id = source_post.id
             AND source_extension.domain IN
             <foreach collection="domains" item="domain" open="(" separator="," close=")">
               #{domain}
             </foreach>
            WHERE r.review_status = 'PENDING'
              <if test="status != null and status != ''">
              AND r.review_status = #{status}
              </if>
              AND r.proposer_uid &lt;&gt; #{uid}
              AND r.is_deleted = 0
            </script>
            """)
    long countPendingReviewActionsForDomains(@Param("uid") Long uid,
                                             @Param("domains") List<Integer> domains,
                                             @Param("status") String status);

    @Select("""
            WITH RECURSIVE relation_path(node_id, visited_ids, depth) AS (
                SELECT target_post_id,
                       CAST(CONCAT(source_post_id, ',', target_post_id) AS CHAR(4000)),
                       1
                FROM t_post_knowledge_relation
                WHERE source_post_id = #{startPostId}
                  AND relation_type = #{relationType}
                  AND review_status IN ('PENDING', 'APPROVED')
                  AND is_deleted = 0
                  AND (#{excludeId} IS NULL OR id <> #{excludeId})
                UNION ALL
                SELECT relation_row.target_post_id,
                       CONCAT(relation_path.visited_ids, ',', relation_row.target_post_id),
                       relation_path.depth + 1
                FROM relation_path
                JOIN t_post_knowledge_relation relation_row
                  ON relation_row.source_post_id = relation_path.node_id
                 AND relation_row.relation_type = #{relationType}
                 AND relation_row.review_status IN ('PENDING', 'APPROVED')
                 AND relation_row.is_deleted = 0
                 AND (#{excludeId} IS NULL OR relation_row.id <> #{excludeId})
                WHERE relation_path.depth < 100
                  AND FIND_IN_SET(relation_row.target_post_id, relation_path.visited_ids) = 0
            )
            SELECT CASE
                     WHEN EXISTS (
                         SELECT 1
                         FROM relation_path
                         WHERE node_id = #{targetPostId}
                     ) THEN 1
                     WHEN EXISTS (
                         SELECT 1
                         FROM relation_path
                         WHERE depth >= 100
                     ) THEN 1
                     ELSE 0
                   END
            """)
    int countEffectivePath(@Param("startPostId") Long startPostId,
                           @Param("targetPostId") Long targetPostId,
                           @Param("relationType") String relationType,
                           @Param("excludeId") Long excludeId);

    @Select("""
            SELECT r.*
            FROM t_post_knowledge_relation r
            JOIN t_post_main source_post
              ON source_post.id = r.source_post_id
             AND source_post.post_status = 1
             AND source_post.visibility = 1
             AND source_post.is_deleted = 0
            JOIN t_post_main target_post
              ON target_post.id = r.target_post_id
             AND target_post.post_status = 1
             AND target_post.visibility = 1
             AND target_post.is_deleted = 0
            WHERE (r.source_post_id = #{postId} OR r.target_post_id = #{postId})
              AND r.review_status = 'APPROVED'
              AND r.visibility_status = 'VISIBLE'
              AND r.is_deleted = 0
            ORDER BY r.create_time DESC, r.id DESC
            LIMIT #{limit}
            """)
    List<PostKnowledgeRelationRow> listPublicByPostId(@Param("postId") Long postId,
                                                       @Param("limit") int limit);

    @Select("""
            <script>
            SELECT r.*
            FROM t_post_knowledge_relation r
            JOIN t_post_main source_post
              ON source_post.id = r.source_post_id
             AND source_post.post_status = 1
             AND source_post.visibility = 1
             AND source_post.is_deleted = 0
            JOIN t_post_main target_post
              ON target_post.id = r.target_post_id
             AND target_post.post_status = 1
             AND target_post.visibility = 1
             AND target_post.is_deleted = 0
            WHERE r.relation_type IN ('PREREQUISITE_OF', 'CONTINUES', 'SUPERSEDES')
              <choose>
                <when test="upstream">
                  AND (
                        (r.relation_type = 'PREREQUISITE_OF' AND r.target_post_id = #{postId})
                        OR (r.relation_type IN ('CONTINUES', 'SUPERSEDES')
                            AND r.source_post_id = #{postId})
                      )
                </when>
                <otherwise>
                  AND (
                        (r.relation_type = 'PREREQUISITE_OF' AND r.source_post_id = #{postId})
                        OR (r.relation_type IN ('CONTINUES', 'SUPERSEDES')
                            AND r.target_post_id = #{postId})
                      )
                </otherwise>
              </choose>
              AND r.review_status = 'APPROVED'
              AND r.visibility_status = 'VISIBLE'
              AND r.is_deleted = 0
            ORDER BY r.create_time DESC, r.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<PostKnowledgeRelationRow> listPublicChainByPostId(@Param("postId") Long postId,
                                                            @Param("upstream") boolean upstream,
                                                            @Param("limit") int limit);

    @Update("""
            UPDATE t_post_knowledge_relation
            SET review_status = #{reviewStatus},
                reviewer_uid = #{reviewerUid},
                review_note = #{reviewNote},
                reviewed_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND review_status = 'PENDING'
              AND proposer_uid <> #{reviewerUid}
              AND is_deleted = 0
            """)
    int reviewPending(@Param("id") Long id,
                      @Param("reviewStatus") String reviewStatus,
                      @Param("reviewerUid") Long reviewerUid,
                      @Param("reviewNote") String reviewNote);
}
