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
public class ChannelQualityReviewCandidateDTO {
    private Integer domain;
    private String sourceType;
    private Long sourcePostId;
    private Long sourceRefId;
    private Integer sourcePostType;
    private String title;
    private String detail;
    private String postHref;
    private String reasonCode;
    private String priority;
    private String lifecycleState;
    private String maintenanceStatus;
    private String maintenancePhase;
    private String terminalOutcome;
    private String dispositionReasonCode;
    private Instant snoozedUntil;
    private Boolean actionable;
}
