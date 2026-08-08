package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityGovernanceAnalyticsCursorRow {
    private String sourceType;
    private LocalDateTime cursorTime;
    private Long cursorId;
    private LocalDateTime coveredThrough;
    private Long backlogCount;
    private LocalDateTime lastSuccessAt;
    private String lastErrorCode;
    private Long projectionGeneration;
    private Integer cursorVersion;
}
