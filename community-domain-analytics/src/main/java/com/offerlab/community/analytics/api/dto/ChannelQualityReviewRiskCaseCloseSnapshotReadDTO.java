package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelQualityReviewRiskCaseCloseSnapshotReadDTO {
    private Long caseId;
    private Boolean legacyClosedWithoutSnapshot;
    private ChannelQualityReviewRiskCaseCloseSnapshotDTO snapshot;
}
