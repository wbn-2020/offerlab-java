package com.offerlab.community.post.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDeletedEvent {
    private Long postId;
    private Long authorId;
    private Long timestamp;
}
