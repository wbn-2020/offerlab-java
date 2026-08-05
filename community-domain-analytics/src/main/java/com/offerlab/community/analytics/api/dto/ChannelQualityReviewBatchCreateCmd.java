package com.offerlab.community.analytics.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ChannelQualityReviewBatchCreateCmd {

    @NotNull
    @Min(1)
    @Max(5)
    private Integer domain;

    @NotBlank
    @Size(min = 2, max = 120)
    private String name;

    @NotNull
    @Positive
    private Long assigneeUid;

    @NotBlank
    @Size(max = 16)
    private String priority;

    @NotNull
    private Integer dueInDays;

    @NotNull
    @Size(min = 1, max = 20)
    private List<@Valid ChannelQualityReviewBatchCandidateCmd> candidates;
}
