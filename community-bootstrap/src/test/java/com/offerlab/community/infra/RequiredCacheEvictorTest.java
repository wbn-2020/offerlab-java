package com.offerlab.community.infra;

import com.offerlab.community.infra.redis.cache.CacheKeyBuilder;
import com.offerlab.community.infra.redis.cache.RequiredCacheEvictor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequiredCacheEvictorTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOperations;

    private RequiredCacheEvictor evictor;

    @BeforeEach
    void setUp() {
        evictor = new RequiredCacheEvictor(redis);
        when(redis.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void evictsOnlyTheSuppliedExactKeysAndBroadcastsEachOne() {
        String key = "post:detail:101";
        String epochKey = CacheKeyBuilder.cacheEpoch(key);
        when(valueOperations.increment(epochKey)).thenReturn(1L);
        when(redis.expire(epochKey, Duration.ofHours(2))).thenReturn(true);
        when(redis.delete(key)).thenReturn(true);
        when(redis.convertAndSend(CacheKeyBuilder.cacheEvictChannel(), key)).thenReturn(1L);

        assertEquals(1, evictor.evictExact(List.of(key, key)));

        verify(valueOperations).increment(epochKey);
        verify(redis).expire(epochKey, Duration.ofHours(2));
        verify(redis).delete(key);
        verify(redis).convertAndSend(CacheKeyBuilder.cacheEvictChannel(), key);
    }

    @Test
    void failsClosedWhenRedisCannotConfirmTheDelete() {
        String key = "post:detail:102";
        String epochKey = CacheKeyBuilder.cacheEpoch(key);
        when(valueOperations.increment(epochKey)).thenReturn(1L);
        when(redis.expire(epochKey, Duration.ofHours(2))).thenReturn(true);
        when(redis.delete(key)).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> evictor.evictExact(List.of(key)));
    }

    @Test
    void failsClosedWhenNoCacheEvictionSubscriberIsObservable() {
        String key = "post:detail:103";
        String epochKey = CacheKeyBuilder.cacheEpoch(key);
        when(valueOperations.increment(epochKey)).thenReturn(1L);
        when(redis.expire(epochKey, Duration.ofHours(2))).thenReturn(true);
        when(redis.delete(key)).thenReturn(false);
        when(redis.convertAndSend(CacheKeyBuilder.cacheEvictChannel(), key)).thenReturn(0L);

        assertThrows(IllegalStateException.class, () -> evictor.evictExact(List.of(key)));
    }
}
