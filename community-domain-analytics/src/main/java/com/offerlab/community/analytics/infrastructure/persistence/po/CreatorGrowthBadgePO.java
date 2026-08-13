package com.offerlab.community.analytics.infrastructure.persistence.po;

import lombok.Data;

@Data
public class CreatorGrowthBadgePO {
    private Long id;
    private String badgeCode;
    private String title;
    private String description;
    private Integer requiredCompletedChallengeCount;
    private Integer enabled;
}
