package com.offerlab.community.interaction.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostLikedEvent {
    private Long uid;
    private Long postId;
    private Long postAuthorId;
    private Long timestamp;
}
