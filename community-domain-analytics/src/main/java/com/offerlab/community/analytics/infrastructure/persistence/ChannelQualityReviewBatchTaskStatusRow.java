package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

@Data
public class ChannelQualityReviewBatchTaskStatusRow {
    private Long batchId;
    private String status;
    private Long taskCount;
}
