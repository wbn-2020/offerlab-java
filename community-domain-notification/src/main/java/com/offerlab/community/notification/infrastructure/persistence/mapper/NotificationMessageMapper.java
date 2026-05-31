package com.offerlab.community.notification.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationMessagePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NotificationMessageMapper extends BaseMapper<NotificationMessagePO> {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_notif_message'
            """)
    int tableExists();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 't_notif_message'
              AND column_name = 'dedup_key'
            """)
    int dedupKeyColumnExists();

    @Select("""
            SELECT id, receiver_uid, sender_uid, notif_type, target_type, target_id,
                   content_json, is_read, create_time, is_deleted
            FROM t_notif_message
            WHERE receiver_uid = #{uid}
              AND is_read = 0
              AND is_deleted = 0
            ORDER BY create_time DESC, id DESC
            LIMIT 1
            """)
    NotificationMessagePO selectLatestUnread(@Param("uid") Long uid);

    @Select("""
            <script>
            SELECT id, receiver_uid, sender_uid, notif_type, target_type, target_id,
                   content_json, is_read, create_time, is_deleted
            FROM t_notif_message
            <where>
              receiver_uid = #{uid}
              AND is_deleted = 0
              <if test="notifType != null">
                AND notif_type = #{notifType}
              </if>
              <if test="cursorTime != null">
                AND create_time &lt; #{cursorTime}
              </if>
            </where>
            ORDER BY create_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<NotificationMessagePO> listByUser(@Param("uid") Long uid,
                                           @Param("notifType") Integer notifType,
                                           @Param("cursorTime") LocalDateTime cursorTime,
                                           @Param("limit") int limit);

    @Insert("""
            INSERT IGNORE INTO t_notif_message (
                id, receiver_uid, sender_uid, notif_type, target_type, target_id,
                content_json, dedup_key, is_read, is_deleted
            ) VALUES (
                #{id}, #{receiverUid}, #{senderUid}, #{notifType}, #{targetType}, #{targetId},
                #{contentJson}, #{dedupKey}, #{isRead}, #{isDeleted}
            )
            """)
    int insertIgnore(NotificationMessagePO message);

    @Insert("""
            INSERT INTO t_notif_message (
                id, receiver_uid, sender_uid, notif_type, target_type, target_id,
                content_json, is_read, is_deleted
            ) VALUES (
                #{id}, #{receiverUid}, #{senderUid}, #{notifType}, #{targetType}, #{targetId},
                #{contentJson}, #{isRead}, #{isDeleted}
            )
            """)
    int insertLegacy(NotificationMessagePO message);
}
