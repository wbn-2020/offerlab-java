package com.offerlab.community.question.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_prep_target")
public class UserPrepTargetPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long uid;
    private String targetType;
    private String targetValue;
    private LocalDateTime createTime;
}
