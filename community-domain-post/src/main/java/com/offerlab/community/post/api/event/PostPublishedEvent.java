package com.offerlab.community.post.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
    private Integer visibility;
    private Integer postStatus;
    private Integer domain;
    private Long timestamp;
    private List<Long> tagIds;
    private List<TopicNotificationTarget> topicNotificationTargets;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicNotificationTarget {
        private Long topicId;
        private String topicSlug;
        private String topicName;
        private List<Long> followerUids;
    }
}
