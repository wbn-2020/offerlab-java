package com.offerlab.community.infra.mq.producer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventTopicResolverNotificationTest {

    @Test
    void commentQualitySignalUsesRegisteredNotificationTopic() {
        EventTopicResolver.TopicMapping mapping = new EventTopicResolver().resolve(
                new CommentQualitySignalChangedEvent(91L));

        assertEquals("interaction.comment.quality-signal-changed", mapping.topic);
        assertEquals("COMMENT_QUALITY_SIGNAL_CHANGED", mapping.eventType);
        assertEquals(91L, mapping.aggregateId);
        assertTrue(mapping.registered);
    }

    private record CommentQualitySignalChangedEvent(Long commentId) {
        public Long getCommentId() {
            return commentId;
        }
    }
}
