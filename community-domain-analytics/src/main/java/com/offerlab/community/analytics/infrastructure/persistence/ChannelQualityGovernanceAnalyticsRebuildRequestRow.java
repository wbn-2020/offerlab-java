package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityGovernanceAnalyticsRebuildRequestRow {
    private Long id;
    private Long operatorUid;
    private String idempotencyKey;
    private String requestFingerprint;
    private String status;
    private Integer dryRun;
    private LocalDateTime scopeFrom;
    private LocalDateTime scopeTo;
    private String resultSummary;
}
