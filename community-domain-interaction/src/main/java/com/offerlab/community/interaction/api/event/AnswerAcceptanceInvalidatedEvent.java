package com.offerlab.community.interaction.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerAcceptanceInvalidatedEvent {
    private Long postId;
    private Long commentId;
    private Long actorUid;
    private String reason;
    private Long timestamp;
}
