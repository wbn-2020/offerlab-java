package com.offerlab.community.interaction.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostOutcomeReviewCmd {
    @NotNull
    private Boolean approved;
    @Size(max = 1000)
    private String note;
}
