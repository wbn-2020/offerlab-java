package com.offerlab.community.question.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_ai_extract_task")
public class AiExtractTaskPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long postId;
    private String taskType;
    private Integer taskStatus;
    private Integer retryCount;
    private Integer questionCount;
    private String provider;
    private Boolean fallbackUsed;
    private Long durationMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long estimatedCostMicros;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
