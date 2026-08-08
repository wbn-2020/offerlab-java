package com.offerlab.community.analytics.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;

public final class ChannelQualityReviewRiskCaseGovernanceCommands {

    private ChannelQualityReviewRiskCaseGovernanceCommands() {
    }

    @Data
    public static class ResolutionRevisionCmd {
        @NotNull @Min(0) private Integer expectedCaseVersion;
        @NotNull @Min(0) private Integer expectedGovernanceVersion;
        @NotBlank @Size(min = 1, max = 64) private String commandId;
        @NotBlank @Size(max = 40) private String outcomeType;
        @NotBlank @Size(max = 24) private String contentRecoveryState;
        @NotBlank @Size(min = 2, max = 500) private String recoveryScope;
        @NotBlank @Size(max = 16) private String residualRiskLevel;
        @NotBlank @Size(min = 2, max = 1000) private String summary;
        @NotEmpty @Size(max = 6) private List<@Valid RootCauseCmd> rootCauses;
    }

    @Data
    public static class RootCauseCmd {
        @NotBlank @Size(max = 16) private String role;
        @NotBlank @Size(max = 40) private String category;
        @NotBlank @Size(min = 2, max = 500) private String note;
    }

    @Data
    public static class ActionReferenceCmd {
        @NotNull @Min(0) private Integer expectedCaseVersion;
        @NotNull @Min(0) private Integer expectedGovernanceVersion;
        @NotBlank @Size(min = 1, max = 64) private String commandId;
        @NotBlank @Size(max = 40) private String referenceType;
        @NotBlank @Size(min = 1, max = 160) private String referenceKey;
        @Min(0) private Integer observedVersion;
        @NotNull private Instant occurredAt;
        @NotBlank @Size(min = 2, max = 500) private String summary;
        @Min(1) private Long correctionOfReferenceId;
    }

    @Data
    public static class EvidenceEntryCmd {
        @NotNull @Min(0) private Integer expectedCaseVersion;
        @NotNull @Min(0) private Integer expectedGovernanceVersion;
        @NotBlank @Size(min = 1, max = 64) private String commandId;
        @NotBlank @Size(max = 40) private String evidenceType;
        @NotBlank @Size(max = 40) private String assertionType;
        @NotBlank @Size(min = 1, max = 40) private String subjectType;
        @NotBlank @Size(min = 1, max = 160) private String subjectRef;
        @NotBlank @Size(min = 1, max = 40) private String sourceType;
        @NotBlank @Size(min = 1, max = 160) private String sourceRef;
        @Min(0) private Integer sourceVersion;
        @NotNull private Instant observedAt;
        @NotBlank @Size(min = 2, max = 1000) private String summary;
        @Min(1) private Long correctionOfEntryId;
    }

    @Data
    public static class ClosePreviewCmd {
        @NotNull @Min(0) private Integer expectedCaseVersion;
        @NotNull @Min(0) private Integer expectedCoordinationVersion;
        @NotNull @Min(0) private Integer expectedGovernanceVersion;
        @NotNull @Min(1) private Long resolutionRevisionId;
        @NotEmpty @Size(max = 20) private List<@NotNull @Min(1) Long> actionReferenceIds;
        @NotEmpty @Size(max = 20) private List<@NotNull @Min(1) Long> evidenceEntryIds;
        @NotNull @Min(1) private Long retrospectiveOwnerUid;
    }

    @Data
    public static class RetrospectiveInitializeCmd {
        @NotNull @Min(0) private Integer expectedRetrospectiveVersion;
        @NotNull @Min(1) private Long ownerUid;
        @NotBlank @Size(min = 1, max = 64) private String commandId;
        @NotBlank @Size(min = 2, max = 1000) private String note;
    }

    @Data
    public static class RetrospectiveAssignOwnerCmd {
        @NotNull @Min(0) private Integer expectedRetrospectiveVersion;
        @NotNull @Min(1) private Long ownerUid;
        @NotBlank @Size(min = 1, max = 64) private String commandId;
        @NotBlank @Size(min = 2, max = 1000) private String note;
    }

    @Data
    public static class RetrospectiveStartCmd {
        @NotNull @Min(0) private Integer expectedRetrospectiveVersion;
        @NotBlank @Size(min = 1, max = 64) private String commandId;
        @NotBlank @Size(min = 2, max = 1000) private String note;
    }

    @Data
    public static class RetrospectiveFindingCmd {
        @NotNull @Min(0) private Integer expectedRetrospectiveVersion;
        @NotBlank @Size(min = 1, max = 64) private String commandId;
        @NotBlank @Size(max = 40) private String learningCategory;
        @NotBlank @Size(min = 2, max = 1000) private String findingSummary;
        @NotBlank @Size(min = 2, max = 1000) private String preventionActionSummary;
        @NotBlank @Size(min = 2, max = 1000) private String note;
    }

    @Data
    public static class RetrospectiveCompleteCmd {
        @NotNull @Min(0) private Integer expectedRetrospectiveVersion;
        @NotBlank @Size(min = 1, max = 64) private String commandId;
        @NotBlank @Size(min = 2, max = 1000) private String note;
    }

    @Data
    public static class RecurrenceLinkCmd {
        @NotNull @Min(0) private Integer expectedCurrentCaseVersion;
        @NotNull @Min(1) private Long previousCaseId;
        @NotBlank @Size(max = 40) private String relationType;
        @NotBlank @Size(max = 40) private String rootCauseCategory;
        @NotBlank @Size(min = 1, max = 64) private String commandId;
        @NotBlank @Size(min = 2, max = 500) private String note;
    }
}
