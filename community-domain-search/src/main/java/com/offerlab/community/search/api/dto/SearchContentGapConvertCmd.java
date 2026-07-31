package com.offerlab.community.search.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SearchContentGapConvertCmd {
    @NotNull
    @Min(1)
    @Max(5)
    private Integer domain;

    @Size(max = 32)
    private String contentFormat;

    @Size(max = 120)
    private String title;

    @Size(max = 2000)
    private String description;

    @Size(max = 1000)
    private String acceptanceCriteria;

    @Size(max = 500)
    private String note;

    @Size(max = 32)
    private String confirmationPhrase;

    private Boolean riskAcknowledged;
}
