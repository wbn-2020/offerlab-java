package com.offerlab.community.search.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SearchContentGapResolveCmd {
    @NotBlank
    @Size(max = 24)
    private String resolutionType;

    @Positive
    private Long resolutionId;

    @Positive
    private Long resolutionPostId;

    @Size(max = 500)
    private String note;

    @Size(max = 32)
    private String confirmationPhrase;

    private Boolean riskAcknowledged;
}
