package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ChannelQualityGovernanceAnalyticsHealthSummaryRow {
    private Long dirtyBucketCount;
    private LocalDate oldestDirtyBucket;
    private Long openIssueCount;
}
