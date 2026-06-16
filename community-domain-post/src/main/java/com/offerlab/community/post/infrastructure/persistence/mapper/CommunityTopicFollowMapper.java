package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.CommunityTopicFollowPO;
import com.offerlab.community.post.infrastructure.persistence.projection.CommunityTopicFollowView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CommunityTopicFollowMapper extends BaseMapper<CommunityTopicFollowPO> {

    @Select("""
            SELECT id, topic_id, uid, create_time, is_deleted
            FROM t_community_topic_follow
            WHERE uid = #{uid}
              AND topic_id = #{topicId}
            ORDER BY is_deleted ASC, create_time DESC
            LIMIT 1
            """)
    CommunityTopicFollowPO selectAnyByPair(@Param("uid") Long uid, @Param("topicId") Long topicId);

    @Update("UPDATE t_community_topic_follow SET is_deleted = 0 WHERE id = #{id} AND is_deleted = 1")
    int restoreById(@Param("id") Long id);

    @Update("UPDATE t_community_topic_follow SET is_deleted = 1 WHERE id = #{id} AND is_deleted = 0")
    int softDeleteById(@Param("id") Long id);

    @Select("""
            SELECT COUNT(*)
            FROM t_community_topic_follow
            WHERE topic_id = #{topicId}
              AND is_deleted = 0
            """)
    long countByTopicId(@Param("topicId") Long topicId);

    @Select("""
            <script>
            SELECT topic_id
            FROM t_community_topic_follow
            WHERE uid = #{uid}
              AND is_deleted = 0
              AND topic_id IN
              <foreach collection="topicIds" item="topicId" open="(" separator="," close=")">
                #{topicId}
              </foreach>
            </script>
            """)
    List<Long> selectFollowedTopicIds(@Param("uid") Long uid, @Param("topicIds") Collection<Long> topicIds);

    @Select("""
            <script>
            SELECT f.id AS relation_id,
                   t.*
            FROM t_community_topic_follow f
            JOIN t_community_topic t ON t.id = f.topic_id
            WHERE f.uid = #{uid}
              AND f.is_deleted = 0
              AND t.is_deleted = 0
              AND t.topic_status = 1
              <if test="cursor != null and cursor > 0">
              AND f.id &lt; #{cursor}
              </if>
            ORDER BY f.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CommunityTopicFollowView> selectFollowingTopics(@Param("uid") Long uid,
                                                         @Param("cursor") Long cursor,
                                                         @Param("limit") int limit);

    @Select("""
            SELECT uid
            FROM t_community_topic_follow
            WHERE topic_id = #{topicId}
              AND (#{authorId} IS NULL OR uid != #{authorId})
              AND is_deleted = 0
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<Long> selectFollowerUidsForNotification(@Param("topicId") Long topicId,
                                                 @Param("authorId") Long authorId,
                                                 @Param("limit") int limit);
}
