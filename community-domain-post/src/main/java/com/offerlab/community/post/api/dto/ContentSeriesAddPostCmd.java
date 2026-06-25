package com.offerlab.community.post.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ContentSeriesAddPostCmd {
    @NotNull
    private Long postId;
    @Min(0)
    private Integer sortOrder;
}
