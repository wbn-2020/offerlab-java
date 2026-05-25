package com.offerlab.community.infra.moderation;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserModerationState {
    private Long uid;
    private LocalDateTime mutedUntil;
    private LocalDateTime bannedUntil;
    private String reason;
    private Long operatorUid;
}
