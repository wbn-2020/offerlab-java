package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityRiskGovernanceSnapshotActionRow {
    private Long id;
    private Long caseId;
    private String referenceType;
    private Integer observedVersion;
    private LocalDateTime occurredAt;
    private String summary;
}
