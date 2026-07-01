package com.offerlab.community.post.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContentSeriesCreateCmd {
    @NotBlank
    @Size(max = 120)
    private String title;
    @Size(max = 1000)
    private String description;
    @NotNull
    private Integer domain;
    @Size(max = 512)
    private String coverUrl;
    private Integer visibility;
}
