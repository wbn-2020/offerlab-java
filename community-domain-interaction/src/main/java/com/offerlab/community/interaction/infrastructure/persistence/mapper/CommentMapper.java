package com.offerlab.community.interaction.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface CommentMapper extends BaseMapper<CommentPO> {

    @Select("""
            SELECT id, post_id, post_author_id, author_id, root_id, parent_id,
                   reply_to_uid, content, like_count, helpful_count,
                   comment_status, create_time, update_time, is_deleted
            FROM t_int_comment
            WHERE id = #{id}
              AND is_deleted = 0
            FOR UPDATE
            """)
    CommentPO selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT COUNT(*)
            FROM t_int_comment c
            WHERE c.post_id = #{postId}
              AND COALESCE(c.root_id, 0) = 0
              AND COALESCE(c.parent_id, 0) = 0
              AND c.comment_status = 1
              AND c.is_deleted = 0
              AND NOT EXISTS (
                SELECT 1
                FROM t_int_comment_quality_signal s
                WHERE s.comment_id = c.id
                  AND s.signal_type = 'LOW_QUALITY_FOLDED'
                  AND s.signal_status = 1
                  AND s.is_deleted = 0
              )
            """)
    long countAvailableRootComments(@Param("postId") Long postId);

    @Update("UPDATE t_int_comment SET like_count = GREATEST(0, like_count + #{delta}) WHERE id = #{id} AND comment_status = 1 AND is_deleted = 0")
    int incrLikeCount(@Param("id") Long id, @Param("delta") int delta);

    @Update("UPDATE t_int_comment SET helpful_count = GREATEST(0, helpful_count + #{delta}) WHERE id = #{id} AND comment_status = 1 AND is_deleted = 0")
    int incrHelpfulCount(@Param("id") Long id, @Param("delta") int delta);

    @Select("""
            WITH ranked AS (
              SELECT c.id, c.post_id, c.post_author_id, c.author_id, c.root_id, c.parent_id,
                     c.reply_to_uid, c.content, c.like_count, c.helpful_count,
                     c.comment_status, c.create_time, c.update_time, c.is_deleted,
                     ROW_NUMBER() OVER (
                       ORDER BY
                         CASE WHEN EXISTS (
                           SELECT 1
                           FROM t_int_comment_quality_signal s
                           WHERE s.post_id = c.post_id
                             AND s.signal_type = 'AUTHOR_PINNED'
                             AND s.signal_status = 1
                             AND s.is_deleted = 0
                             AND (s.comment_id = c.id OR s.root_id = c.id)
                         ) THEN 1 ELSE 0 END DESC,
                         CASE WHEN EXISTS (
                           SELECT 1
                           FROM t_int_comment_quality_signal s
                           WHERE s.post_id = c.post_id
                             AND s.signal_type = 'FEATURED'
                             AND s.signal_status = 1
                             AND s.is_deleted = 0
                             AND (s.comment_id = c.id OR s.root_id = c.id)
                         ) THEN 1 ELSE 0 END DESC,
                         CASE WHEN c.author_id = c.post_author_id THEN 1 ELSE 0 END DESC,
                         COALESCE(c.helpful_count, 0) DESC,
                         COALESCE(c.like_count, 0) DESC,
                         c.create_time DESC,
                         c.id DESC
                     ) AS rn
              FROM t_int_comment c
              WHERE c.post_id = #{postId}
                AND c.root_id = 0
                AND c.comment_status = 1
                AND c.is_deleted = 0
            ),
            cursor_rank AS (
              SELECT 0 AS rn
              WHERE #{beforeId} IS NULL
              UNION ALL
              SELECT rn
              FROM ranked
              WHERE #{beforeId} IS NOT NULL
                AND id = #{beforeId}
            )
            SELECT r.id, r.post_id, r.post_author_id, r.author_id, r.root_id, r.parent_id,
                   r.reply_to_uid, r.content, r.like_count, r.helpful_count,
                   r.comment_status, r.create_time, r.update_time, r.is_deleted
            FROM ranked r
            JOIN cursor_rank cr ON r.rn > cr.rn
            ORDER BY r.rn
            LIMIT #{limit}
            """)
    List<CommentPO> selectQualityRoots(@Param("postId") Long postId,
                                       @Param("beforeId") Long beforeId,
                                       @Param("limit") int limit);

    @Select("""
            <script>
            SELECT id, post_id, post_author_id, author_id, root_id, parent_id,
                   reply_to_uid, content, like_count, helpful_count,
                   comment_status, create_time, update_time, is_deleted
            FROM (
                SELECT c.id, c.post_id, c.post_author_id, c.author_id, c.root_id, c.parent_id,
                       c.reply_to_uid, c.content, c.like_count, c.helpful_count,
                       c.comment_status, c.create_time, c.update_time, c.is_deleted,
                       ROW_NUMBER() OVER (PARTITION BY c.root_id ORDER BY c.create_time ASC, c.id ASC) AS rn
                FROM t_int_comment c
                WHERE c.post_id = #{postId}
                  AND c.root_id IN
                  <foreach collection="rootIds" item="rootId" open="(" separator="," close=")">
                    #{rootId}
                  </foreach>
                  AND c.comment_status = 1
                  AND c.is_deleted = 0
            ) ranked
            WHERE rn &lt;= #{limitPerRoot}
            ORDER BY root_id ASC, create_time ASC, id ASC
            </script>
            """)
    List<CommentPO> selectPreviewRepliesByRootIds(@Param("postId") Long postId,
                                                  @Param("rootIds") List<Long> rootIds,
                                                  @Param("limitPerRoot") int limitPerRoot);

    @Select("""
            <script>
            SELECT c.id, c.post_id, c.post_author_id, c.author_id, c.root_id, c.parent_id,
                   c.reply_to_uid, c.content, c.like_count, c.helpful_count,
                   c.comment_status, c.create_time, c.update_time, c.is_deleted
            FROM t_int_comment c
            WHERE c.post_id = #{postId}
              AND c.root_id = #{rootId}
              AND c.comment_status = 1
              AND c.is_deleted = 0
              <if test="afterCreateTime != null">
                AND (
                  c.create_time &gt; #{afterCreateTime}
                  OR (#{afterId} IS NOT NULL AND c.create_time = #{afterCreateTime} AND c.id &gt; #{afterId})
                )
              </if>
            ORDER BY c.create_time ASC, c.id ASC
            LIMIT #{limit}
            </script>
            """)
    List<CommentPO> selectRepliesByRootId(@Param("postId") Long postId,
                                          @Param("rootId") Long rootId,
                                          @Param("afterCreateTime") LocalDateTime afterCreateTime,
                                          @Param("afterId") Long afterId,
                                          @Param("limit") int limit);

    @Select("""
            <script>
            SELECT c.root_id AS rootId, COUNT(*) AS replyCount
            FROM t_int_comment c
            WHERE c.post_id = #{postId}
              AND c.root_id IN
              <foreach collection="rootIds" item="rootId" open="(" separator="," close=")">
                #{rootId}
              </foreach>
              AND c.comment_status = 1
              AND c.is_deleted = 0
            GROUP BY c.root_id
            </script>
            """)
    List<Map<String, Object>> countRepliesByRootIds(@Param("postId") Long postId,
                                                    @Param("rootIds") List<Long> rootIds);
}
