package com.offerlab.community.post.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PostReferenceReorderItemCmd {
    @NotNull
    @Positive
    private Long referenceId;

    @NotNull
    @Positive
    private Integer expectedRevision;
}
