package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectionReconcileResultDTO {
    private String projectionType;
    private String status;
    private Boolean dryRun;
    private Boolean replayed;
    private String idempotencyKey;
    private String requestFingerprint;
    private Long delegatedRunId;
    private Integer slaMinutes;
    private Integer processedCount;
    private Integer issueCount;
    private Integer changedCount;
    private Integer appliedCount;
    private Integer rejectedCount;
    private Boolean coverageComplete;
    private Long operatorUid;
    private String reason;
    private LocalDateTime completedAt;
}
