package com.offerlab.community.analytics.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChannelQualityGovernanceAnalyticsBreakdownDTO {
    private String metricCode;
    private String dimension;
    private ChannelQualityGovernanceAnalyticsWindowDTO window;
    private Long projectionGeneration;
    private String projectionStatus;
    private List<ChannelQualityGovernanceAnalyticsBreakdownItemDTO> items;
}
