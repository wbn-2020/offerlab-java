package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewRiskCaseGovernanceCaseRow {
    private Long id;
    private Long batchId;
    private Integer domain;
    private String triggerType;
    private String status;
    private Long ownerUid;
    private Integer caseVersion;
    private Integer openedCoordinationVersion;
    private Long v41CloseSnapshotId;
    private Integer legacyClosedWithoutSnapshot;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
