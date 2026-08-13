package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewRiskCaseCloseCheckRow {
    private Long id;
    private Long snapshotId;
    private Long caseId;
    private String providerCode;
    private Integer providerVersion;
    private String requirementLevel;
    private String result;
    private String reasonCode;
    private String summary;
    private LocalDateTime createTime;
}
