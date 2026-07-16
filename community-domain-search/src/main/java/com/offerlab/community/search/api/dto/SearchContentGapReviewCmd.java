package com.offerlab.community.search.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SearchContentGapReviewCmd {
    @Min(1)
    @Max(5)
    private Integer domain;

    @Size(max = 500)
    private String note;

    @Size(max = 32)
    private String confirmationPhrase;

    private Boolean riskAcknowledged;
}
