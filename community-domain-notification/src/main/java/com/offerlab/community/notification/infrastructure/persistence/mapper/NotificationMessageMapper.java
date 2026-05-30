package com.offerlab.community.notification.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationMessagePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NotificationMessageMapper extends BaseMapper<NotificationMessagePO> {

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
}
