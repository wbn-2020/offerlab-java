package com.offerlab.community.infra.moderation;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserModerationState {
    private Long uid;
    private String nickname;
    private String avatarUrl;
    private LocalDateTime mutedUntil;
    private LocalDateTime bannedUntil;
    private String reason;
    private Long operatorUid;
    private String recentViolationKeyword;
    private String recentViolationAction;
    private String recentViolationSummary;
    private LocalDateTime recentViolationTime;
}
