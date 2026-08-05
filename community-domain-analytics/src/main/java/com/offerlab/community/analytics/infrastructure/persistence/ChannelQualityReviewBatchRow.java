package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewBatchRow {
    private Long id;
    private Integer domain;
    private String sourceType;
    private String name;
    private Long assigneeUid;
    private Long createdByUid;
    private String priority;
    private LocalDateTime dueAt;
    private Integer candidateCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
