package com.offerlab.community.user.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_follow")
public class UserFollowPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long fromUid;
    private Long toUid;
    private LocalDateTime createTime;
    private Integer isDeleted;
}
