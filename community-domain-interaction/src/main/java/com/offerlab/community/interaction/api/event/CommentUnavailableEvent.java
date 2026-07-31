package com.offerlab.community.interaction.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published when a comment can no longer be treated as trusted content.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentUnavailableEvent {
    private Long commentId;
    private Long postId;
    private Long actorUid;
    private String reason;
    private Boolean cascade;
}
