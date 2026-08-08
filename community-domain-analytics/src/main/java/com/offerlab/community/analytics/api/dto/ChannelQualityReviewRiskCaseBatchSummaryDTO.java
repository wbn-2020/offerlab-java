package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelQualityReviewRiskCaseBatchSummaryDTO {
    private Long batchId;
    private Integer domain;
    private String name;
    private String priority;
    private Instant dueAt;
    private Instant effectiveDueAt;
    private Integer activeTaskCount;
    private String dueState;
}
