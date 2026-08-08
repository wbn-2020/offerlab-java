package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelQualityReviewRiskCaseResolutionRevisionWriteResultDTO {
    private ChannelQualityReviewRiskCaseGovernanceDTO governance;
    private ChannelQualityReviewRiskCaseResolutionRevisionDTO resolutionRevision;
}
