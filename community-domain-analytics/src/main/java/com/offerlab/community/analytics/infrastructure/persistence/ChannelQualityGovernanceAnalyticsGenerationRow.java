package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityGovernanceAnalyticsGenerationRow {
    private Long id;
    private String status;
    private LocalDateTime scopeFrom;
    private LocalDateTime scopeTo;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String validationDigest;
    private Integer isActive;
}
