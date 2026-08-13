package com.offerlab.community.analytics.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChannelQualityGovernanceAnalyticsFunnelStageDTO {
    private String stage;
    private String availability;
    private Long count;
    private String reason;
}
