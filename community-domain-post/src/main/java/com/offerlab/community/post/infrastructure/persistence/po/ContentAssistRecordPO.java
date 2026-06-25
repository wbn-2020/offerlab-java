package com.offerlab.community.post.infrastructure.persistence.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContentAssistRecordPO {
    private Long id;
    private Long uid;
    private String scene;
    private String provider;
    private String assistStatus;
    private Integer domain;
    private Integer contentLength;
    private String contentHash;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long estimatedCostMicros;
    private String errorCode;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
