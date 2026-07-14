package com.offerlab.community.interaction.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostFreshnessChangedEvent {
    private Long postId;
    private Long postAuthorUid;
    private Long actorUid;
    private String previousStatus;
    private String freshnessStatus;
    private Long successorPostId;
    private Long timestamp;
}
