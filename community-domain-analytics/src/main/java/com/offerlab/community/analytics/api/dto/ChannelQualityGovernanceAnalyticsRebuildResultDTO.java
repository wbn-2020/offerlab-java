package com.offerlab.community.analytics.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ChannelQualityGovernanceAnalyticsRebuildResultDTO {
    private String status;
    private Boolean dryRun;
    private Instant scopeFrom;
    private Instant scopeTo;
    private Integer domainCount;
    private Integer metricFamilyCount;
    private Integer limit;
    private Boolean idempotentReplay;
    private Boolean backgroundWorkStarted;
}
