package com.offerlab.community.user.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_privacy_setting")
public class UserPrivacySettingPO {
    @TableId(type = IdType.INPUT)
    private Long userId;
    private String profileVisibility;
    private String intentVisibility;
    private Integer searchable;
    private Integer interactionNotification;
    private Integer systemNotification;
    private Integer likeNotification;
    private Integer commentNotification;
    private Integer followNotification;
    private Integer favoriteNotification;
    private Integer mentionNotification;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
