package com.offerlab.community.analytics.infrastructure.persistence.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreatorGrowthChallengeParticipationPO {
    private Long id;
    private Long challengeId;
    private Long uid;
    private String status;
    private Long completedPostId;
    private LocalDateTime joinedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
