package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityRiskGovernanceSnapshotRecurrenceRow {
    private Long id;
    private Long currentCaseId;
    private Long previousCaseId;
    private String relationType;
    private String rootCauseCategory;
    private LocalDateTime linkedAt;
    private String commandFingerprint;
}
