package com.offerlab.community.post.reference.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

public final class PostReferenceRows {

    private PostReferenceRows() {
    }

    @Data
    public static class PostAccessRow {
        private Long id;
        private Long authorId;
        private Integer visibility;
        private Integer postStatus;
        private Integer isDeleted;
    }

    @Data
    public static class ReferenceRow {
        private Long id;
        private Long postId;
        private Long ownerUid;
        private String referenceType;
        private String title;
        private String url;
        private String normalizedUrl;
        private String sourceDomain;
        private String note;
        private String brokenReason;
        private String referenceStatus;
        private Integer sortOrder;
        private Integer revision;
        private LocalDateTime lastConfirmedAt;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Integer isDeleted;
    }
}
