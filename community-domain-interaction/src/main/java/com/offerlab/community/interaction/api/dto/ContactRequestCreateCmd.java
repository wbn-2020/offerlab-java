package com.offerlab.community.interaction.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContactRequestCreateCmd {
    @NotNull
    private Long receiverUid;
    @NotBlank
    @Size(max = 32)
    private String sourceType;
    private Long sourceId;
    @NotBlank
    @Size(max = 32)
    private String scene;
    @NotBlank
    @Size(min = 20, max = 500)
    private String message;
}
