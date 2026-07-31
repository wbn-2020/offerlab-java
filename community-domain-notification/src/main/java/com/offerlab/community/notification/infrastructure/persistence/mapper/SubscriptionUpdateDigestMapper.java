package com.offerlab.community.notification.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.SubscriptionUpdateDigestPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SubscriptionUpdateDigestMapper extends BaseMapper<SubscriptionUpdateDigestPO> {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_subscription_update_digest'
            """)
    int tableExists();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 't_subscription_update_digest'
              AND column_name IN (
                  'id', 'receiver_uid', 'source_type', 'source_id',
                  'resource_type', 'resource_id', 'event_type', 'event_key',
                  'actor_uid', 'payload_json', 'occurred_at', 'is_deleted',
                  'create_time', 'update_time'
              )
            """)
    int requiredColumnCount();

    @Insert("""
            INSERT IGNORE INTO t_subscription_update_digest (
                id, receiver_uid, source_type, source_id, resource_type, resource_id,
                event_type, event_key, actor_uid, payload_json, occurred_at, is_deleted
            ) VALUES (
                #{id}, #{receiverUid}, #{sourceType}, #{sourceId}, #{resourceType}, #{resourceId},
                #{eventType}, #{eventKey}, #{actorUid}, #{payloadJson}, #{occurredAt}, #{isDeleted}
            )
            """)
    int insertIgnore(SubscriptionUpdateDigestPO digest);

    @Select("""
            <script>
            SELECT id,
                   receiver_uid AS receiverUid,
                   source_type AS sourceType,
                   source_id AS sourceId,
                   resource_type AS resourceType,
                   resource_id AS resourceId,
                   event_type AS eventType,
                   event_key AS eventKey,
                   actor_uid AS actorUid,
                   payload_json AS payloadJson,
                   occurred_at AS occurredAt,
                   is_deleted AS isDeleted,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_subscription_update_digest
            WHERE receiver_uid = #{uid}
              AND is_deleted = 0
              <if test="resourceType != null">
                AND resource_type = #{resourceType}
              </if>
              <if test="resourceId != null">
                AND resource_id = #{resourceId}
              </if>
              <if test="subscriptionSourceType != null">
                AND source_type = #{subscriptionSourceType}
              </if>
              <if test="subscriptionSourceId != null">
                AND source_id = #{subscriptionSourceId}
              </if>
              <if test="cursorTime != null">
                AND (
                  occurred_at &lt; #{cursorTime}
                  OR (
                    #{cursorId} IS NOT NULL
                    AND occurred_at = #{cursorTime}
                    AND id &lt; #{cursorId}
                  )
                )
              </if>
            ORDER BY occurred_at DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<SubscriptionUpdateDigestPO> listByReceiver(
            @Param("uid") Long uid,
            @Param("resourceType") String resourceType,
            @Param("resourceId") Long resourceId,
            @Param("subscriptionSourceType") String subscriptionSourceType,
            @Param("subscriptionSourceId") Long subscriptionSourceId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);
}
