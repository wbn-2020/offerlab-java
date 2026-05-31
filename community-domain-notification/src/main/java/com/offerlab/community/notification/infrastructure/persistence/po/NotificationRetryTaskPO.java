package com.offerlab.community.notification.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_notif_retry_task")
public class NotificationRetryTaskPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String dedupKey;
    private String scene;
    private Long receiverUid;
    private Long senderUid;
    private Integer notifType;
    private Integer targetType;
    private Long targetId;
    private String contentJson;
    private Integer taskStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lockOwner;
    private LocalDateTime lockUntil;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
