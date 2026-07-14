package com.offerlab.community.analytics.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class EffectiveReadSessionStartCmd {

    @NotNull
    @Positive
    private Long postId;
}
