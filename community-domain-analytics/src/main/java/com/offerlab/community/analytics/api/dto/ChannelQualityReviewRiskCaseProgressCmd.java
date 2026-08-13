package com.offerlab.community.analytics.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChannelQualityReviewRiskCaseProgressCmd {
    @NotNull
    @Min(0)
    private Integer expectedCaseVersion;

    @NotBlank
    @Size(min = 2, max = 500)
    private String note;
}
