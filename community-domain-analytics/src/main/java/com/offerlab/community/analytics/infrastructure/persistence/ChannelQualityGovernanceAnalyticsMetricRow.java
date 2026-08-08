package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ChannelQualityGovernanceAnalyticsMetricRow {
    private String metricCode;
    private LocalDate bucketDate;
    private Integer domain;
    private String dimensionType;
    private String dimensionValue;
    private Long numerator;
    private Long denominator;
    private Long sampleCount;
    private Long valueSumSeconds;
    private String availabilityStatus;
    private String definitionVersion;
}
