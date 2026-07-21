package com.offerlab.community.post.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

public final class CommunitySpaceRows {

    private CommunitySpaceRows() {
    }

    @Data
    public static class NeedRow {
        private Long id;
        private String sourceType;
        private Long sourceRefId;
        private String contentFormat;
        private String title;
        private String description;
        private String status;
        private LocalDateTime updateTime;
    }

    @Data
    public static class ContributionRow {
        private Long contributionId;
        private Long seriesId;
        private Long postId;
        private Long contributorUid;
        private String contributionType;
        private String title;
        private LocalDateTime createTime;
    }

    @Data
    public static class CollaborationSeriesRow {
        private Long id;
        private Integer domain;
        private String title;
        private String description;
        private String status;
        private LocalDateTime updateTime;
    }

    @Data
    public static class MaintenanceSummaryRow {
        private Long publicMaintenanceCount;
    }
}
