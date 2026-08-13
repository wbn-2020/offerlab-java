package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

@Data
public class ChannelQualityGovernanceAnalyticsFunnelRow {
    private Long openedCount;
    private Long acknowledgedCount;
    private Long plannedCount;
    private Long resolutionSubmittedCount;
    private Long governanceClosedCount;
    private Long recoveryVerifiedCount;
}
