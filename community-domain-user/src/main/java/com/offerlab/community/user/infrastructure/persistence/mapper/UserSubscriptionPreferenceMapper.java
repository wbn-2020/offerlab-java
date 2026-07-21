package com.offerlab.community.user.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.user.api.dto.UserSubscriptionPreferenceKeyDTO;
import com.offerlab.community.user.infrastructure.persistence.po.UserSubscriptionPreferencePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface UserSubscriptionPreferenceMapper extends BaseMapper<UserSubscriptionPreferencePO> {

    @Insert("""
            INSERT INTO t_user_subscription_preference
                (id, uid, source_type, source_id, delivery_mode, expires_at,
                 create_time, update_time, is_deleted)
            VALUES
                (#{id}, #{uid}, #{sourceType}, #{sourceId}, #{deliveryMode}, #{expiresAt},
                 #{createTime}, #{updateTime}, 0)
            ON DUPLICATE KEY UPDATE
                delivery_mode = #{deliveryMode},
                expires_at = #{expiresAt},
                update_time = #{updateTime},
                is_deleted = 0
            """)
    int upsert(UserSubscriptionPreferencePO preference);

    @Select("""
            SELECT id,
                   uid,
                   source_type AS sourceType,
                   source_id AS sourceId,
                   delivery_mode AS deliveryMode,
                   expires_at AS expiresAt,
                   create_time AS createTime,
                   update_time AS updateTime,
                   is_deleted AS isDeleted
              FROM t_user_subscription_preference
             WHERE uid = #{uid}
               AND source_type = #{sourceType}
               AND source_id = #{sourceId}
               AND is_deleted = 0
               AND (expires_at IS NULL OR expires_at > #{now})
             LIMIT 1
            """)
    UserSubscriptionPreferencePO findEffective(@Param("uid") Long uid,
                                               @Param("sourceType") String sourceType,
                                               @Param("sourceId") Long sourceId,
                                               @Param("now") LocalDateTime now);

    @Select("""
            <script>
            SELECT id,
                   uid,
                   source_type AS sourceType,
                   source_id AS sourceId,
                   delivery_mode AS deliveryMode,
                   expires_at AS expiresAt,
                   create_time AS createTime,
                   update_time AS updateTime,
                   is_deleted AS isDeleted
              FROM t_user_subscription_preference
             WHERE uid = #{uid}
               AND is_deleted = 0
               AND (expires_at IS NULL OR expires_at > #{now})
               AND (
                 <foreach collection="sourceKeys" item="sourceKey" separator=" OR ">
                   (source_type = #{sourceKey.sourceType}
                    AND source_id = #{sourceKey.sourceId})
                 </foreach>
               )
             ORDER BY source_type ASC, source_id ASC
            </script>
            """)
    List<UserSubscriptionPreferencePO> findEffectiveBatch(
            @Param("uid") Long uid,
            @Param("sourceKeys") Collection<UserSubscriptionPreferenceKeyDTO> sourceKeys,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE t_user_subscription_preference
               SET is_deleted = 1,
                   update_time = #{now}
             WHERE uid = #{uid}
               AND source_type = #{sourceType}
               AND source_id = #{sourceId}
               AND is_deleted = 0
            """)
    int softDelete(@Param("uid") Long uid,
                   @Param("sourceType") String sourceType,
                   @Param("sourceId") Long sourceId,
                   @Param("now") LocalDateTime now);
}
