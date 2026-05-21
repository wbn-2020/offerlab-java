package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDTO {
    private Long id;
    private Long authorId;
    private Integer postType;
    private String title;
    private String content;
    private String coverUrl;
    private Integer visibility;
    private Integer postStatus;
    private String extJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
