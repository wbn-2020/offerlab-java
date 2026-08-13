package com.offerlab.community.post.infrastructure.persistence.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContentAssistEnhancedRequestPO {
    private Long id;
    private Long uid;
    private String consumerCode;
    private String idempotencyKey;
    private String requestFingerprint;
    private String contentHash;
    private Integer contentLength;
    private Long usageId;
    private String requestStatus;
    private String provider;
    private String resultJson;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long estimatedCostMicros;
    private String errorCode;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
