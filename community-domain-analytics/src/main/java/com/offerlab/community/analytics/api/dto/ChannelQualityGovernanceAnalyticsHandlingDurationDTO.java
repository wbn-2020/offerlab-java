package com.offerlab.community.analytics.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChannelQualityGovernanceAnalyticsHandlingDurationDTO {
    private String availability;
    private Long sampleCount;
    private Long averageSeconds;
    private Long p50Seconds;
    private Long p90Seconds;
    private Long maxSeconds;
    private String reason;
}
