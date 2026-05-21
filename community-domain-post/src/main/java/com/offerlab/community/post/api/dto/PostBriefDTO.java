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
public class PostBriefDTO {
    private Long id;
    private Long authorId;
    private Integer postType;
    private String title;
    private String summary;
    private String coverUrl;
    private String extJson;
    private LocalDateTime createTime;
}
