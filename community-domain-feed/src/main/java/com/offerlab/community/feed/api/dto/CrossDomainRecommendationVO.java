package com.offerlab.community.feed.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrossDomainRecommendationVO {
    private FeedItemVO item;
    private Integer sourceDomain;
    private String sourceDomainName;
    private Integer targetDomain;
    private String targetDomainName;
    private String recommendationReason;
    private Boolean degraded;
}
