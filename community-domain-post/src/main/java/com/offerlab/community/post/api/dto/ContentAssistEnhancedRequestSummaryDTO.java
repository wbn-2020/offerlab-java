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
public class ContentAssistEnhancedRequestSummaryDTO {
    private Long requestId;
    private String requestStatus;
    private String usageStatus;
    private Boolean quotaConsumed;
    private String requestFingerprint;
    private String errorCode;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
