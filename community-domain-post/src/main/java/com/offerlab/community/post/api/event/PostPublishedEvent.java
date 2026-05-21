package com.offerlab.community.post.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 帖子发布事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostPublishedEvent {
    private Long postId;
    private Long authorId;
    private String title;
    private String content;
    private Long timestamp;
}
