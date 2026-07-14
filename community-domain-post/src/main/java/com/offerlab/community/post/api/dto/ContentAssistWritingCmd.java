package com.offerlab.community.post.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentAssistWritingCmd {
    private Integer domain;
    private Integer postType;
    @Size(max = 255)
    private String title;
    @NotBlank
    @Size(max = PostContentLimits.MAX_CONTENT_LEN)
    private String content;
    @Size(max = 8)
    private List<@Size(max = 64) String> tagNames;
    private JsonNode assistContext;
    @Size(max = 64)
    private String assistTemplateCode;
}
