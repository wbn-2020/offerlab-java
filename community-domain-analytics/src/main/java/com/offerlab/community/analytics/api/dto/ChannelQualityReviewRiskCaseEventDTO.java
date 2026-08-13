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
public class ChannelQualityReviewRiskCaseEventDTO {
    private Long id;
    private String eventType;
    private String previousStatus;
    private String status;
    private Integer caseVersion;
    private String note;
    private Instant createTime;
}
