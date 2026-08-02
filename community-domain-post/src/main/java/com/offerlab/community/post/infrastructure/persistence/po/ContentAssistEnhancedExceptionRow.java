package com.offerlab.community.post.infrastructure.persistence.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContentAssistEnhancedExceptionRow {
    private Long requestId;
    private Long requestUid;
    private Long usageId;
    private Long usageUid;
    private String usageStatus;
    private String usageSourceRef;
    private String requestStatus;
    private String requestFingerprint;
    private String errorCode;
    private String provider;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long estimatedCostMicros;
    private Long ageSeconds;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
