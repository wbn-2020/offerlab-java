package com.offerlab.community.analytics.collaboration.infrastructure.persistence;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class CollaborationAnalyticsRows {

    private CollaborationAnalyticsRows() {
    }

    @Data
    public static class ContributionFactRow {
        private String factType;
        private Long sourceId;
        private String referenceType;
        private Long referenceId;
        private Integer domain;
        private LocalDateTime occurredAt;
    }

    @Data
    public static class FunnelRow {
        private Long createdNeedCount;
        private Long claimedNeedCount;
        private Long submittedNeedCount;
        private Long acceptedNeedCount;
        private Long rejectedNeedCount;
        private Long resubmittedNeedCount;
        private Long releasedNeedCount;
        private Long reclaimedNeedCount;
        private Long activeClaimedNeedCount;
        private Long stalledNeedCount;
        private Long followedNeedCount;
        private Long followedToClaimedNeedCount;
        private Long maintenanceTaskCount;
        private Long completedMaintenanceTaskCount;
        private BigDecimal averageCreateToClaimSeconds;
        private BigDecimal averageClaimToSubmitSeconds;
        private BigDecimal averageSubmitToAcceptSeconds;
    }
}
