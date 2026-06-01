package com.offerlab.community.question.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_question_index_retry_task")
public class QuestionIndexRetryTaskPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String dedupKey;
    private Long questionId;
    private String operation;
    private Integer taskStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lockOwner;
    private LocalDateTime lockUntil;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
