package com.offerlab.community.post.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostReferenceUpdateCmd {
    @NotBlank
    @Size(max = 16)
    private String referenceType;

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    @Size(max = 2048)
    private String url;

    @Size(max = 1000)
    private String note;

    @Size(max = 16)
    private String referenceStatus;

    @Size(max = 500)
    private String brokenReason;

    @NotNull
    @Positive
    private Integer expectedRevision;
}
