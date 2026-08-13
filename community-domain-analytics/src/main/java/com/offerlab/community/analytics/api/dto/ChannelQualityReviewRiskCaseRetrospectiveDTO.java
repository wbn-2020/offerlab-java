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
public class ChannelQualityReviewRiskCaseRetrospectiveDTO {
    private Long id;
    private Long caseId;
    private Long closeSnapshotId;
    private Integer domain;
    private String status;
    private Long ownerUid;
    private Integer retrospectiveVersion;
    private Boolean legacyBaseline;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createTime;
    private Instant updateTime;
    private Boolean canAssignOwner;
    private Boolean canStart;
    private Boolean canRecordFinding;
    private Boolean canComplete;
}
