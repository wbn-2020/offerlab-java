package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewRiskCaseResolutionRevisionRow {
    private Long id;
    private Long caseId;
    private Integer revisionNo;
    private String outcomeType;
    private String contentRecoveryState;
    private String recoveryScope;
    private String residualRiskLevel;
    private String summary;
    private Long createdByUid;
    private String commandId;
    private String commandFingerprint;
    private LocalDateTime createTime;
}
