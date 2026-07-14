package com.offerlab.community.interaction.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerAcceptedEvent {
    private Long postId;
    private Long postAuthorUid;
    private Long commentId;
    private Long commentAuthorUid;
    private Long actorUid;
    private Long acceptanceId;
    private Long timestamp;
}
