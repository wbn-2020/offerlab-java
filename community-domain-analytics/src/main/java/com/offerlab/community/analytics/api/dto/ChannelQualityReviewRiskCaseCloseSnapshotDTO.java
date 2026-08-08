package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelQualityReviewRiskCaseCloseSnapshotDTO {
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
    private String snapshotDigest;
    private List<ChannelQualityReviewRiskCaseResolutionRevisionDTO.RootCauseDTO> rootCauses;
    private List<ChannelQualityReviewRiskCaseActionReferenceDTO> actionReferences;
    private List<ChannelQualityReviewRiskCaseEvidenceEntryDTO> evidenceEntries;
    private List<ChannelQualityReviewRiskCaseCloseCheckDTO> checks;
    private Instant createTime;
}
