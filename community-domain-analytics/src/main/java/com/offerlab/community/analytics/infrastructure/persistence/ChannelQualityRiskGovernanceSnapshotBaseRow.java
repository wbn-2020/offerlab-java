package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityRiskGovernanceSnapshotBaseRow {
    private Long caseId;
    private Long batchId;
    private Integer domain;
    private String caseStatus;
    private Integer caseVersion;
    private Integer coordinationVersion;
    private Integer governanceFactVersion;
    private Long currentResolutionRevisionId;
    private String outcomeType;
    private String contentRecoveryState;
    private String residualRiskLevel;
    private Long closeSnapshotId;
    private String snapshotPrimaryRootCause;
    private Integer closedCaseVersion;
    private Integer closedCoordinationVersion;
    private Integer closedGovernanceVersion;
    private Integer payloadSchemaVersion;
    private String snapshotDigest;
    private LocalDateTime closedAt;
    private Long retrospectiveId;
    private String retrospectiveStatus;
    private Integer retrospectiveVersion;
}
