package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertCertificationEligibilityDTO {
    private Integer domain;
    private String domainName;
    private Boolean eligible;
    private Boolean riskAcknowledgementRequired;
    private Boolean manualReviewOnly;
    private String riskWarning;
    private String explanation;
    private List<CheckItemDTO> checks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckItemDTO {
        private String code;
        private String label;
        private Boolean passed;
        private String detail;
    }
}
