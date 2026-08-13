package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityRiskGovernanceMilestoneFactRow {
    private Long sourceFactId;
    private Long caseId;
    private Integer domain;
    private Integer caseVersion;
    private String factType;
    private LocalDateTime occurredAt;
    private Long ownerUid;
    private String responsibilityScope;
    private Integer responsibilityEpoch;
    private String caseStatusAfter;
    private String requiredAction;
    private Long retrospectiveId;
    private String contractVersion;
}
