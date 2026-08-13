package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewRiskCaseActionReferenceRow {
    private Long id;
    private Long caseId;
    private String referenceType;
    private String referenceKey;
    private Integer observedVersion;
    private LocalDateTime occurredAt;
    private String summary;
    private String referenceFingerprint;
    private Long correctionOfReferenceId;
    private Long createdByUid;
    private String commandId;
    private String commandFingerprint;
    private LocalDateTime createTime;
}
