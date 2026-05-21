package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostCounterDTO {
    private Long postId;
    private Long viewCount;
    private Long likeCount;
    private Long commentCount;
    private Long favoriteCount;
}
