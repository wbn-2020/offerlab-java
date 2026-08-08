package com.offerlab.community.analytics.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChannelQualityGovernanceAnalyticsRatesDTO {
    private ChannelQualityGovernanceAnalyticsRateDTO governanceOverdueRate;
    private ChannelQualityGovernanceAnalyticsRateDTO reminderCoverageRate;
    private ChannelQualityGovernanceAnalyticsRateDTO postReminderCompletionRate;
    private ChannelQualityGovernanceAnalyticsRateDTO taskReworkRate;
    private ChannelQualityGovernanceAnalyticsRateDTO batchWithdrawRate;
    private ChannelQualityGovernanceAnalyticsRateDTO riskRecurrenceRate;
}
