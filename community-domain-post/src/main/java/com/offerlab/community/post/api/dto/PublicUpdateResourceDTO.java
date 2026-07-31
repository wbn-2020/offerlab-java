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
public class PublicUpdateResourceDTO {
    private String sourceType;
    private String sourceId;
    private String title;
    private String canonicalPath;
    private LocalDateTime updatedAt;
    private Long postId;
}
