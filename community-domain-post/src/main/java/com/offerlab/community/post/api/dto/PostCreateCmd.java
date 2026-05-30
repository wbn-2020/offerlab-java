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
    @NotNull
    private Long authorId;
    @NotNull
    private Integer postType;
    @NotBlank
    @Size(max = 255)
    private String title;
    @NotBlank
    @Size(max = 20000)
    private String content;
    @Size(max = 512)
    private String coverUrl;
    private Integer visibility;
    /** 扩展字段 JSON：公司/岗位/年限/结果 等 */
    @Size(max = 20000)
    private String extJson;
    private List<Long> tagIds;
    private List<String> tagNames;
    private Boolean reviewRequired;
}
