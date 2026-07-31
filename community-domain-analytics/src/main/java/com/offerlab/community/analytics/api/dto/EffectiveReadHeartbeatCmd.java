package com.offerlab.community.analytics.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EffectiveReadHeartbeatCmd {

    @NotBlank
    @Size(min = 32, max = 32)
    @Pattern(regexp = "^[0-9a-f]{32}$")
    private String sessionToken;

    @NotNull
    @Positive
    private Long heartbeatSeq;

    @NotBlank
    @Pattern(regexp = "^(ACTIVE|PAUSED)$")
    private String activityState;

    @NotNull
    @Min(0)
    @Max(100)
    private Integer scrollPercent;
}
