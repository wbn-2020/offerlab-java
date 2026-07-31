package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

public final class ProjectionHealthRows {
    private ProjectionHealthRows() {
    }

    @Data
    public static class IssueRow {
        private Long issueId;
        private String issueType;
        private String severity;
        private String subjectType;
        private String subjectId;
        private String summary;
        private LocalDateTime detectedAt;
        private Long relatedPostId;
        private Integer domain;
    }

    @Data
    public static class ReconciliationRunRow {
        private Long runId;
        private String status;
        private Long issueCount;
        private LocalDateTime checkedAt;
    }

    @Data
    public static class DeliveryHealthRow {
        private Long watermark;
        private Long backlogCount;
        private Long failedCount;
        private LocalDateTime oldestBacklogAt;
        private LocalDateTime lastSuccessAt;
        private LocalDateTime lastFailureAt;
    }

    @Data
    public static class RewardInboxDeliveryHealthRow {
        private Long watermark;
        private Long backlogCount;
        private LocalDateTime oldestBacklogAt;
        private LocalDateTime lastSuccessAt;
        private LocalDateTime lastFailureAt;
    }

    @Data
    public static class KnowledgeLifecycleSourceHealthRow {
        private Long issueCount;
        private LocalDateTime oldestIssueAt;
    }

    @Data
    public static class AuditReplayRow {
        private Long operatorUid;
        private String afterJson;
    }

    @Data
    public static class ReconcileRequestRow {
        private Long operatorUid;
        private String projectionType;
        private String requestFingerprint;
        private String requestStatus;
        private String resultJson;
    }
}
