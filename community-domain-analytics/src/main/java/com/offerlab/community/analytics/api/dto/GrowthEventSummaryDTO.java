package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrowthEventSummaryDTO {
    private Integer days;
    private Integer activeDomain;
    private Long total;
    private java.util.List<java.util.Map<String, Object>> eventDistribution;
    private java.util.List<java.util.Map<String, Object>> domainDistribution;
    private java.util.List<java.util.Map<String, Object>> dailyTrend;
}
