package com.offerlab.community.user.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_counter")
public class UserCounterPO {
    @TableId(type = IdType.INPUT)
    private Long userId;
    private Long followerCount;
    private Long followingCount;
    private Long postCount;
    private Long likeReceived;
    private LocalDateTime updateTime;
    @Version
    private Integer version;
}
