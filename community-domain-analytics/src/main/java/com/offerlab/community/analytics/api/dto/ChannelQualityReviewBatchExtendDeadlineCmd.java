package com.offerlab.community.analytics.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChannelQualityReviewBatchExtendDeadlineCmd {
    @NotNull
    @Min(0)
    private Integer expectedCoordinationVersion;

    @NotNull
    private Integer extendByDays;

    @NotBlank
    @Size(min = 2, max = 500)
    private String note;
}
