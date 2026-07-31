package com.offerlab.community.interaction.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionStateChangedEvent {
    private Long postId;
    private Long postAuthorUid;
    private Long actorUid;
    private String previousStatus;
    private String questionStatus;
    private Long acceptedCommentId;
    private Long duplicatePostId;
    private Long timestamp;
}
