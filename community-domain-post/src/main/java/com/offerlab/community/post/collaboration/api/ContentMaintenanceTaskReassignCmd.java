package com.offerlab.community.post.collaboration.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContentMaintenanceTaskReassignCmd {
    @NotNull
    @Positive
    private Long replacementUid;

    @NotBlank
    @Size(max = 500)
    private String reason;
}
