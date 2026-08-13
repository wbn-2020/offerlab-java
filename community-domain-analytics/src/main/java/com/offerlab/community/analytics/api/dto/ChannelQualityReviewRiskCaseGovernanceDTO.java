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
public class ChannelQualityReviewRiskCaseGovernanceDTO {
    private Long caseId;
    private Long batchId;
    private Integer domain;
    private String caseStatus;
    private Integer caseVersion;
    private Integer coordinationVersion;
    private Integer governanceVersion;
    private Integer governanceFactVersion;
    private String governanceSnapshotEtag;
    private ResolutionRevisionSummaryDTO currentResolutionRevision;
    private Integer actionReferenceCount;
    private Integer evidenceCount;
    private Long closeSnapshotId;
    private Boolean legacyClosedWithoutSnapshot;
    private RetrospectiveSummaryDTO retrospective;
    private String milestoneFactVersion;
    private Boolean canAddResolutionRevision;
    private Boolean canAddActionReference;
    private Boolean canAddEvidence;
    private Boolean canPreviewClose;
    private Boolean canClose;
    private Boolean canInitializeRetrospective;
    private Boolean canManageRetrospective;
    private Boolean canLinkRecurrence;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolutionRevisionSummaryDTO {
        private Long id;
        private Integer revisionNo;
        private String outcomeType;
        private String contentRecoveryState;
        private String residualRiskLevel;
        private String summary;
        private Instant createTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrospectiveSummaryDTO {
        private Long id;
        private String status;
        private Long ownerUid;
        private Integer retrospectiveVersion;
        private Boolean legacyBaseline;
        private Instant updateTime;
    }
}
