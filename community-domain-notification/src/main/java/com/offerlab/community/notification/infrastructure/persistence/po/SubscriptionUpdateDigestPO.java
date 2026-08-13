package com.offerlab.community.notification.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_subscription_update_digest")
public class SubscriptionUpdateDigestPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long receiverUid;
    private String sourceType;
    private Long sourceId;
    private String resourceType;
    private Long resourceId;
    private String eventType;
    private String eventKey;
    private Long actorUid;
    private String payloadJson;
    private LocalDateTime occurredAt;
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
