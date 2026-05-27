package com.offerlab.community.archtest;

import com.offerlab.community.infra.mq.EventEnvelope;
import com.offerlab.community.infra.redis.cache.MultiLevelCacheImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfrastructureSafetyGuardTest {

    @Test
    void eventEnvelopeBuilderKeepsDefaultFields() {
        EventEnvelope<String> envelope = EventEnvelope.<String>builder()
                .messageId("m1")
                .eventType("TEST_EVENT")
                .payload("ok")
                .build();

        assertEquals("v1", envelope.getVersion());
        assertEquals(0, envelope.getRetryCount());
    }

    @Test
    void cacheTtlJitterNeverReturnsNonPositiveDuration() throws Exception {
        MultiLevelCacheImpl<String> cache = new MultiLevelCacheImpl<>(null, null, null);
        Method method = MultiLevelCacheImpl.class.getDeclaredMethod("withJitter", Duration.class);
        method.setAccessible(true);

        for (int i = 0; i < 100; i++) {
            Duration ttl = (Duration) method.invoke(cache, Duration.ofMillis(10));
            assertTrue(ttl.toMillis() >= 1, "jittered ttl must stay positive");
        }
    }
}
