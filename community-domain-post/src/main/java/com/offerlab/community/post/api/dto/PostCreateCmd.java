package com.offerlab.community.post.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class PostCreateCmd {
    private Long postId;
    @NotNull
    private Long authorId;
    @NotNull
    private Integer postType;
    @NotBlank
    @Size(max = PostContentLimits.MAX_TITLE_LEN)
    private String title;
    @NotBlank
    @Size(max = PostContentLimits.MAX_CONTENT_LEN)
    private String content;
    @Size(max = PostContentLimits.MAX_COVER_URL_LEN)
    private String coverUrl;
    private Integer visibility;
    /** 扩展字段 JSON：公司/岗位/年限/结果 等 */
    @Size(max = PostContentLimits.MAX_EXT_JSON_LEN)
    private String extJson;
    @NotNull(message = "请选择频道")
    private Integer domain;
    private Boolean anonymous;
    @Size(max = PostContentLimits.MAX_TAG_COUNT)
    private List<Long> tagIds;
    @Size(max = PostContentLimits.MAX_TAG_COUNT)
    private List<@Size(max = PostContentLimits.MAX_TAG_NAME_LEN) String> tagNames;
    private Boolean reviewRequired;
    private Boolean keywordReviewRequired;
}
