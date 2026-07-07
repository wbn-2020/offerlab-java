package com.offerlab.community.interaction.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentQualitySignalChangedEvent {
    private Long postId;
    private Long postAuthorUid;
    private Long commentId;
    private Long operatorUid;
    private Long commentAuthorUid;
    private String signalType;
    private Boolean active;
    private String reason;
    private Long timestamp;
}
