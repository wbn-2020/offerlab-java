package com.offerlab.community.post.collaboration.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

public final class NeedDeliveryCandidateRows {

    private NeedDeliveryCandidateRows() {
    }

    @Data
    public static class NeedContextRow {
        private Long id;
        private Long claimedByUid;
        private Integer domain;
        private String contentFormat;
        private String status;
    }

    @Data
    public static class CandidateRow {
        private Long id;
        private String resolutionType;
        private String title;
        private Integer domain;
        private Integer postType;
        private String publicPath;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }
}
