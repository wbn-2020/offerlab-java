package com.offerlab.community.post.collaboration.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

public final class NeedDiscoveryRows {

    private NeedDiscoveryRows() {
    }

    @Data
    public static class NeedRow {
        private Long id;
        private Long creatorUid;
        private Integer domain;
        private String sourceType;
        private Long sourceRefId;
        private String contentFormat;
        private String title;
        private String description;
        private String acceptanceCriteria;
        private String status;
        private Long claimedByUid;
        private LocalDateTime claimedAt;
        private LocalDateTime lastProgressAt;
        private Integer stalled;
        private Long mergedIntoNeedId;
        private String resolutionType;
        private Long resolutionId;
        private Long resolutionPostId;
        private Integer followerCount;
        private Integer followed;
        private Integer viewerDomainMatch;
        private Integer viewerFormatMatch;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }
}
