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
public class ChannelQualityReviewRiskCaseRecurrenceLinkDTO {
    private Long id;
    private Long previousCaseId;
    private String relationType;
    private String rootCauseCategory;
    private String note;
    private String linkDigest;
    private Instant createTime;
}
