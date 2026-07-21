package com.offerlab.community.feed.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedFeedbackRedisExpiryTest {

    @Test
    void redisFallbackOnlyReadsControlsInsideTheThirtyDayWindow() {
        Map<String, ScoreRange> requestedRanges = new HashMap<>();
        @SuppressWarnings("unchecked")
        ZSetOperations<String, Object> zSet = (ZSetOperations<String, Object>) Proxy.newProxyInstance(
                ZSetOperations.class.getClassLoader(),
                new Class<?>[]{ZSetOperations.class},
                (proxy, method, args) -> {
                    if ("reverseRangeByScore".equals(method.getName()) && args != null && args.length == 3) {
                        String key = String.valueOf(args[0]);
                        requestedRanges.put(key, new ScoreRange((Double) args[1], (Double) args[2]));
                        return key.endsWith("hidden:z:7") ? Set.of("101") : Set.of("2");
                    }
                    if ("toString".equals(method.getName())) {
                        return "ZSetOperationsStub";
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>() {
            @Override
            public ZSetOperations<String, Object> opsForZSet() {
                return zSet;
            }
        };

        long before = System.currentTimeMillis();
        FeedFeedbackStore store = new FeedFeedbackStore(redisTemplate);

        assertEquals(Set.of(101L), store.hiddenPostIds(7L));
        assertEquals(Set.of(2), store.lessLikedDomains(7L));

        long after = System.currentTimeMillis();
        assertThirtyDayWindow(requestedRanges.get("offerlab:feed:hidden:z:7"), before, after);
        assertThirtyDayWindow(requestedRanges.get("offerlab:feed:less-domain:z:7"), before, after);
    }

    private static void assertThirtyDayWindow(ScoreRange range, long before, long after) {
        long durationMillis = Duration.ofDays(30).toMillis();
        assertTrue(range.minimum() >= before - durationMillis);
        assertTrue(range.minimum() <= after - durationMillis);
        assertEquals(Double.POSITIVE_INFINITY, range.maximum());
    }

    private record ScoreRange(double minimum, double maximum) {
    }
}
