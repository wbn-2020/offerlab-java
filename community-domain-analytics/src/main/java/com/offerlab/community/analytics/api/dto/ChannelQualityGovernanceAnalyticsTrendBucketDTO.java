package com.offerlab.community.analytics.api.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class ChannelQualityGovernanceAnalyticsTrendBucketDTO {
    private Instant bucketStart;
    private Instant bucketEnd;
    private String availability;
    private BigDecimal value;
    private Long numerator;
    private Long denominator;
    private Long sampleCount;
    private String reason;
    private Long projectionGeneration;
}
