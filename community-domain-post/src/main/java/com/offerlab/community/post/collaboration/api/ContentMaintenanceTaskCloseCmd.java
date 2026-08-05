package com.offerlab.community.post.collaboration.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContentMaintenanceTaskCloseCmd {
    @NotBlank
    @Size(max = 32)
    private String reasonCode;

    @NotBlank
    @Size(max = 1000)
    private String note;
}
