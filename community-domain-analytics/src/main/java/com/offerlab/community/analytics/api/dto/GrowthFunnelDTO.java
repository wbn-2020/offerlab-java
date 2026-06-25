package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrowthFunnelDTO {
    private Integer days;
    private Integer activeDomain;
    private Long visitCount;
    private Long registerCount;
    private Long interactionCount;
    private Long firstPublishCount;
    private Long revisitCount;
}
