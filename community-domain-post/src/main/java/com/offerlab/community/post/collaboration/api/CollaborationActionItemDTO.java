package com.offerlab.community.post.collaboration.api;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CollaborationActionItemDTO {
    private Long id;
    private String actionType;
    private String sourceType;
    private Long sourceId;
    private String sourceStatus;
    private String title;
    private String reason;
    private String targetPath;
    private Long lastEventId;
    private LocalDateTime updatedAt;
    private Boolean canAct;
}
