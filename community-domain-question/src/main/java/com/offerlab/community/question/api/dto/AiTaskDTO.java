package com.offerlab.community.question.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskDTO {
    private Long id;
    private Long postId;
    private String taskType;
    private Integer taskStatus;
    private Integer retryCount;
    private Integer questionCount;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
