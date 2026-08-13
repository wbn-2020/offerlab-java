package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelQualityReviewRiskCaseCloseCheckDTO {
    private String providerCode;
    private Integer providerVersion;
    private String requirementLevel;
    private String result;
    private String reasonCode;
    private String summary;
}
