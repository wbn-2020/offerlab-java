package com.offerlab.community.interaction.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiscussionFollowStatusDTO {
    private Long postId;
    private Boolean followed;
    private Long lastReadCommentId;
    private Long lastNotifiedCommentId;
    private String source;
}
