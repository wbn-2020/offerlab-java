package com.offerlab.community.interaction.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentHelpfulPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CommentHelpfulMapper extends BaseMapper<CommentHelpfulPO> {

    @Select("""
            SELECT id, uid, post_id, comment_id, helpful_status,
                   create_time, update_time, is_deleted
            FROM t_int_comment_helpful
            WHERE uid = #{uid}
              AND comment_id = #{commentId}
            ORDER BY is_deleted ASC, update_time DESC
            LIMIT 1
            """)
    CommentHelpfulPO selectByUidAndComment(@Param("uid") Long uid,
                                           @Param("commentId") Long commentId);

    @Update("""
            UPDATE t_int_comment_helpful
            SET helpful_status = 1,
                is_deleted = 0,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int restoreById(@Param("id") Long id);

    @Update("""
            UPDATE t_int_comment_helpful
            SET helpful_status = 0,
                is_deleted = 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    int softDeleteById(@Param("id") Long id);

    @Select("""
            SELECT COUNT(*)
            FROM t_int_comment_helpful
            WHERE comment_id = #{commentId}
              AND helpful_status = 1
              AND is_deleted = 0
            """)
    long countActiveByComment(@Param("commentId") Long commentId);

    @Select("""
            <script>
            SELECT comment_id
            FROM t_int_comment_helpful
            WHERE uid = #{uid}
              AND helpful_status = 1
              AND is_deleted = 0
              AND comment_id IN
              <foreach collection="commentIds" item="commentId" open="(" separator="," close=")">
                #{commentId}
              </foreach>
            </script>
            """)
    List<Long> selectActiveCommentIdsByUid(@Param("uid") Long uid,
                                           @Param("commentIds") List<Long> commentIds);
}
