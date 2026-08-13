package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewRiskCaseRetrospectiveRow {
    private Long id;
    private Long caseId;
    private Long closeSnapshotId;
    private Integer domain;
    private String status;
    private Long ownerUid;
    private Integer retrospectiveVersion;
    private Integer legacyBaseline;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
