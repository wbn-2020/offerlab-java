package com.offerlab.community.analytics.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class ChannelQualityGovernanceAnalyticsProjectionHealthDTO {
    private String projectionStatus;
    private Long activeGeneration;
    private Long latestCompletedGeneration;
    private Instant latestCompletedAt;
    private List<ChannelQualityGovernanceAnalyticsSourceWatermarkDTO> sourceWatermarks;
    private Long dirtyBucketCount;
    private LocalDate oldestDirtyBucket;
    private Long openIssueCount;
    private Boolean issueCountCapped;
    private String v42CapabilityStatus;
    private Boolean rebuildSupported;
}
