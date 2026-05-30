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
    @Size(max = 20000)
    private String content;
    @Size(max = 512)
    private String coverUrl;
    private Integer visibility;
    @Size(max = 20000)
    private String extJson;
    private List<Long> tagIds;
    private List<String> tagNames;
}
