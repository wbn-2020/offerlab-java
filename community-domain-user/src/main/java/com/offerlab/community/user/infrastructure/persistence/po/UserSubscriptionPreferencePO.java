package com.offerlab.community.user.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_subscription_preference")
public class UserSubscriptionPreferencePO {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long uid;
    private String sourceType;
    private Long sourceId;
    private String deliveryMode;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
