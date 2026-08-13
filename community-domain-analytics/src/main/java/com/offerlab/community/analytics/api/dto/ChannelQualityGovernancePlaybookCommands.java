package com.offerlab.community.analytics.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

public final class ChannelQualityGovernancePlaybookCommands {
    private ChannelQualityGovernancePlaybookCommands() {
    }

    @Data
    public static class CreatePlaybookCmd {
        @NotBlank
        private String playbookCode;
        @NotNull
        private JsonNode content;
        @NotBlank
        private String idempotencyKey;
        @NotBlank
        private String reason;
    }

    @Data
    public static class CreateVersionCmd {
        @NotNull
        @Min(0)
        private Integer expectedPlaybookVersion;
        private Long baseVersionId;
        @NotNull
        private JsonNode content;
        @NotBlank
        private String idempotencyKey;
        @NotBlank
        private String reason;
    }

    @Data
    public static class VersionTransitionCmd {
        @NotNull
        @Min(0)
        private Integer expectedPlaybookVersion;
        @NotBlank
        private String expectedContentHash;
        @NotBlank
        private String idempotencyKey;
        @NotBlank
        private String reason;
    }

    @Data
    public static class BindCasePlaybookCmd {
        @NotNull
        private Long playbookVersionId;
        @NotNull
        @Min(0)
        private Integer expectedCaseVersion;
        @NotNull
        @Min(0)
        private Integer expectedGovernanceFactVersion;
        @NotBlank
        private String expectedGovernanceSnapshotEtag;
        @NotBlank
        private String expectedContentHash;
        @NotBlank
        private String idempotencyKey;
        @NotBlank
        private String note;
    }

    @Data
    public static class CasePlaybookTransitionCmd {
        @NotNull
        @Min(0)
        private Integer expectedBindingVersion;
        @NotNull
        @Min(0)
        private Integer expectedCaseVersion;
        @NotNull
        @Min(0)
        private Integer expectedGovernanceFactVersion;
        @NotBlank
        private String expectedGovernanceSnapshotEtag;
        @NotBlank
        private String idempotencyKey;
        @NotBlank
        private String note;
    }

    @Data
    public static class VerifyCheckCmd extends CasePlaybookTransitionCmd {
        @NotNull
        @Min(0)
        private Integer expectedCheckVersion;
        private List<Long> evidenceIds;
    }
}
