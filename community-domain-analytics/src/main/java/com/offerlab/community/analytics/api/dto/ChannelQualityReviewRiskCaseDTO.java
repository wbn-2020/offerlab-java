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
public class ChannelQualityReviewRiskCaseDTO {
    private Long id;
    private ChannelQualityReviewRiskCaseBatchSummaryDTO batch;
    private String triggerType;
    private Long riskEventId;
    private String riskCode;
    private String dueState;
    private String status;
    private Long ownerUid;
    private Integer caseVersion;
    private Integer openedCoordinationVersion;
    private Integer coordinationVersion;
    private Instant createTime;
    private Instant updateTime;
    private Boolean canAssignOwner;
    private Boolean canAcknowledge;
    private Boolean canRecordPlan;
    private Boolean canRecordProgress;
    private Boolean canSubmitResolution;
    private Boolean canClose;
}
