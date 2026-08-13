package com.offerlab.community.analytics.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ChannelQualityGovernanceAnalyticsSourceWatermarkDTO {
    private String sourceType;
    private Boolean available;
    private Instant coveredThrough;
    private Long lagSeconds;
    private Long backlogCount;
    private String status;
}
