package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewBatchCoordinationTaskRow {
    private Long taskId;
    private String title;
    private String status;
    private String latestAttemptDecision;
    private Long assigneeUid;
    private LocalDateTime dueAt;
    private LocalDateTime updateTime;
}
