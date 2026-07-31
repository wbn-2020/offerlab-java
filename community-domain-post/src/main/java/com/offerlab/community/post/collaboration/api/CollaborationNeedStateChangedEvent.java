package com.offerlab.community.post.collaboration.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationNeedStateChangedEvent {
    private Long eventId;
    private Long needId;
    private String eventType;
    private Long actorUid;
    private Long creatorUid;
    private Long claimantUid;
    private Integer domain;
    private Long targetNeedId;
    private String targetType;
    private Long targetId;
    private String note;
    private String fromStatus;
    private String toStatus;
    private Long occurredAt;
    private String dedupKey;
}
