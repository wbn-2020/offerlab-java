package com.offerlab.community.analytics.collaboration.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationFunnelDTO {

    private Integer domain;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private String timezone;
    private String deduplicationPolicy;
    private String emptyDenominatorPolicy;
    private String dataFreshness;
    private Boolean degraded;
    private List<String> degradationReasons;

    private long createdNeedCount;
    private long claimedNeedCount;
    private long submittedNeedCount;
    private long acceptedNeedCount;
    private long rejectedNeedCount;
    private long resubmittedNeedCount;
    private long releasedNeedCount;
    private long reclaimedNeedCount;
    private long activeClaimedNeedCount;
    private long stalledNeedCount;
    private long followedNeedCount;
    private long followedToClaimedNeedCount;
    private long maintenanceTaskCount;
    private long completedMaintenanceTaskCount;

    private BigDecimal claimRate;
    private BigDecimal submitRate;
    private BigDecimal acceptanceRate;
    private BigDecimal rejectionResubmissionRate;
    private BigDecimal releaseRate;
    private BigDecimal reclaimRate;
    private BigDecimal stalledRate;
    private BigDecimal followedToClaimedRate;
    private BigDecimal maintenanceCompletionRate;

    private Long averageCreateToClaimSeconds;
    private Long averageClaimToSubmitSeconds;
    private Long averageSubmitToAcceptSeconds;
}
