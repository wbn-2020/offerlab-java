package com.offerlab.community.user.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_user_task_state")
public class UserTaskStatePO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long uid;
    private String taskType;
    private String taskCode;
    private LocalDate taskDate;
    private Integer completed;
    private String completeSource;
    private Long completeRefId;
    private LocalDateTime firstCompletedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
