package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelQualityReviewBatchDTO {
    private Long id;
    private Integer domain;
    private String sourceType;
    private String name;
    private Long assigneeUid;
    private Long createdByUid;
    private String priority;
    private Instant dueAt;
    private Integer candidateCount;
    private String progressState;
    private String dueState;
    private Map<String, Integer> taskStatusCounts;
    private Instant createTime;
}
