package com.offerlab.community.analytics.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChannelQualityGovernanceAnalyticsOverviewDTO {
    private ChannelQualityGovernanceAnalyticsWindowDTO window;
    private String metricDefinitionVersion;
    private Long projectionGeneration;
    private String projectionStatus;
    private List<ChannelQualityGovernanceAnalyticsSourceWatermarkDTO> sourceWatermarks;
    private List<ChannelQualityGovernanceAnalyticsFunnelStageDTO> funnel;
    private ChannelQualityGovernanceAnalyticsHandlingDurationDTO handlingDuration;
    private ChannelQualityGovernanceAnalyticsRatesDTO rates;
}
