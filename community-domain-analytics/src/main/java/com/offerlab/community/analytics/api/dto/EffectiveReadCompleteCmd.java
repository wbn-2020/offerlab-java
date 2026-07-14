package com.offerlab.community.analytics.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EffectiveReadCompleteCmd {

    @NotBlank
    @Size(min = 32, max = 32)
    @Pattern(regexp = "^[0-9a-f]{32}$")
    private String sessionToken;
}
