package com.offerlab.community.interaction.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.PostUsefulFeedbackPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface PostUsefulFeedbackMapper extends BaseMapper<PostUsefulFeedbackPO> {

    @Select("""
            SELECT id, user_id, post_id, post_author_id, reason, create_time, update_time
            FROM t_int_post_useful_feedback
            WHERE user_id = #{userId}
              AND post_id = #{postId}
            LIMIT 1
            """)
    PostUsefulFeedbackPO selectByUserAndPost(@Param("userId") Long userId,
                                             @Param("postId") Long postId);

    @Select("""
            SELECT id, user_id, post_id, post_author_id, reason, create_time, update_time
            FROM t_int_post_useful_feedback
            WHERE user_id = #{userId}
              AND post_id = #{postId}
            LIMIT 1
            FOR UPDATE
            """)
    PostUsefulFeedbackPO selectByUserAndPostForUpdate(@Param("userId") Long userId,
                                                      @Param("postId") Long postId);

    @Insert("""
            INSERT INTO t_int_post_useful_feedback (
                id, user_id, post_id, post_author_id, reason, create_time, update_time
            )
            VALUES (
                #{id}, #{userId}, #{postId}, #{postAuthorId}, #{reason},
                CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
            )
            ON DUPLICATE KEY UPDATE
                post_author_id = VALUES(post_author_id),
                reason = VALUES(reason),
                update_time = CURRENT_TIMESTAMP(3)
            """)
    int upsert(@Param("id") Long id,
               @Param("userId") Long userId,
               @Param("postId") Long postId,
               @Param("postAuthorId") Long postAuthorId,
               @Param("reason") String reason);

    @Delete("""
            DELETE FROM t_int_post_useful_feedback
            WHERE user_id = #{userId}
              AND post_id = #{postId}
            """)
    int deleteByUserAndPost(@Param("userId") Long userId,
                            @Param("postId") Long postId);

    @Select("""
            SELECT reason, COUNT(*) AS feedbackCount
            FROM t_int_post_useful_feedback
            WHERE post_id = #{postId}
            GROUP BY reason
            """)
    List<Map<String, Object>> countByReason(@Param("postId") Long postId);
}
