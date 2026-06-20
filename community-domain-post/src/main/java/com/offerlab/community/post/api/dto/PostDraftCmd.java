package com.offerlab.community.post.api.dto;

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
public class PostDraftCmd {
    private Long id;
    private Long uid;
    private Long sourcePostId;
    private Integer postType;
    @Size(max = 255)
    private String title;
    @Size(max = PostContentLimits.MAX_CONTENT_LEN)
    private String content;
    @Size(max = 512)
    private String coverUrl;
    private Integer visibility;
    private Integer domain;
    private Boolean anonymous;
    @Size(max = PostContentLimits.MAX_EXT_JSON_LEN)
    private String extJson;
    private List<Long> tagIds;
    private List<String> tagNames;
}
