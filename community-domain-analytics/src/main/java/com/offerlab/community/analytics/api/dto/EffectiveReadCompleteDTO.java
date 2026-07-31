package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveReadCompleteDTO {

    private Boolean recorded;
    private Boolean completed;
    private Integer activeSeconds;
    private Integer maxScrollPercent;
}
