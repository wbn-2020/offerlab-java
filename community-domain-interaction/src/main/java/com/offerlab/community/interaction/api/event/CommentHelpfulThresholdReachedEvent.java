package com.offerlab.community.interaction.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentHelpfulThresholdReachedEvent {
    private Long commentId;
    private Long commentAuthorId;
    private Long postId;
    private Integer helpfulCount;
    private Long timestamp;
}
