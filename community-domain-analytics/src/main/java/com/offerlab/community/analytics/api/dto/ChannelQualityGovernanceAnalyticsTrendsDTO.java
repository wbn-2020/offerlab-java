package com.offerlab.community.analytics.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChannelQualityGovernanceAnalyticsTrendsDTO {
    private String metricCode;
    private String grain;
    private ChannelQualityGovernanceAnalyticsWindowDTO window;
    private Long projectionGeneration;
    private String projectionStatus;
    private List<ChannelQualityGovernanceAnalyticsTrendBucketDTO> buckets;
}
