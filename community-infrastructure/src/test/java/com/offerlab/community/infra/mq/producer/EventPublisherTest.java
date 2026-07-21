package com.offerlab.community.infra.mq.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.mq.EventEnvelope;
import com.offerlab.community.infra.mq.outbox.OutboxMessage;
import com.offerlab.community.infra.mq.outbox.OutboxMessageMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventPublisherTest {

    @Test
    void kafkaDisabledPublishesLocalEventWithoutCreatingUndeliverableOutboxRows() {
        AtomicReference<OutboxMessage> inserted = new AtomicReference<>();
        List<Object> localEvents = new ArrayList<>();
        EventPublisher publisher = publisher(false, inserted, localEvents);
        PostPublishedEvent event = new PostPublishedEvent(42L);

        publisher.publish(event);

        assertEquals(null, inserted.get());
        assertEquals(1, localEvents.size());
        assertSame(event, localEvents.get(0));
    }

    @Test
    void kafkaEnabledPersistsOutboxBeforePublishingLocalEvent() throws Exception {
        AtomicReference<OutboxMessage> inserted = new AtomicReference<>();
        List<Object> localEvents = new ArrayList<>();
        EventPublisher publisher = publisher(true, inserted, localEvents);
        PostPublishedEvent event = new PostPublishedEvent(42L, 7L);

        publisher.publish(event);

        assertEquals("post.published", inserted.get().getTopic());
        assertEquals(42L, inserted.get().getAggregateId());
        assertEquals(OutboxMessageMapper.STATUS_PENDING, inserted.get().getMsgStatus());
        EventEnvelope<?> envelope = new ObjectMapper().readValue(inserted.get().getPayload(), EventEnvelope.class);
        assertEquals(envelope.getMessageId(), envelope.getIdempotencyKey());
        assertEquals("1", envelope.getSchemaVersion());
        assertEquals("post", envelope.getSourceType());
        assertEquals("42", envelope.getSourceId());
        assertEquals(7L, envelope.getActorUid());
        assertTrue(envelope.getOccurredAt() > 0);
        assertEquals(1, localEvents.size());
        assertSame(event, localEvents.get(0));
    }

    private EventPublisher publisher(boolean kafkaEnabled,
                                     AtomicReference<OutboxMessage> inserted,
                                     List<Object> localEvents) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "test",
                Map.of("offerlab.kafka.enabled", kafkaEnabled)
        ));
        ApplicationEventPublisher localPublisher = localEvents::add;
        OutboxMessageMapper mapper = (OutboxMessageMapper) Proxy.newProxyInstance(
                OutboxMessageMapper.class.getClassLoader(),
                new Class<?>[]{OutboxMessageMapper.class},
                (proxy, method, args) -> {
                    if ("insert".equals(method.getName())) {
                        inserted.set((OutboxMessage) args[0]);
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        return new EventPublisher(
                localPublisher,
                mapper,
                new EventTopicResolver(),
                new ObjectMapper(),
                environment
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    private static final class PostPublishedEvent {
        private final Long postId;
        private final Long actorUid;

        private PostPublishedEvent(Long postId) {
            this(postId, null);
        }

        private PostPublishedEvent(Long postId, Long actorUid) {
            this.postId = postId;
            this.actorUid = actorUid;
        }

        public Long getPostId() {
            return postId;
        }

        public Long getActorUid() {
            return actorUid;
        }
    }
}
