package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityGovernanceTodoCheckpointRow {
    private Long caseId;
    private Integer domain;
    private Long lastSourceFactId;
    private LocalDateTime lastSourceOccurredAt;
    private String sourceContractVersion;
    private String projectionVersion;
    private String healthStatus;
    private Integer checkpointVersion;
    private LocalDateTime updateTime;
}
