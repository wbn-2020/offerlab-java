package com.offerlab.community.interaction.api.dto;

import com.offerlab.community.interaction.api.enums.FreshnessStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class FreshnessUpdateCmd {
    @NotNull
    private FreshnessStatus status;
    @Positive
    private Long successorPostId;
}
