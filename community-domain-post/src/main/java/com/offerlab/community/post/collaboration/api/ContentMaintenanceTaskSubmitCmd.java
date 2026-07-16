package com.offerlab.community.post.collaboration.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContentMaintenanceTaskSubmitCmd {
    @NotBlank
    @Size(max = 24)
    private String deliveryType;
    @NotNull
    @Positive
    private Long deliveryRefId;
    @Positive
    private Long deliveryPostId;
    @NotBlank
    @Size(max = 1000)
    private String note;
}
