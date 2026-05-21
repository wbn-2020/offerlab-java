package com.offerlab.community.infra.mq.producer;

import com.offerlab.community.interaction.api.event.PostLikedEvent;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.user.api.event.UserFollowedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 事件类型 → Topic 映射解析器
 * 根据事件类型推导 Kafka Topic 和 aggregateId
 */
@Slf4j
@Component
public class EventTopicResolver {

    /**
     * 事件类型映射结果
     */
    public static class TopicMapping {
        public final String topic;
        public final Long aggregateId;
        public final String eventType;

        public TopicMapping(String topic, Long aggregateId, String eventType) {
            this.topic = topic;
            this.aggregateId = aggregateId;
            this.eventType = eventType;
        }
    }

    /**
     * 根据事件对象推导 Topic 和 aggregateId
     * @param event 事件对象
     * @return TopicMapping
     */
    public TopicMapping resolve(Object event) {
        if (event instanceof PostPublishedEvent) {
            PostPublishedEvent e = (PostPublishedEvent) event;
            return new TopicMapping("post.published", e.getPostId(), "POST_PUBLISHED");
        }

        if (event instanceof PostLikedEvent) {
            PostLikedEvent e = (PostLikedEvent) event;
            return new TopicMapping("interaction.like", e.getPostId(), "LIKE");
        }

        if (event instanceof UserFollowedEvent) {
            UserFollowedEvent e = (UserFollowedEvent) event;
            return new TopicMapping("user.followed", e.getFromUid(), "USER_FOLLOWED");
        }

        // 未识别的事件类型，用类名小写作为 topic
        String className = event.getClass().getSimpleName();
        String topic = className.replaceAll("([A-Z])", "_$1").toLowerCase().replaceFirst("^_", "");
        log.warn("Unknown event type: {}, using topic: {}", className, topic);
        return new TopicMapping(topic, 0L, className);
    }
}
