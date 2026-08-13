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
    @Size(max = PostContentLimits.MAX_TITLE_LEN)
    private String title;
    @Size(max = PostContentLimits.MAX_CONTENT_LEN)
    private String content;
    @Size(max = PostContentLimits.MAX_COVER_URL_LEN)
    private String coverUrl;
    private Integer visibility;
    private Integer domain;
    private Boolean anonymous;
    @Size(max = PostContentLimits.MAX_EXT_JSON_LEN)
    private String extJson;
    @Size(max = PostContentLimits.MAX_TAG_COUNT)
    private List<Long> tagIds;
    @Size(max = PostContentLimits.MAX_TAG_COUNT)
    private List<@Size(max = PostContentLimits.MAX_TAG_NAME_LEN) String> tagNames;
}
