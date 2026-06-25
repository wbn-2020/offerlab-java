package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentSeriesProgressDTO {
    private Long publishedPostCount;
    private Long totalPostCount;
    private Integer completionRate;
}
