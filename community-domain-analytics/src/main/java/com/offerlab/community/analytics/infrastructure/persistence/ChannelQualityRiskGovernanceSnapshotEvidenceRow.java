package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityRiskGovernanceSnapshotEvidenceRow {
    private Long id;
    private Long caseId;
    private String evidenceType;
    private String assertionType;
    private String subjectType;
    private String sourceType;
    private Integer sourceVersion;
    private LocalDateTime observedAt;
    private String summary;
}
