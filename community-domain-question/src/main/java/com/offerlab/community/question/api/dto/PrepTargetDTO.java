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
public class PrepTargetDTO {
    private Long id;
    private Long uid;
    private String targetType;
    private String targetValue;
    private LocalDateTime createTime;
}
