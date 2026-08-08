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
public class ChannelQualityReviewRiskCaseGovernanceMilestoneDTO {
    private Long sourceFactId;
    private String milestoneCode;
    private Instant occurredAt;
    private String sourceType;
    private String responsibilityScope;
    private Integer responsibilityEpoch;
    private Long ownerUid;
    private String caseStatusAfter;
    private String requiredAction;
    private String contractVersion;
    private Integer factVersion;
}
