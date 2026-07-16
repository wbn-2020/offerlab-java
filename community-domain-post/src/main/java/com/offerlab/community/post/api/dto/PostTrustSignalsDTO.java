package com.offerlab.community.post.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PostTrustSignalsDTO {
    private Boolean profileAvailable;
    private Integer completenessScore;
    private LocalDateTime lastConfirmedAt;
    private String freshnessStatus;
    private Boolean hasAcceptedAnswer;
    private Integer acceptedSuggestionCount;
    private Integer publicCorrectionCount;
    private Boolean sourceComplete;
    private Boolean resolved;
}
