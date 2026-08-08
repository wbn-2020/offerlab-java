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
public class ChannelQualityReviewBatchCoordinationTaskDTO {
    private Long taskId;
    private String title;
    private String status;
    private String maintenancePhase;
    private Long assigneeUid;
    private Instant dueAt;
    private Instant updateTime;
    private Boolean canReassign;
    private Boolean canWithdraw;
}
