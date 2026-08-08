package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

@Data
public class ChannelQualityGovernanceAnalyticsHandlingDurationRow {
    private Long sampleCount;
    private Long averageSeconds;
    private Long p50Seconds;
    private Long p90Seconds;
    private Long maxSeconds;
}
