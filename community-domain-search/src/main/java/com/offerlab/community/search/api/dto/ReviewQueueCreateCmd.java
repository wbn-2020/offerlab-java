package com.offerlab.community.search.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewQueueCreateCmd {
    @NotBlank
    @Size(max = 32)
    private String sourceType;
    private Long sourceId;
    @NotBlank
    @Size(max = 200)
    private String title;
    @Size(max = 1000)
    private String summary;
    @Size(max = 16)
    private String riskLevel;
    private Integer priority;
    @Size(max = 2000)
    private String extJson;
    @Size(max = 500)
    private String note;
}
