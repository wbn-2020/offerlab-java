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
public class ChannelQualityReviewRiskCaseQueueItemDTO {
    private Long batchId;
    private Integer domain;
    private String batchName;
    private String priority;
    private Instant dueAt;
    private Instant effectiveDueAt;
    private Integer activeTaskCount;
    private String dueState;
    private String triggerType;
    private Long riskEventId;
    private String riskCode;
    private String triggerSummary;
    private Long caseId;
    private String caseStatus;
    private Long coordinationOwnerUid;
    private Integer coordinationVersion;
    private Instant updateTime;
}
