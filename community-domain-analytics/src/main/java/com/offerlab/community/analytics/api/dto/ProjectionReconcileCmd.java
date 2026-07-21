package com.offerlab.community.analytics.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectionReconcileCmd {
    @NotNull
    private Boolean dryRun;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer limit;

    @NotBlank
    @Size(max = 500)
    private String reason;

    @NotBlank
    @Size(max = 96)
    private String idempotencyKey;
}
