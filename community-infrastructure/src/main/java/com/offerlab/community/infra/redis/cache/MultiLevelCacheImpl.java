package com.offerlab.community.infra.redis.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
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
public class MultiLevelCacheImpl<V> implements MultiLevelCache<V> {

    private final StringRedisTemplate redisTemplate;
    @Nullable
    private final RedissonClient redisson;
    private final ObjectMapper objectMapper;

    // 空值标记
    private static final String NULL_MARKER = "$$NULL$$";

    // 随机数生成器
    private static final Random RANDOM = new Random();
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
    private static final Duration MAX_TTL_JITTER = Duration.ofMinutes(5);
    private static final Duration MIN_TTL = Duration.ofSeconds(1);
    private static final Duration EPOCH_TTL = Duration.ofHours(2);
    private static final int LOCAL_LOCK_STRIPES = 256;
    private static final ReentrantLock[] LOCAL_LOAD_LOCKS = createLocalLocks();

    @Autowired
    public MultiLevelCacheImpl(StringRedisTemplate redisTemplate,
                               ObjectProvider<RedissonClient> redissonProvider,
                               ObjectMapper objectMapper) {
        this(redisTemplate, redissonProvider.getIfAvailable(), objectMapper);
    }

    public MultiLevelCacheImpl(StringRedisTemplate redisTemplate,
                               @Nullable RedissonClient redisson,
                               ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.redisson = redisson;
        this.objectMapper = objectMapper;
    }

