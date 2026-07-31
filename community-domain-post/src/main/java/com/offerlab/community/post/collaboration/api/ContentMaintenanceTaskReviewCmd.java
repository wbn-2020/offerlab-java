package com.offerlab.community.post.collaboration.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContentMaintenanceTaskReviewCmd {
    @NotBlank
    @Size(max = 24)
    private String decision;
    @NotBlank
    @Size(max = 1000)
    private String note;
}
