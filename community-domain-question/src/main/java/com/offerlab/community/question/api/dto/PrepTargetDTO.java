package com.offerlab.community.question.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepTargetDTO {
    private Long id;
    private Long uid;
    private String targetType;
    private String targetValue;
    private LocalDate interviewDate;
    private String priority;
    private String note;
    private LocalDateTime createTime;
}
