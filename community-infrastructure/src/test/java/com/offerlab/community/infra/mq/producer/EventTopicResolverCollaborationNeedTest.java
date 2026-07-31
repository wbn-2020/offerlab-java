package com.offerlab.community.infra.mq.producer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventTopicResolverCollaborationNeedTest {

    @Test
    void resolvesCollaborationNeedStateChangedTopicAndAggregate() {
        EventTopicResolver.TopicMapping mapping = new EventTopicResolver().resolve(
                new CollaborationNeedStateChangedEvent(42L));

        assertEquals("collaboration.need.state-changed", mapping.topic);
        assertEquals(42L, mapping.aggregateId);
        assertEquals("COLLABORATION_NEED_STATE_CHANGED", mapping.eventType);
        assertTrue(mapping.registered);
        assertEquals("collaboration", mapping.sourceType);
        assertEquals("42", mapping.sourceId);
    }

    @Test
    void unknownEventKeepsFallbackButMarksMappingUnregistered() {
        EventTopicResolver.TopicMapping mapping = new EventTopicResolver().resolve(new UnknownEvent());

        assertEquals("unknown_event", mapping.topic);
        assertFalse(mapping.registered);
        assertEquals("UnknownEvent", mapping.sourceType);
    }

    private static final class CollaborationNeedStateChangedEvent {
        private final Long needId;

        private CollaborationNeedStateChangedEvent(Long needId) {
            this.needId = needId;
        }

        public Long getNeedId() {
            return needId;
        }
    }

    private static final class UnknownEvent {
    }
}
