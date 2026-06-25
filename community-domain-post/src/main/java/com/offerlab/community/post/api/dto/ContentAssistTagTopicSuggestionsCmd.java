package com.offerlab.community.post.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentAssistTagTopicSuggestionsCmd {
    private Integer domain;
    @Size(max = 255)
    private String title;
    @NotBlank
    @Size(max = PostContentLimits.MAX_CONTENT_LEN)
    private String content;
}
