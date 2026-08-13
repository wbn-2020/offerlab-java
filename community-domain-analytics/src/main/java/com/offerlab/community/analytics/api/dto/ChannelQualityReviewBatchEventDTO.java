package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelQualityReviewBatchEventDTO {
    private Long id;
    private String eventType;
    private Instant previousDueAt;
    private Instant effectiveDueAt;
    private Long previousAssigneeUid;
    private Long replacementAssigneeUid;
    private String riskCode;
    private String withdrawReasonCode;
    private Integer affectedTaskCount;
    private String note;
    private Integer coordinationVersion;
    private Instant createTime;
}
