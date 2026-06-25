package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrowthProfileDTO {

    private int days;
    private boolean degraded;
    private List<String> degradationReasons;
    private String strongestDomain;
    private String emergingDomain;
    private String nextFocus;
    private List<DomainProfileDTO> domains;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DomainProfileDTO {
        private Integer domain;
        private String domainName;
        private Long postCount;
        private Long seriesCount;
        private Long activeDays;
        private Long interactionCount;
        private Long viewCount;
        private List<DimensionDTO> dimensions;
        private List<ReferencePostDTO> representativePosts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DimensionDTO {
        private String key;
        private String label;
        private int score;
        private String explanation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferencePostDTO {
        private Long postId;
        private String title;
        private Integer domain;
        private Long heat;
        private boolean featured;
    }
}
