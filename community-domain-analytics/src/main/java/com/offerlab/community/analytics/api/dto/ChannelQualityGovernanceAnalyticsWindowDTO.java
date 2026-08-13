package com.offerlab.community.analytics.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ChannelQualityGovernanceAnalyticsWindowDTO {
    private Instant from;
    private Instant to;
    private Instant asOf;
    private String timezone;
}
