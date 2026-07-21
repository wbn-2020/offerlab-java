package com.offerlab.community.feed.infrastructure.persistence.mapper;

import com.offerlab.community.feed.infrastructure.persistence.po.FeedFeedbackPreferencePO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Mapper
public interface FeedFeedbackPreferenceMapper {

    @Insert("""
            INSERT INTO t_feed_feedback_preference
                (id, uid, post_id, action, target_type, target_id, reason,
                 expires_at, create_time, update_time)
            VALUES
                (#{id}, #{uid}, #{postId}, #{action}, #{targetType}, #{targetId}, #{reason},
                 #{expiresAt}, #{createTime}, #{updateTime})
            ON DUPLICATE KEY UPDATE
                action = #{action},
                target_type = #{targetType},
                target_id = #{targetId},
                reason = #{reason},
                expires_at = #{expiresAt},
                update_time = #{updateTime}
            """)
    int upsert(FeedFeedbackPreferencePO preference);

    @Delete("""
            DELETE FROM t_feed_feedback_preference
             WHERE uid = #{uid}
               AND post_id = #{postId}
            """)
    int deleteByPost(@Param("uid") Long uid, @Param("postId") Long postId);

    @Select("""
            <script>
            SELECT id,
                   uid,
                   post_id AS postId,
                   action,
                   target_type AS targetType,
                   target_id AS targetId,
                   reason,
                   expires_at AS expiresAt,
                   create_time AS createTime,
                   update_time AS updateTime
              FROM t_feed_feedback_preference
             WHERE uid = #{uid}
               AND expires_at &gt; #{now}
             <if test="cursorTime != null">
               AND (update_time &lt; #{cursorTime}
                    OR (update_time = #{cursorTime} AND id &lt; #{cursorId}))
             </if>
             ORDER BY update_time DESC, id DESC
             LIMIT #{limit}
            </script>
            """)
    List<FeedFeedbackPreferencePO> listActive(@Param("uid") Long uid,
                                              @Param("now") LocalDateTime now,
                                              @Param("cursorTime") LocalDateTime cursorTime,
                                              @Param("cursorId") Long cursorId,
                                              @Param("limit") int limit);

    @Select("""
            SELECT id,
                   uid,
                   post_id AS postId,
                   action,
                   target_type AS targetType,
                   target_id AS targetId,
                   reason,
                   expires_at AS expiresAt,
                   create_time AS createTime,
                   update_time AS updateTime
              FROM t_feed_feedback_preference
             WHERE uid = #{uid}
               AND post_id = #{postId}
               AND expires_at > #{now}
             LIMIT 1
            """)
    FeedFeedbackPreferencePO findActive(@Param("uid") Long uid,
                                        @Param("postId") Long postId,
                                        @Param("now") LocalDateTime now);

    @Select("""
            SELECT post_id
              FROM t_feed_feedback_preference
             WHERE uid = #{uid}
               AND action = 'HIDE'
               AND expires_at > #{now}
            """)
    Set<Long> listActiveHiddenPostIds(@Param("uid") Long uid,
                                      @Param("now") LocalDateTime now);

    @Select("""
            SELECT DISTINCT target_id
              FROM t_feed_feedback_preference
             WHERE uid = #{uid}
               AND action = 'LESS_LIKE_THIS'
               AND target_type = 'DOMAIN'
               AND expires_at > #{now}
            """)
    Set<Long> listActiveReducedDomainIds(@Param("uid") Long uid,
                                         @Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(*)
              FROM t_feed_feedback_preference
             WHERE uid = #{uid}
               AND post_id <> #{postId}
               AND action = 'LESS_LIKE_THIS'
               AND target_type = 'DOMAIN'
               AND target_id = #{domain}
               AND expires_at > #{now}
            """)
    int countOtherActiveDomainControls(@Param("uid") Long uid,
                                       @Param("postId") Long postId,
                                       @Param("domain") Integer domain,
                                       @Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(*)
              FROM information_schema.tables
             WHERE table_schema = DATABASE()
               AND table_name = 't_feed_feedback_preference'
            """)
    int tableExists();
}
