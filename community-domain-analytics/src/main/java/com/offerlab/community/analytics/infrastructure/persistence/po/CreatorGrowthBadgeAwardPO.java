package com.offerlab.community.analytics.infrastructure.persistence.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreatorGrowthBadgeAwardPO {
    private Long id;
    private Long uid;
    private Long badgeId;
    private Long sourceChallengeId;
    private String awardStatus;
    private LocalDateTime awardedAt;
}
