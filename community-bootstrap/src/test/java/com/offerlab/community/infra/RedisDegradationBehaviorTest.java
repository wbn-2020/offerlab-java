package com.offerlab.community.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.redis.cache.MultiLevelCacheImpl;
import com.offerlab.community.infra.redis.cache.PostCounterRedis;
import com.offerlab.community.infra.security.JwtAuthResult;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.common.exception.SystemException;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisDegradationBehaviorTest {

    @Test
    void postCounterFallsBackWhenRedisIsUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForHash()).thenThrow(redisDown());
        PostCounterRedis counterRedis = new PostCounterRedis(redisTemplate);

        assertDoesNotThrow(() -> counterRedis.incrView(1L, 1));
        assertDoesNotThrow(() -> counterRedis.incrLike(1L, 1));
        assertDoesNotThrow(() -> counterRedis.init(1L));
        assertDoesNotThrow(() -> counterRedis.fillFromDb(1L, 1, 2, 3, 4, 5));
        assertNull(counterRedis.get(1L));
        assertTrue(counterRedis.batchGet(List.of(1L, 2L)).isEmpty());
    }

    @Test
    void multiLevelCacheLoadsFromSourceWhenRedisAndLockFail() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        RedissonClient redisson = mock(RedissonClient.class);
        String key = "test:cache:" + UUID.randomUUID();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(key)).thenThrow(redisDown());
        when(redisson.getLock(anyString())).thenThrow(redisDown());

        MultiLevelCacheImpl<String> cache = new MultiLevelCacheImpl<>(redisTemplate, redisson, new ObjectMapper());

        assertEquals("from-db", cache.get(key, ignored -> "from-db", String.class));
    }

    @Test
    void multiLevelCacheLoadsFromSourceWhenRedissonIsNotConfigured() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        String key = "test:cache:no-redisson:" + UUID.randomUUID();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(key)).thenReturn(null);

        MultiLevelCacheImpl<String> cache = new MultiLevelCacheImpl<>(redisTemplate, (RedissonClient) null, new ObjectMapper());

        assertEquals("from-db", cache.get(key, ignored -> "from-db", String.class));
    }

    @Test
    void multiLevelCacheEvictAndPutDoNotPropagateRedisFailure() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        RedissonClient redisson = mock(RedissonClient.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(redisDown()).when(redisTemplate).delete(eq("k"));
        doThrow(redisDown()).when(redisTemplate).convertAndSend(anyString(), eq("k"));
        doThrow(redisDown()).when(valueOps).set(anyString(), anyString(), any(Duration.class));

        MultiLevelCacheImpl<String> cache = new MultiLevelCacheImpl<>(redisTemplate, redisson, new ObjectMapper());

        assertDoesNotThrow(() -> cache.put("k", "v", Duration.ofSeconds(5)));
        assertDoesNotThrow(() -> cache.put("k", null, Duration.ofSeconds(5)));
        assertDoesNotThrow(() -> cache.evict("k"));
    }

    @Test
    void jwtRevocationChecksDegradeClosedAndInvalidationFailsClosed() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.hasKey(anyString())).thenThrow(redisDown());
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(redisDown()).when(valueOps).set(anyString(), anyString(), any(Duration.class));

        JwtService jwtService = new JwtService(redisTemplate);
        ReflectionTestUtils.setField(jwtService, "secret",
                "offerlab-test-secret-key-please-change-in-prod-1234567890abcdef");
        ReflectionTestUtils.setField(jwtService, "ttlHours", 1L);

        String token = jwtService.issue(123L);

        JwtAuthResult authResult = jwtService.parse(token);
        assertEquals(123L, authResult.uid());
        assertTrue(authResult.revocationCheckDegraded());
        assertNull(jwtService.parseUid(token));
        assertThrows(SystemException.class, () -> jwtService.invalidate(token));
        assertThrows(SystemException.class, () -> jwtService.invalidateAll(123L));
    }

    private static RedisConnectionFailureException redisDown() {
        return new RedisConnectionFailureException("redis down");
    }
}
