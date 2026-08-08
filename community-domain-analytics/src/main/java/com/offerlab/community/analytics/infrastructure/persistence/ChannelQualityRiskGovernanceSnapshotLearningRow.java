package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityRiskGovernanceSnapshotLearningRow {
    private Long retrospectiveId;
    private String learningCategory;
    private LocalDateTime occurredAt;
}
