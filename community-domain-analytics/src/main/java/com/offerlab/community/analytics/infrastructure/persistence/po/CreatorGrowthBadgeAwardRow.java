package com.offerlab.community.analytics.infrastructure.persistence.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreatorGrowthBadgeAwardRow {
    private String badgeCode;
    private String title;
    private String description;
    private Integer requiredCompletedChallengeCount;
    private LocalDateTime awardedAt;
}
