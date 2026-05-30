package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDraftDTO {
    private Long id;
    private Long uid;
    private Long sourcePostId;
    private Integer postType;
    private String title;
    private String content;
    private String coverUrl;
    private Integer visibility;
    private String extJson;
    private List<Long> tagIds;
    private List<String> tagNames;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
