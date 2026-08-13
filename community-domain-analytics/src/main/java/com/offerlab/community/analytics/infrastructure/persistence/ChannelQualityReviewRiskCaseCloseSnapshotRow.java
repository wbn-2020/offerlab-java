package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewRiskCaseCloseSnapshotRow {
    private Long id;
    private Long caseId;
    private Long batchId;
    private Integer domain;
    private Long resolutionRevisionId;
    private Integer closedCaseVersion;
    private Integer closedCoordinationVersion;
    private Integer closedGovernanceVersion;
    private String outcomeType;
    private String contentRecoveryState;
    private String primaryRootCause;
    private String residualRiskLevel;
    private Integer actionReferenceCount;
    private Integer evidenceCount;
    private Integer payloadSchemaVersion;
    private String canonicalPayload;
    private String snapshotDigest;
    private Long closedByUid;
    private String commandId;
    private String commandFingerprint;
    private LocalDateTime createTime;
}
