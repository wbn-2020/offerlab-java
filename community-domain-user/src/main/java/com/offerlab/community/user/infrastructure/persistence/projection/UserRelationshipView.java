package com.offerlab.community.user.infrastructure.persistence.projection;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserRelationshipView {

    private Long relationId;
    private Long uid;
    private String nickname;
    private String bio;
    private LocalDateTime relationTime;
    private LocalDateTime lastPublicUpdateAt;
    private String deliveryMode;
    private LocalDateTime expiresAt;
}
