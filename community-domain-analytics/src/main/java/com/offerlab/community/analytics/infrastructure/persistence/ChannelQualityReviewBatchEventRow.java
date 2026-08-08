package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewBatchEventRow {
    private Long id;
    private Long batchId;
    private String eventType;
    private LocalDateTime previousDueAt;
    private LocalDateTime effectiveDueAt;
    private Long previousAssigneeUid;
    private Long replacementAssigneeUid;
    private String riskCode;
    private String withdrawReasonCode;
    private Integer affectedTaskCount;
    private String note;
    private Integer coordinationVersion;
    private LocalDateTime createTime;
}
