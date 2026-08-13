package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewRiskCaseEvidenceEntryRow {
    private Long id;
    private Long caseId;
    private String evidenceType;
    private String assertionType;
    private String subjectType;
    private String subjectRef;
    private String sourceType;
    private String sourceRef;
    private Integer sourceVersion;
    private LocalDateTime observedAt;
    private String summary;
    private String evidenceDigest;
    private Long correctionOfEntryId;
    private Long createdByUid;
    private String commandId;
    private String commandFingerprint;
    private LocalDateTime createTime;
}
