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
public class ChannelQualityReviewRiskCaseResolutionRevisionDTO {
    private Long id;
    private Long caseId;
    private Integer revisionNo;
    private String outcomeType;
    private String contentRecoveryState;
    private String recoveryScope;
    private String residualRiskLevel;
    private String summary;
    private List<RootCauseDTO> rootCauses;
    private Instant createTime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RootCauseDTO {
        private String role;
        private String category;
        private Integer sequenceNo;
        private String note;
    }
}
