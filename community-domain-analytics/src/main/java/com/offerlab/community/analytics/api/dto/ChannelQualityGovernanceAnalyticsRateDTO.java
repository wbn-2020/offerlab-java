package com.offerlab.community.analytics.api.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ChannelQualityGovernanceAnalyticsRateDTO {
    private String availability;
    private BigDecimal value;
    private Long numerator;
    private Long denominator;
    private String reason;
}
