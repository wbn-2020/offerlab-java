package com.offerlab.community.post.relationship.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

public final class RelationshipRows {

    private RelationshipRows() {
    }

    @Data
    public static class RelationshipRow {
        private String sourceType;
        private Long sourceId;
        private Long relationId;
        private String title;
        private String summary;
        private String targetPath;
        private String relationStatus;
        private String sourceStatus;
        private LocalDateTime lastPublicUpdateAt;
        private LocalDateTime relationTime;
        private String deliveryMode;
        private LocalDateTime expiresAt;
    }

    @Data
    public static class RelationshipCountRow {
        private String sourceType;
        private String deliveryMode;
        private Long count;
    }
}
