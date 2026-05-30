package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostUpdateCmd {
    private Long postId;
    private Long operatorUid;
    private String title;
    private String content;
    private String coverUrl;
    private Integer visibility;
    private String extJson;
    private List<Long> tagIds;
    private List<String> tagNames;
    private Boolean reviewRequired;
}
