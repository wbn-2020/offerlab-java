package com.offerlab.community.post.collaboration.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationContributionAcceptedEvent {
    private Long contributorUid;
    private Integer domain;
    private String contributionType;
    private Long sourceId;
    private Long targetPostId;
    private String stableKey;
    private Long occurredAt;
}
