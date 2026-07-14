package com.offerlab.community.interaction.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostUsefulFeedbackChangedEvent {
    private Long postId;
    private Long postAuthorUid;
    private Long userId;
    private String reason;
    private String previousReason;
    private Boolean active;
    private Long timestamp;
}
