package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewRiskCaseRetrospectiveEventRow {
    private Long id;
    private Long retrospectiveId;
    private Long caseId;
    private Long operatorUid;
    private String eventType;
    private String previousStatus;
    private String status;
    private Long ownerUid;
    private String learningCategory;
    private String findingSummary;
    private String preventionActionSummary;
    private Integer retrospectiveVersion;
    private String commandId;
    private String commandFingerprint;
    private String note;
    private LocalDateTime createTime;
}