    @Override
    public V get(String key, Function<String, V> loader, Class<V> type) {
        // L1 查询（使用全局缓存）
        Object l1Value = CacheEvictListener.getGlobalL1Value(key);
        if (l1Value != null) {
            if (NULL_MARKER.equals(l1Value)) {
                return null;
            }
            if (type.isInstance(l1Value)) {
                return type.cast(l1Value);
            }
            log.warn("L1 cache type mismatch, keyRef={}, expected={}, actual={}",
                    safeCacheKey(key), type.getName(), l1Value.getClass().getName());
            CacheEvictListener.invalidateGlobalL1(key);
        }

        try {
            try {
                String l2Value = redisTemplate.opsForValue().get(key);
                V cached = fromL2Value(key, l2Value, type);
                if (cached != null || NULL_MARKER.equals(l2Value)) {
                    return cached;
                }
            } catch (Exception e) {
                log.warn("L2 cache recheck failed after lock, keyRef={}: {}",
                        safeCacheKey(key), e.getMessage());
            }
        } catch (Exception e) {
            log.warn("L2 cache read failed before lock, keyRef={}, fallback to loader protection: {}", safeCacheKey(key), e.getMessage());
        }

        // L2 未命中后再进入击穿保护
        String lockKey = CacheKeyBuilder.cacheLock(key);
        if (redisson == null) {
            log.warn("L2 cache lock unavailable, keyRef={}, fallback to loader", safeCacheKey(key));
            return loadAndCache(key, loader, type);
        }
        RLock lock = null;
        boolean locked = false;
        try {
            try {
                lock = redisson.getLock(lockKey);
                locked = lock.tryLock(3, 30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while acquiring cache lock, keyRef={}", safeCacheKey(key));
                return loadAndCache(key, loader, type);
            } catch (Exception e) {
                log.warn("L2 cache lock degraded, keyRef={}, fallback to loader: {}",
                        safeCacheKey(key), e.getMessage());
                return loadAndCache(key, loader, type);
            }
            if (!locked) {
                log.warn("Failed to acquire cache lock, keyRef={}, rechecking L2 before loader", safeCacheKey(key));
                try {
                    String l2Value = redisTemplate.opsForValue().get(key);
                    V cached = fromL2Value(key, l2Value, type);
                    if (cached != null || NULL_MARKER.equals(l2Value)) {
                        return cached;
                    }
                } catch (Exception e) {
                    log.warn("L2 cache recheck failed after lock miss, keyRef={}: {}", safeCacheKey(key), e.getMessage());
                }
                return loadAndCache(key, loader, type);
            }

            // 双重检查：加锁后再查一次 L2
            String l2Value = redisTemplate.opsForValue().get(key);
            V cached = fromL2Value(key, l2Value, type);
            if (cached != null || NULL_MARKER.equals(l2Value)) {
                return cached;
            }

            // L2 也未命中，走 loader
            return loadAndCache(key, loader, type);
        } finally {
            if (locked && lock != null) {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (Exception e) {
                    log.warn("L2 cache lock release failed, keyRef={}: {}", safeCacheKey(key), e.getMessage());
                }
            }
        }
    }

    private V fromL2Value(String key, String l2Value, Class<V> type) {
        if (l2Value == null) {
            return null;
        }
        if (NULL_MARKER.equals(l2Value)) {
            CacheEvictListener.putGlobalL1(key, NULL_MARKER, NULL_MARKER.length());
            return null;
        }
        V result = deserialize(l2Value, type);
        if (result != null) {
            CacheEvictListener.putGlobalL1(key, result, l2Value.length());
        }
        return result;
    }

    @Override
    public void evict(String key) {
        RLock distributedLock = null;
        boolean distributedLocked = false;
        try {
            if (redisson != null) {
                distributedLock = redisson.getLock(CacheKeyBuilder.cacheLock(key));
                if (distributedLock != null) {
                    distributedLocked = distributedLock.tryLock(3, 30, TimeUnit.SECONDS);
                    if (!distributedLocked) {
                        log.warn("Timed out waiting for distributed cache eviction lock, keyRef={}", safeCacheKey(key));
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting to evict cache, keyRef={}", safeCacheKey(key));
        } catch (Exception e) {
            log.warn("Distributed cache eviction lock unavailable, keyRef={}: {}", safeCacheKey(key), e.getMessage());
        }

        ReentrantLock localLock = localLoadLock(key);
        localLock.lock();
        try {
            advanceEpoch(key);
            CacheEvictListener.invalidateGlobalL1(key);
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                log.warn("L2 cache delete failed, keyRef={}: {}", safeCacheKey(key), e.getMessage());
            }
            try {
                redisTemplate.convertAndSend(CacheKeyBuilder.cacheEvictChannel(), key);
            } catch (Exception e) {
                log.warn("L2 cache eviction publish failed, keyRef={}: {}", safeCacheKey(key), e.getMessage());
            }
        } finally {
            localLock.unlock();
            if (distributedLocked && distributedLock != null) {
                try {
                    if (distributedLock.isHeldByCurrentThread()) {
                        distributedLock.unlock();
                    }
                } catch (Exception e) {
                    log.warn("Distributed cache eviction lock release failed, keyRef={}: {}",
                            safeCacheKey(key), e.getMessage());
                }
            }
        }
    }

    @Override
    public void put(String key, V value, Duration ttl) {
        advanceEpoch(key);
        if (value == null) {
            // 缓存空值，短 TTL 防穿透
            CacheEvictListener.putGlobalL1(key, NULL_MARKER, NULL_MARKER.length());
            try {
                redisTemplate.opsForValue().set(key, NULL_MARKER, Duration.ofSeconds(60));
            } catch (Exception e) {
                log.warn("L2 null cache write failed, keyRef={}: {}", safeCacheKey(key), e.getMessage());
            }
        } else {
            String serialized = serialize(value);
            if (serialized != null) {
                CacheEvictListener.putGlobalL1(key, value, serialized.length());
                try {
                    redisTemplate.opsForValue().set(key, serialized, withJitter(ttl));
                } catch (Exception e) {
                    log.warn("L2 cache write failed, keyRef={}: {}", safeCacheKey(key), e.getMessage());
                }
            }
        }
    }

    /**
     * 加载数据并缓存
     */
    private V loadAndCache(String key, Function<String, V> loader, Class<V> type) {
        ReentrantLock localLock = localLoadLock(key);
        localLock.lock();
        try {
            Object l1Value = CacheEvictListener.getGlobalL1Value(key);
            if (NULL_MARKER.equals(l1Value)) {
                return null;
            }
            if (l1Value != null) {
                if (type.isInstance(l1Value)) {
                    return type.cast(l1Value);
                }
                log.warn("L1 cache type mismatch after local lock, keyRef={}, expected={}, actual={}",
                        safeCacheKey(key), type.getName(), l1Value.getClass().getName());
                CacheEvictListener.invalidateGlobalL1(key);
            }

            try {
                String l2Value = redisTemplate.opsForValue().get(key);
                V cached = fromL2Value(key, l2Value, type);
                if (cached != null || NULL_MARKER.equals(l2Value)) {
                    return cached;
                }
            } catch (Exception e) {
                log.warn("L2 cache recheck failed after local lock, keyRef={}: {}",
                        safeCacheKey(key), e.getMessage());
            }

            long epochBeforeLoad = currentEpoch(key);
            V value = loader.apply(key);
            if (!isCurrentEpoch(key, epochBeforeLoad)) {
                log.debug("Skipping stale cache write after eviction or replacement, keyRef={}", safeCacheKey(key));
                return value;
            }
            var l1Cache = CacheEvictListener.getGlobalL1Cache();
            boolean loadedNull = value == null;
            if (loadedNull) {
                // 缓存空值防穿透
                l1Cache.put(key, NULL_MARKER);
                try {
                    redisTemplate.opsForValue().set(key, NULL_MARKER, Duration.ofSeconds(60));
                } catch (Exception e) {
                    log.warn("L2 null cache write failed, keyRef={}: {}", safeCacheKey(key), e.getMessage());
                }
            } else {
                // 缓存到 L1 和 L2
                String serialized = serialize(value);
                if (serialized != null) {
                    CacheEvictListener.putGlobalL1(key, value, serialized.length());
                    try {
                        redisTemplate.opsForValue().set(key, serialized, withJitter(DEFAULT_TTL));
                    } catch (Exception e) {
                        log.warn("L2 cache write failed, keyRef={}: {}", safeCacheKey(key), e.getMessage());
                    }
                }
            }
            return value;
        } finally {
            localLock.unlock();
        }
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
            log.error("Failed to deserialize cached value, type={}", type.getName(), e);
            return null;
        }
    }

    private long currentEpoch(String key) {
        try {
            String value = redisTemplate.opsForValue().get(CacheKeyBuilder.cacheEpoch(key));
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            log.debug("Cache epoch read degraded, keyRef={}: {}", safeCacheKey(key), e.getMessage());
            return 0L;
        }
    }

    private boolean isCurrentEpoch(String key, long expected) {
        return currentEpoch(key) == expected;
    }

    private void advanceEpoch(String key) {
        try {
            String epochKey = CacheKeyBuilder.cacheEpoch(key);
            redisTemplate.opsForValue().increment(epochKey);
            redisTemplate.expire(epochKey, EPOCH_TTL);
        } catch (Exception e) {
            log.debug("Cache epoch update degraded, keyRef={}: {}", safeCacheKey(key), e.getMessage());
        }
    }

    private static String safeCacheKey(String key) {
        return key == null ? "null" : "hash=" + Integer.toHexString(key.hashCode()) + ",len=" + key.length();
    }

    private static ReentrantLock[] createLocalLocks() {
        ReentrantLock[] locks = new ReentrantLock[LOCAL_LOCK_STRIPES];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }

    private static ReentrantLock localLoadLock(String key) {
        int hash = key == null ? 0 : key.hashCode();
        return LOCAL_LOAD_LOCKS[(hash & Integer.MAX_VALUE) % LOCAL_LOAD_LOCKS.length];
    }
}
