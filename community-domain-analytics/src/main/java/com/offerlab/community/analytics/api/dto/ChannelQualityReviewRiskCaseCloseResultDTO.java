package com.offerlab.community.analytics.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelQualityReviewRiskCaseCloseResultDTO {
    @JsonProperty("case")
    private ChannelQualityReviewRiskCaseDTO caseDetail;
    private ChannelQualityReviewRiskCaseCloseSnapshotDTO snapshot;
    private ChannelQualityReviewRiskCaseRetrospectiveDTO retrospective;
    private String milestoneFactVersion;
}
