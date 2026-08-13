package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

@Data
public class ChannelQualityReviewBatchTaskRow {
    private Long taskId;
    private Long batchId;
    private Long sourcePostId;
    private Long sourceRefId;
    private String title;
    private String status;
    private String terminalOutcomeCode;
    private String latestAttemptDecision;
}
