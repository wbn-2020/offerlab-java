package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveReadHeartbeatDTO {

    private Boolean accepted;
    private Boolean countingActive;
    private Long nextHeartbeatSeq;
    private Integer activeSeconds;
    private Integer maxScrollPercent;
    private Boolean qualified;
    private Boolean completed;
    private Long expiresAt;
}
