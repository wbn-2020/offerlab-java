package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewRiskCaseGovernanceV40EventRow {
    private Long id;
    private Long caseId;
    private Long batchId;
    private Long operatorUid;
    private String eventType;
    private String previousStatus;
    private String status;
    private Long ownerUid;
    private Integer observedCoordinationVersion;
    private Integer caseVersion;
    private String note;
    private LocalDateTime createTime;
}
