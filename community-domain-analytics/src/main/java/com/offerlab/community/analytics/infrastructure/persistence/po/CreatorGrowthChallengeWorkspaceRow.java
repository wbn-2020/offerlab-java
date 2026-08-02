package com.offerlab.community.analytics.infrastructure.persistence.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreatorGrowthChallengeWorkspaceRow {
    private Long id;
    private String challengeCode;
    private String title;
    private String description;
    private Integer domain;
    private Integer postType;
    private String assistTemplateCode;
    private String status;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private String participationStatus;
    private Long completedPostId;
    private LocalDateTime joinedAt;
    private LocalDateTime completedAt;
}
