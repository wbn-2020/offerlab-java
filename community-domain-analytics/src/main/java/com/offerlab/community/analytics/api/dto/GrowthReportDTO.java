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
public class GrowthReportDTO {

    private String period;
    private int days;
    private boolean degraded;
    private List<String> degradationReasons;
    private Long publishedPostCount;
    private Long interactionCount;
    private Long featuredPostCount;
    private Long seriesContributionCount;
    private List<DomainChangeDTO> domainChanges;
    private List<HighlightPostDTO> highlightPosts;
    private List<String> nextActions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DomainChangeDTO {
        private Integer domain;
        private String domainName;
        private Long currentPostCount;
        private Long previousPostCount;
        private String trend;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HighlightPostDTO {
        private Long postId;
        private String title;
        private Integer domain;
        private String domainName;
        private Long interactionCount;
        private boolean featured;
    }
}
