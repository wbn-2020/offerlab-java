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
public class ChannelQualityGovernancePlaybookDTO {
    private Long id;
    private String playbookCode;
    private Integer latestVersionNo;
    private Integer playbookVersion;
    private Instant createTime;
    private Instant updateTime;
    private List<VersionDTO> versions;
    private Boolean canCreateVersion;
    private Boolean canPublish;
    private Boolean canRetire;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersionDTO {
        private Long id;
        private Long playbookId;
        private Integer versionNo;
        private String status;
        private String contentSchemaVersion;
        private String contentSummary;
        private String canonicalContentJson;
        private String contentHash;
        private Instant publishedAt;
        private Instant retiredAt;
        private Long createdByUid;
        private Instant createTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationDTO {
        private Long playbookId;
        private Long playbookVersionId;
        private Integer versionNo;
        private String contentSummary;
        private String contentHash;
        private String applicabilityResult;
        private List<String> reasonCodes;
        private Integer rank;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationPageDTO {
        private Long caseId;
        private Integer caseVersion;
        private Integer governanceFactVersion;
        private String governanceSnapshotEtag;
        private String trendContextState;
        private List<RecommendationDTO> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CasePlaybookDTO {
        private Long id;
        private Long caseId;
        private Long playbookId;
        private Long playbookVersionId;
        private String sourceContentHash;
        private String snapshotSummary;
        private String snapshotContentJson;
        private String snapshotHash;
        private String applicabilityResult;
        private String applicabilityReasonCodesJson;
        private Integer observedCaseVersion;
        private Integer observedGovernanceFactVersion;
        private String observedGovernanceSnapshotEtag;
        private String bindingStatus;
        private String completionEvaluationHash;
        private Integer bindingVersion;
        private Instant createTime;
        private Instant updateTime;
        private List<CheckDTO> checks;
        private Boolean canAccept;
        private Boolean canVerifyCheck;
        private Boolean canWaiveCheck;
        private Boolean canComplete;
        private Boolean canWaive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckDTO {
        private Long id;
        private Long casePlaybookId;
        private String checkKey;
        private String title;
        private String instruction;
        private String evidenceRequirement;
        private String state;
        private Integer observedFactVersion;
        private String observedSnapshotEtag;
        private Long verifiedByUid;
        private String verificationNote;
        private String waiverReason;
        private Integer checkVersion;
        private Instant createTime;
        private Instant updateTime;
    }
}
