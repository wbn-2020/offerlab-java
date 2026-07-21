package com.offerlab.community.post.collaboration.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

public final class CollaborationActionQueryRows {

    private CollaborationActionQueryRows() {
    }

    @Data
    public static class ActionRow {
        private String actionType;
        private String sourceType;
        private Long sourceId;
        private String sourceStatus;
        private String title;
        private String reason;
        private String targetPath;
        private Long lastEventId;
        private LocalDateTime updatedAt;
        private Integer canAct;
    }

    @Data
    public static class ActionCountRow {
        private String actionType;
        private Integer count;
    }
}
