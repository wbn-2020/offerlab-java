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
public class ChannelQualityReviewRiskCaseEvidenceEntryDTO {
    private Long id;
    private Long caseId;
    private String evidenceType;
    private String assertionType;
    private String subjectType;
    private String sourceType;
    private Integer sourceVersion;
    private Instant observedAt;
    private String summary;
    private Long correctionOfEntryId;
    private Instant createTime;
}
