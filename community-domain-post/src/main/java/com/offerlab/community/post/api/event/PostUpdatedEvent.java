package com.offerlab.community.post.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostUpdatedEvent {
    private Long postId;
    private Long authorId;
    private String title;
    private String content;
    private Long timestamp;
}
