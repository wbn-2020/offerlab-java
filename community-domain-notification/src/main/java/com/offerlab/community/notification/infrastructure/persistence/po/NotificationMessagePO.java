package com.offerlab.community.notification.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_notif_message")
public class NotificationMessagePO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long receiverUid;
    private Long senderUid;
    private Integer notifType;
    private Integer targetType;
    private Long targetId;
    private String contentJson;
    private Integer isRead;
    private LocalDateTime createTime;
    private Integer isDeleted;
}
