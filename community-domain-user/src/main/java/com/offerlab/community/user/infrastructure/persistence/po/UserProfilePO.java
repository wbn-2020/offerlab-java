package com.offerlab.community.user.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_profile")
public class UserProfilePO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String nickname;
    private String avatarUrl;
    private String bio;
    private String intentJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
}
