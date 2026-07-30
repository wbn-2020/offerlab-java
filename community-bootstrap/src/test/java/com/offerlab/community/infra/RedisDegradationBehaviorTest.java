package com.offerlab.community.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.redis.cache.MultiLevelCacheImpl;
import com.offerlab.community.infra.redis.cache.PostCounterRedis;
import com.offerlab.community.infra.redis.cache.CacheEvictListener;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

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
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

class RedisDegradationBehaviorTest {

    @Test
    void postCounterFallsBackWhenRedisIsUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForHash()).thenThrow(redisDown());
        when(redisTemplate.opsForValue()).thenThrow(redisDown());
        PostCounterRedis counterRedis = new PostCounterRedis(redisTemplate);

        assertDoesNotThrow(() -> counterRedis.incrView(1L, 1));
        assertDoesNotThrow(() -> counterRedis.incrLike(1L, 1));
        assertDoesNotThrow(() -> counterRedis.init(1L));
        assertDoesNotThrow(() -> counterRedis.fillFromDb(1L, 1, 2, 3, 4, 5));
        assertNull(counterRedis.get(1L));
        assertTrue(counterRedis.batchGet(List.of(1L, 2L)).isEmpty());
    }

    @Test
    void postCounterUsesAtomicFillAndInvalidationScripts() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        PostCounterRedis counterRedis = new PostCounterRedis(redisTemplate);

        counterRedis.init(11L);
        counterRedis.incrView(11L, 1);
        counterRedis.fillFromDb(11L, 1, 2, 3, 4, 5);

        long scriptCalls = mockingDetails(redisTemplate).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("execute"))
                .count();
        assertEquals(3L, scriptCalls);
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
    void multiLevelCacheDoesNotRetryLoaderWhenLoaderItselfFails() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        RedissonClient redisson = mock(RedissonClient.class);
        org.redisson.api.RLock lock = mock(org.redisson.api.RLock.class);
        String key = "test:cache:loader-failure:" + UUID.randomUUID();
        RuntimeException failure = new IllegalStateException("source unavailable");

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(key)).thenReturn(null);
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(3, 30, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        MultiLevelCacheImpl<String> cache = new MultiLevelCacheImpl<>(redisTemplate, redisson, new ObjectMapper());
        AtomicInteger loaderCalls = new AtomicInteger();

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> cache.get(key, ignored -> {
            loaderCalls.incrementAndGet();
            throw failure;
        }, String.class));

        assertEquals(failure, thrown);
        assertEquals(1, loaderCalls.get(),
                "a source-loader failure must not be mistaken for cache degradation and executed twice");
    }

    @Test
    void multiLevelCacheLoadsFromSourceWhenRedissonIsNotConfigured() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        String key = "test:cache:no-redisson:" + UUID.randomUUID();
        int callers = 8;
        CountDownLatch initialReads = new CountDownLatch(callers);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(key)).thenAnswer(ignored -> {
            initialReads.countDown();
            if (!initialReads.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent callers did not reach the cache miss together");
            }
            return null;
        });

        MultiLevelCacheImpl<String> cache = new MultiLevelCacheImpl<>(redisTemplate, (RedissonClient) null, new ObjectMapper());
        AtomicInteger loaderCalls = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(callers);

        try {
            List<Future<String>> results = IntStream.range(0, callers)
                    .mapToObj(index -> executor.submit(() -> {
                        start.await();
                        return cache.get(key, ignored -> {
                            loaderCalls.incrementAndGet();
                            return "from-db";
                        }, String.class);
                    }))
                    .toList();
            start.countDown();
            for (Future<String> result : results) {
                assertEquals("from-db", result.get(10, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, loaderCalls.get(),
                "local cache protection must collapse concurrent misses into one source load");
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
    void multiLevelCachePutRefreshesLocalL1Immediately() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        String key = "test:cache:put-l1:" + UUID.randomUUID();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(key)).thenReturn(null);
        CacheEvictListener.getGlobalL1Cache().invalidate(key);

        MultiLevelCacheImpl<String> cache = new MultiLevelCacheImpl<>(
                redisTemplate, (RedissonClient) null, new ObjectMapper());

        assertEquals("old", cache.get(key, ignored -> "old", String.class));
        cache.put(key, "new", Duration.ofMinutes(1));

        assertEquals("new", cache.get(key, ignored -> "loader-must-not-run", String.class));
        CacheEvictListener.getGlobalL1Cache().invalidate(key);
    }

    @Test
    void multiLevelCacheDoesNotRetainOversizedValuesInLocalL1() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        String key = "test:cache:oversized-l1:" + UUID.randomUUID();
        String oversized = "x".repeat(2_100_000);
        AtomicInteger loaderCalls = new AtomicInteger();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(key)).thenReturn(null);
        CacheEvictListener.getGlobalL1Cache().invalidate(key);
        MultiLevelCacheImpl<String> cache = new MultiLevelCacheImpl<>(
                redisTemplate, (RedissonClient) null, new ObjectMapper());

        cache.put(key, oversized, Duration.ofMinutes(1));
        String loaded = cache.get(key, ignored -> {
            loaderCalls.incrementAndGet();
            return "from-loader";
        }, String.class);

        assertEquals("from-loader", loaded);
        assertEquals(1, loaderCalls.get());
        CacheEvictListener.getGlobalL1Cache().invalidate(key);
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
