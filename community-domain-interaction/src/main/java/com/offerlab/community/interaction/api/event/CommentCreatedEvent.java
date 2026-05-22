package com.offerlab.community.interaction.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentCreatedEvent {
    private Long uid;
    private Long postId;
    private Long postAuthorId;
    private Long commentId;
    private Long parentId;
    private Long replyToUid;
    private String content;
    private Long timestamp;
}
