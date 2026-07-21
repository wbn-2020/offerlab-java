package com.offerlab.community.user.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A privacy-filtered row from the user's active following relation.
 */
@Data
@Builder
public class UserRelationshipItemDTO {

    private Long relationId;
    private Long uid;
    private String nickname;
    private String bio;
    private LocalDateTime relationTime;
    private LocalDateTime lastPublicUpdateAt;
    private String deliveryMode;
    private LocalDateTime expiresAt;
}
