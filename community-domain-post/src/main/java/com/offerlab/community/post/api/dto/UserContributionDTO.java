package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContributionDTO {
    private Long uid;
    private Integer score;
    private String level;
    private String badge;
    private Long postCount;
    private Long featuredCount;
    private Long likeCount;
    private Long favoriteCount;
    private Long commentCount;
    private Long viewCount;
    private String source;
    private Boolean estimated;
    private Boolean profileVisible;
}
