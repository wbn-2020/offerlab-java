package com.offerlab.community.user.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.user.infrastructure.persistence.po.UserFollowPO;
import com.offerlab.community.user.infrastructure.persistence.projection.UserRelationshipDeliveryCountView;
import com.offerlab.community.user.infrastructure.persistence.projection.UserRelationshipView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollowPO> {

    @Select("""
            SELECT id, from_uid, to_uid, create_time, is_deleted
            FROM t_user_follow
            WHERE from_uid = #{fromUid}
              AND to_uid = #{toUid}
            ORDER BY is_deleted ASC, create_time DESC
            LIMIT 1
            """)
    UserFollowPO selectAnyByPair(@Param("fromUid") Long fromUid, @Param("toUid") Long toUid);

    @Select("""
            <script>
            SELECT to_uid
            FROM t_user_follow
            WHERE from_uid = #{fromUid}
              AND is_deleted = 0
              AND to_uid IN
              <foreach collection="toUids" item="toUid" open="(" separator="," close=")">
                #{toUid}
              </foreach>
            </script>
            """)
    List<Long> selectFollowingTargets(@Param("fromUid") Long fromUid, @Param("toUids") Collection<Long> toUids);

    @Select("""
            <script>
            SELECT f.id AS relationId,
                   f.to_uid AS uid,
                   p.nickname AS nickname,
                    p.bio AS bio,
                    f.create_time AS relationTime,
                    p.update_time AS lastPublicUpdateAt,
                    'IMMEDIATE' AS deliveryMode,
                    NULL AS expiresAt
             FROM t_user_follow f
             JOIN t_user_profile p ON p.id = f.to_uid
                                 AND p.is_deleted = 0
             LEFT JOIN t_user_privacy_setting ps ON ps.user_id = p.id
             WHERE f.from_uid = #{fromUid}
              AND f.is_deleted = 0
              AND (
                    p.id = #{fromUid}
                    OR COALESCE(ps.profile_visibility, 'PUBLIC') = 'PUBLIC'
                    OR (
                        COALESCE(ps.profile_visibility, 'PUBLIC') = 'FOLLOWERS'
                        AND EXISTS (
                            SELECT 1
                            FROM t_user_follow viewerFollow
                            WHERE viewerFollow.from_uid = #{fromUid}
                              AND viewerFollow.to_uid = p.id
                              AND viewerFollow.is_deleted = 0
                        )
                    )
                  )
              <if test="mode == 'MUTED'">
                AND 1 = 0
              </if>
              <if test="cursorTime != null">
              AND (
                    f.create_time &lt; #{cursorTime}
                    OR (
                        f.create_time = #{cursorTime}
                        AND (
                            f.id &lt; #{cursorId}
                            OR (
                                f.id = #{cursorId}
                                AND 'USER' &gt; #{cursorSourceType}
                            )
                        )
                    )
                  )
              </if>
            ORDER BY f.create_time DESC, f.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<UserRelationshipView> selectFollowingRelationshipRows(
            @Param("fromUid") Long fromUid,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("cursorSourceType") String cursorSourceType,
            @Param("mode") String mode,
            @Param("limit") int limit);

    @Select("""
            SELECT 'IMMEDIATE' AS deliveryMode,
                   COUNT(*) AS count
             FROM t_user_follow f
             JOIN t_user_profile p ON p.id = f.to_uid
                                 AND p.is_deleted = 0
             LEFT JOIN t_user_privacy_setting ps ON ps.user_id = p.id
             WHERE f.from_uid = #{fromUid}
              AND f.is_deleted = 0
              AND (
                    p.id = #{fromUid}
                    OR COALESCE(ps.profile_visibility, 'PUBLIC') = 'PUBLIC'
                    OR (
                        COALESCE(ps.profile_visibility, 'PUBLIC') = 'FOLLOWERS'
                        AND EXISTS (
                            SELECT 1
                            FROM t_user_follow viewerFollow
                            WHERE viewerFollow.from_uid = #{fromUid}
                              AND viewerFollow.to_uid = p.id
                              AND viewerFollow.is_deleted = 0
                        )
                    )
                  )
            GROUP BY deliveryMode
            ORDER BY deliveryMode ASC
            """)
    List<UserRelationshipDeliveryCountView> countVisibleFollowingRelationshipsByDeliveryMode(
            @Param("fromUid") Long fromUid);

    @Select("""
            SELECT COUNT(*)
            FROM t_user_follow
            WHERE from_uid = #{fromUid}
              AND to_uid = #{toUid}
              AND is_deleted = 0
            """)
    int existsActiveFollowing(@Param("fromUid") Long fromUid, @Param("toUid") Long toUid);

    @Select("""
            SELECT COUNT(*)
            FROM t_user_follow f
            JOIN t_user_profile p ON p.id = f.to_uid
                                AND p.is_deleted = 0
            LEFT JOIN t_user_privacy_setting ps ON ps.user_id = p.id
            WHERE f.from_uid = #{fromUid}
              AND f.is_deleted = 0
              AND (
                    p.id = #{fromUid}
                    OR COALESCE(ps.profile_visibility, 'PUBLIC') = 'PUBLIC'
                    OR (
                        COALESCE(ps.profile_visibility, 'PUBLIC') = 'FOLLOWERS'
                        AND EXISTS (
                            SELECT 1
                            FROM t_user_follow viewerFollow
                            WHERE viewerFollow.from_uid = #{fromUid}
                              AND viewerFollow.to_uid = p.id
                              AND viewerFollow.is_deleted = 0
                        )
                    )
                  )
            """)
    long countVisibleFollowingRelationships(@Param("fromUid") Long fromUid);

    @Update("UPDATE t_user_follow SET is_deleted = 0 WHERE id = #{id} AND is_deleted = 1")
    int restoreById(@Param("id") Long id);

    @Update("UPDATE t_user_follow SET is_deleted = 1 WHERE id = #{id} AND is_deleted = 0")
    int softDeleteById(@Param("id") Long id);
}
