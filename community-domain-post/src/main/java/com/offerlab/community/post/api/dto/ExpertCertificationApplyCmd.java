package com.offerlab.community.post.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ExpertCertificationApplyCmd {
    @NotNull
    private Integer domain;
    @NotBlank
    @Size(max = 500)
    private String evidenceSummary;
    @Size(max = 8)
    private List<@Size(max = 512) String> evidenceLinks;
    private Boolean riskAcknowledged;
}
