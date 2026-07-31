package com.offerlab.community.interaction.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContactRequestReportCmd {
    @NotBlank
    @Size(max = 64)
    private String reason;

    @Size(max = 1000)
    private String detail;
}
