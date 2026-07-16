package com.offerlab.community.post.collaboration.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContentMaintenanceTaskCreateCmd {
    @NotNull
    @Min(1)
    @Max(5)
    private Integer domain;
    @NotBlank
    @Size(max = 32)
    private String sourceType;
    @Positive
    private Long sourceRefId;
    @Positive
    private Long sourcePostId;
    @NotNull
    @Positive
    private Long assigneeUid;
    @NotBlank
    @Size(max = 160)
    private String title;
    @NotBlank
    @Size(max = 2000)
    private String detail;
}
