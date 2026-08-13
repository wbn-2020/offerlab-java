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
public class ChannelQualityReviewRiskCaseRetrospectiveEventDTO {
    private Long id;
    private String eventType;
    private String previousStatus;
    private String status;
    private Long ownerUid;
    private String learningCategory;
    private String findingSummary;
    private String preventionActionSummary;
    private Integer retrospectiveVersion;
    private String note;
    private Instant createTime;
}
