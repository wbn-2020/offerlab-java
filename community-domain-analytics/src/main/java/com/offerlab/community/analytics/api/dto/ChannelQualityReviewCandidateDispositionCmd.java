package com.offerlab.community.analytics.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ChannelQualityReviewCandidateDispositionCmd {

    @NotNull
    @Min(1)
    @Max(5)
    private Integer domain;

    @NotNull
    @Positive
    private Long sourcePostId;

    @NotNull
    @Positive
    private Long sourceRefId;

    @NotNull
    @Size(max = 16)
    private String action;

    @Size(max = 32)
    private String reasonCode;

    @Min(1)
    @Max(30)
    private Integer snoozeDays;
}
