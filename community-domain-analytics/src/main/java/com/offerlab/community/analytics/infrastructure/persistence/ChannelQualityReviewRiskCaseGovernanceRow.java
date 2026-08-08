package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewRiskCaseGovernanceRow {
    private Long caseId;
    private Long batchId;
    private Integer domain;
    private Integer governanceVersion;
    private Long currentResolutionRevisionId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
