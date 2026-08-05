package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelQualityReviewBatchTaskDTO {
    private Long taskId;
    private Long sourcePostId;
    private Long sourceRefId;
    private String title;
    private String postHref;
    private String status;
    private String maintenancePhase;
    private String terminalOutcome;
}
