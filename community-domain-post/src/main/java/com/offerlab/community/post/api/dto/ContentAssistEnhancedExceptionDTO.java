package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentAssistEnhancedExceptionDTO {
    private Long requestId;
    private Long usageId;
    private String issueType;
    private Boolean recoverable;
    private String requestStatus;
    private String usageStatus;
    private String fingerprintPrefix;
    private String errorCode;
    private String provider;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long estimatedCostMicros;
    private Long ageSeconds;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
