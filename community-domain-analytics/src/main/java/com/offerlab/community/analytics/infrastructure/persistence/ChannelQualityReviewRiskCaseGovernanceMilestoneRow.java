package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewRiskCaseGovernanceMilestoneRow {
    private Long id;
    private Long caseId;
    private Long batchId;
    private Integer domain;
    private String milestoneCode;
    private LocalDateTime occurredAt;
    private String sourceType;
    private Long sourceId;
    private Long ownerUid;
    private String responsibilityScope;
    private Integer responsibilityEpoch;
    private String caseStatusAfter;
    private String requiredAction;
    private Long retrospectiveId;
    private String contractVersion;
    private Integer factVersion;
    private LocalDateTime createTime;
}
