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
public class ChannelQualityReviewRiskCaseActionReferenceDTO {
    private Long id;
    private Long caseId;
    private String referenceType;
    private Integer observedVersion;
    private Instant occurredAt;
    private String summary;
    private Long correctionOfReferenceId;
    private Instant createTime;
}
