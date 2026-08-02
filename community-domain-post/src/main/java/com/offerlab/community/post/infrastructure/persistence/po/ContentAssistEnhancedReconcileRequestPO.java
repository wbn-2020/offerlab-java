package com.offerlab.community.post.infrastructure.persistence.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContentAssistEnhancedReconcileRequestPO {
    private Long id;
    private Long operatorUid;
    private String idempotencyKey;
    private String requestFingerprint;
    private String requestStatus;
    private String resultJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
