package com.offerlab.community.post.collaboration.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentMaintenanceTaskDTO {
    private Long id;
    private Integer domain;
    private String sourceType;
    private Long sourceRefId;
    private Long sourcePostId;
    private Long createdByUid;
    private Long assigneeUid;
    private String title;
    private String detail;
    private String status;
    private String deliveryType;
    private Long deliveryRefId;
    private Long deliveryPostId;
    private String deliveryNote;
    private String reviewNote;
    private Long dispatchBatchId;
    private String priority;
    private LocalDateTime dueAt;
    private Integer currentAttemptNo;
    private String maintenancePhase;
    private String terminalOutcomeCode;
    private String closeReasonCode;
    private Boolean canClaim;
    private Boolean canSubmit;
    private Boolean canReview;
    private Boolean canClose;
    private Boolean canReassign;
    private LocalDateTime claimedAt;
    private LocalDateTime submittedAt;
    private Long reviewedByUid;
    private LocalDateTime reviewedAt;
    private Long closedByUid;
    private LocalDateTime closedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
