package com.offerlab.community.interaction.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentLikedEvent {
    private Long uid;
    private Long commentId;
    private Long commentAuthorId;
    private Long postId;
    private Long timestamp;
}
