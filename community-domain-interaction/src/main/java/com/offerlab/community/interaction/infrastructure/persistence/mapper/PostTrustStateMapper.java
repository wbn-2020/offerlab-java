package com.offerlab.community.interaction.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.PostTrustStatePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostTrustStateMapper extends BaseMapper<PostTrustStatePO> {

    @Insert("""
            INSERT INTO t_int_post_trust_state (
                post_id, question_status, freshness_status, suggestions_open,
                create_time, update_time
            )
            VALUES (
                #{postId}, #{questionStatus}, #{freshnessStatus}, #{suggestionsOpen},
                CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
            )
            ON DUPLICATE KEY UPDATE post_id = VALUES(post_id)
            """)
    int insertIfAbsent(@Param("postId") Long postId,
                       @Param("questionStatus") String questionStatus,
                       @Param("freshnessStatus") String freshnessStatus,
                       @Param("suggestionsOpen") int suggestionsOpen);

    @Select("""
            SELECT post_id, question_status, accepted_comment_id, duplicate_post_id,
                   freshness_status, successor_post_id, last_confirmed_at, suggestions_open,
                   create_time, update_time
            FROM t_int_post_trust_state
            WHERE post_id = #{postId}
            LIMIT 1
            """)
    PostTrustStatePO selectByPostId(@Param("postId") Long postId);

    @Select("""
            SELECT post_id, question_status, accepted_comment_id, duplicate_post_id,
                   freshness_status, successor_post_id, last_confirmed_at, suggestions_open,
                   create_time, update_time
            FROM t_int_post_trust_state
            WHERE post_id = #{postId}
            FOR UPDATE
            """)
    PostTrustStatePO selectForUpdate(@Param("postId") Long postId);

    @Update("""
            UPDATE t_int_post_trust_state
            SET question_status = #{state.questionStatus},
                accepted_comment_id = #{state.acceptedCommentId},
                duplicate_post_id = #{state.duplicatePostId},
                freshness_status = #{state.freshnessStatus},
                successor_post_id = #{state.successorPostId},
                last_confirmed_at = #{state.lastConfirmedAt},
                suggestions_open = #{state.suggestionsOpen},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE post_id = #{state.postId}
            """)
    int updateState(@Param("state") PostTrustStatePO state);
}
