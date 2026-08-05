package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelQualityReviewCandidateDispositionDTO {
    private Long sourcePostId;
    private Long sourceRefId;
    private String state;
    private String reasonCode;
    private Instant snoozedUntil;
}
