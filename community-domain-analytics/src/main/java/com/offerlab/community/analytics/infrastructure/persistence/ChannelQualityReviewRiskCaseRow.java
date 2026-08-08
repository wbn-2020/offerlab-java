package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewRiskCaseRow {
    private Long id;
    private Long batchId;
    private Integer domain;
    private String triggerType;
    private Long riskEventId;
    private String riskCode;
    private String dueState;
    private String status;
    private Long ownerUid;
    private Integer caseVersion;
    private Integer openedCoordinationVersion;
    private Long createdByUid;
    private Long v41CloseSnapshotId;
    private Integer legacyClosedWithoutSnapshot;
    private Long activeBatchId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
