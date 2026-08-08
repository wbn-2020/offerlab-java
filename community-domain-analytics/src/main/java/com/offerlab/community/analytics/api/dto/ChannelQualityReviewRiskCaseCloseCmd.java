package com.offerlab.community.analytics.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ChannelQualityReviewRiskCaseCloseCmd {
    @NotNull
    @Min(0)
    private Integer expectedCaseVersion;

    @NotNull
    @Min(0)
    private Integer expectedCoordinationVersion;

    @NotNull
    @Min(0)
    private Integer expectedGovernanceVersion;

    @NotNull
    @Positive
    private Long resolutionRevisionId;

    @NotEmpty
    @Size(max = 20)
    private List<@NotNull @Positive Long> actionReferenceIds;

    @NotEmpty
    @Size(max = 20)
    private List<@NotNull @Positive Long> evidenceEntryIds;

    @NotNull
    @Positive
    private Long retrospectiveOwnerUid;

    @NotBlank
    @Size(max = 64)
    private String commandId;

    @NotBlank
    @Size(min = 2, max = 500)
    private String note;
}
