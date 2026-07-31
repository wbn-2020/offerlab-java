package com.offerlab.community.post.relationship.api;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RelationshipItemDTO {

    private String sourceType;
    private Long sourceId;
    private Long relationId;
    private String title;
    private String summary;
    private String targetPath;
    private String relationStatus;
    private String sourceStatus;
    private LocalDateTime lastPublicUpdateAt;
    private LocalDateTime relationTime;
    private String deliveryMode;
    private LocalDateTime expiresAt;
    private boolean deliveryPreferenceSupported;
    private String deliveryPreferenceUnsupportedReason;
}
