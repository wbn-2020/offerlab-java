package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewBatchCoordinationRow {
    private Long id;
    private Integer domain;
    private String sourceType;
    private String name;
    private Long assigneeUid;
    private LocalDateTime dueAt;
    private LocalDateTime effectiveDueAt;
    private Integer coordinationVersion;
    private Integer candidateCount;
    private LocalDateTime createTime;
}
