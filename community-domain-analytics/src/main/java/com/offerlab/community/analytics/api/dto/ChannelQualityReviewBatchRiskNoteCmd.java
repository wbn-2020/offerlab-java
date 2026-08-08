package com.offerlab.community.analytics.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChannelQualityReviewBatchRiskNoteCmd {
    @NotNull
    @Min(0)
    private Integer expectedCoordinationVersion;

    @NotBlank
    @Size(max = 32)
    private String riskCode;

    @NotBlank
    @Size(min = 2, max = 500)
    private String note;
}
