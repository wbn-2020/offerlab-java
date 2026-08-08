package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelQualityReviewRiskCaseClosePreviewDTO {
    private Long caseId;
    private Boolean readyToClose;
    private Integer caseVersion;
    private Integer coordinationVersion;
    private Integer governanceVersion;
    private Integer governanceFactVersion;
    private List<ChannelQualityReviewRiskCaseCloseCheckDTO> checks;
}
