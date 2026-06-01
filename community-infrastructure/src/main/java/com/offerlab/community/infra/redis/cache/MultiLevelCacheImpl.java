package com.offerlab.community.infra.redis.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 多级缓存实现
 * L1: Caffeine（进程内，maxSize=10_000, expireAfterWrite=5min）
 * L2: Redis（集中式，TTL=30min ± 5min 随机抖动防雪崩）
 *
 * 防护机制：
 * - 击穿：Redisson 互斥锁 + 双重检查
 * - 穿透：空值缓存（TTL=60s）
 * - 雪崩：TTL 加随机抖动
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiLevelCacheImpl<V> implements MultiLevelCache<V> {

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redisson;
    private final ObjectMapper objectMapper;

    // 空值标记
    private static final String NULL_MARKER = "$$NULL$$";

    // 随机数生成器
    private static final Random RANDOM = new Random();
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
    private static final Duration MAX_TTL_JITTER = Duration.ofMinutes(5);
    private static final Duration MIN_TTL = Duration.ofSeconds(1);

    @Override
    public V get(String key, Function<String, V> loader, Class<V> type) {
        // L1 查询（使用全局缓存）
        Cache<String, Object> l1Cache = CacheEvictListener.getGlobalL1Cache();
        Object l1Value = l1Cache.getIfPresent(key);
        if (l1Value != null) {
            if (NULL_MARKER.equals(l1Value)) {
                return null;
            }
            if (type.isInstance(l1Value)) {
                return type.cast(l1Value);
            }
            log.warn("L1 cache type mismatch for key: {}, expected: {}, actual: {}",
                    key, type.getName(), l1Value.getClass().getName());
            l1Cache.invalidate(key);
        }

        try {
            String l2Value = redisTemplate.opsForValue().get(key);
            V cached = fromL2Value(key, l2Value, type, l1Cache);
            if (cached != null || NULL_MARKER.equals(l2Value)) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("L2 cache read failed before lock, key={}, fallback to loader protection: {}", key, e.getMessage());
        }

        // L2 未命中后再进入击穿保护
        String lockKey = CacheKeyBuilder.cacheLock(key);
        RLock lock = null;
        boolean locked = false;
        try {
            lock = redisson.getLock(lockKey);
            locked = lock.tryLock(3, 30, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("Failed to acquire lock for key: {}, rechecking L2 before loader", key);
                try {
                    String l2Value = redisTemplate.opsForValue().get(key);
                    V cached = fromL2Value(key, l2Value, type, l1Cache);
                    if (cached != null || NULL_MARKER.equals(l2Value)) {
                        return cached;
                    }
                } catch (Exception e) {
                    log.warn("L2 cache recheck failed after lock miss, key={}: {}", key, e.getMessage());
                }
                return loadAndCache(key, loader, type);
            }

            // 双重检查：加锁后再查一次 L2
            String l2Value = redisTemplate.opsForValue().get(key);
            V cached = fromL2Value(key, l2Value, type, l1Cache);
            if (cached != null || NULL_MARKER.equals(l2Value)) {
                return cached;
            }

            // L2 也未命中，走 loader
            return loadAndCache(key, loader, type);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while acquiring lock for key: {}", key, e);
            return loadAndCache(key, loader, type);
        } catch (Exception e) {
            log.warn("L2 cache lock degraded, key={}, fallback to loader: {}", key, e.getMessage());
            return loadAndCache(key, loader, type);
        } finally {
            if (locked && lock != null) {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (Exception e) {
                    log.warn("L2 cache lock release failed, key={}: {}", key, e.getMessage());
                }
            }
        }
    }

    private V fromL2Value(String key, String l2Value, Class<V> type, Cache<String, Object> l1Cache) {
        if (l2Value == null) {
            return null;
        }
        if (NULL_MARKER.equals(l2Value)) {
            l1Cache.put(key, NULL_MARKER);
            return null;
        }
        V result = deserialize(l2Value, type);
        if (result != null) {
            l1Cache.put(key, result);
        }
        return result;
    }

    @Override
    public void evict(String key) {
        CacheEvictListener.getGlobalL1Cache().invalidate(key);
        // 删 L2
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("L2 cache delete failed, key={}: {}", key, e.getMessage());
        }
        // 广播失效消息
        try {
            redisTemplate.convertAndSend(CacheKeyBuilder.cacheEvictChannel(), key);
        } catch (Exception e) {
            log.warn("L2 cache eviction publish failed, key={}: {}", key, e.getMessage());
        }
    }

    @Override
    public void put(String key, V value, Duration ttl) {
        if (value == null) {
            // 缓存空值，短 TTL 防穿透
            try {
                redisTemplate.opsForValue().set(key, NULL_MARKER, Duration.ofSeconds(60));
            } catch (Exception e) {
                log.warn("L2 null cache write failed, key={}: {}", key, e.getMessage());
            }
        } else {
            String serialized = serialize(value);
            if (serialized != null) {
                try {
                    redisTemplate.opsForValue().set(key, serialized, withJitter(ttl));
                } catch (Exception e) {
                    log.warn("L2 cache write failed, key={}: {}", key, e.getMessage());
                }
            }
        }
    }

    /**
     * 加载数据并缓存
     */
    private V loadAndCache(String key, Function<String, V> loader, Class<V> type) {
        Cache<String, Object> l1Cache = CacheEvictListener.getGlobalL1Cache();
        V value = loader.apply(key);
        boolean loadedNull = value == null;
        if (loadedNull) {
            // 缓存空值防穿透
            l1Cache.put(key, NULL_MARKER);
            try {
                redisTemplate.opsForValue().set(key, NULL_MARKER, Duration.ofSeconds(60));
            } catch (Exception e) {
                log.warn("L2 null cache write failed, key={}: {}", key, e.getMessage());
            }
        } else {
            // 缓存到 L1 和 L2
            l1Cache.put(key, value);
            String serialized = serialize(value);
            if (serialized != null) {
                try {
                    redisTemplate.opsForValue().set(key, serialized, withJitter(DEFAULT_TTL));
                } catch (Exception e) {
                    log.warn("L2 cache write failed, key={}: {}", key, e.getMessage());
                }
            }
        }
        return value;
    }

    private Duration withJitter(Duration ttl) {
        long ttlMs = Math.max(MIN_TTL.toMillis(), ttl == null ? DEFAULT_TTL.toMillis() : ttl.toMillis());
        long jitterCapMs = Math.min(MAX_TTL_JITTER.toMillis(), Math.max(0, ttlMs / 2));
        if (jitterCapMs == 0) {
            return Duration.ofMillis(ttlMs);
        }
        long jitterMs = RANDOM.nextLong(jitterCapMs * 2 + 1) - jitterCapMs;
        return Duration.ofMillis(Math.max(MIN_TTL.toMillis(), ttlMs + jitterMs));
    }

    /**
     * 序列化为 JSON
     */
    private String serialize(V value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("Failed to serialize value", e);
            return null;
        }
    }

    /**
     * 反序列化
     */
    private V deserialize(String json, Class<V> type) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        if (NULL_MARKER.equals(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("Failed to deserialize value: {}", json, e);
            return null;
        }
    }
}
