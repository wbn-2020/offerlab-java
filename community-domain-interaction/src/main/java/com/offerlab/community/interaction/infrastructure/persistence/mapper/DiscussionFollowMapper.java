package com.offerlab.community.interaction.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.DiscussionFollowPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DiscussionFollowMapper extends BaseMapper<DiscussionFollowPO> {

    @Select("""
            SELECT id, uid, post_id, follow_status, last_read_comment_id, last_notified_comment_id,
                   create_time, update_time, is_deleted
            FROM t_int_discussion_follow
            WHERE uid = #{uid}
              AND post_id = #{postId}
            ORDER BY is_deleted ASC, update_time DESC
            LIMIT 1
            """)
    DiscussionFollowPO selectAnyByUserPost(@Param("uid") Long uid, @Param("postId") Long postId);

    @Update("""
            UPDATE t_int_discussion_follow
            SET follow_status = 1,
                is_deleted = 0,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int restoreById(@Param("id") Long id);

    @Update("""
            UPDATE t_int_discussion_follow
            SET follow_status = 0,
                is_deleted = 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    int softDeleteById(@Param("id") Long id);

    @Update("""
            UPDATE t_int_discussion_follow
            SET last_notified_comment_id = GREATEST(COALESCE(last_notified_comment_id, 0), #{commentId}),
                update_time = update_time
            WHERE post_id = #{postId}
              AND uid = #{uid}
              AND follow_status = 1
              AND is_deleted = 0
            """)
    int markNotified(@Param("postId") Long postId,
                     @Param("uid") Long uid,
                     @Param("commentId") Long commentId);

    @Select("""
            SELECT uid
            FROM t_int_discussion_follow
            WHERE post_id = #{postId}
              AND follow_status = 1
              AND is_deleted = 0
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<Long> selectFollowerUidsForNotification(@Param("postId") Long postId,
                                                 @Param("limit") int limit);

    @Select("""
            <script>
            SELECT id, uid, post_id, follow_status, last_read_comment_id, last_notified_comment_id,
                   create_time, update_time, is_deleted
            FROM t_int_discussion_follow
            WHERE post_id = #{postId}
              AND follow_status = 1
              AND is_deleted = 0
              <if test="afterId != null">
              AND id &gt; #{afterId}
              </if>
            ORDER BY id ASC
            LIMIT #{limit}
            </script>
            """)
    List<DiscussionFollowPO> selectFollowerRowsForNotification(@Param("postId") Long postId,
                                                               @Param("afterId") Long afterId,
                                                               @Param("limit") int limit);
}
