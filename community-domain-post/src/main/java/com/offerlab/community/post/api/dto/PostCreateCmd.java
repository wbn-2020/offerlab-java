package com.offerlab.community.post.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostCreateCmd {
    @NotNull
    private Long authorId;
    @NotNull
    private Integer postType;
    @NotBlank
    @Size(max = 255)
    private String title;
    @NotBlank
    private String content;
    private String coverUrl;
    private Integer visibility;
    /** 扩展字段 JSON：公司/岗位/年限/结果 等 */
    private String extJson;
}
