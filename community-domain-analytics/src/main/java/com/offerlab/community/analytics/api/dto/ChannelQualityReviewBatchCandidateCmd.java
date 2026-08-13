package com.offerlab.community.analytics.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ChannelQualityReviewBatchCandidateCmd {

    @NotNull
    @Positive
    private Long sourcePostId;

    @NotNull
    @Positive
    private Long sourceRefId;
}
