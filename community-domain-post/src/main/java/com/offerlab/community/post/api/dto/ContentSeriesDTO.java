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
public class ContentSeriesDTO {
    private Long id;
    private Long creatorUid;
    private String title;
    private String description;
    private Integer domain;
    private String coverUrl;
    private ContentSeriesProgressDTO progress;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
