package com.offerlab.community.infra.redis.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Fail-closed cache eviction for audited administrative operations.
 */
@Component
public class RequiredCacheEvictor {

    private static final int MAX_KEYS = 500;
    private static final Duration EPOCH_TTL = Duration.ofHours(2);

    private final StringRedisTemplate redis;

    public RequiredCacheEvictor(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public int evictExact(Collection<String> keys) {
        List<String> normalizedKeys = normalize(keys);
        for (String key : normalizedKeys) {
            evictRequired(key);
        }
        return normalizedKeys.size();
    }

    private void evictRequired(String key) {
        String epochKey = CacheKeyBuilder.cacheEpoch(key);
        Long epoch = redis.opsForValue().increment(epochKey);
        if (epoch == null) {
            throw new IllegalStateException("cache epoch update returned no result");
        }
        Boolean epochExpirySet = redis.expire(epochKey, EPOCH_TTL);
        if (!Boolean.TRUE.equals(epochExpirySet)) {
            throw new IllegalStateException("cache epoch expiry was not applied");
        }

        CacheEvictListener.invalidateGlobalL1(key);
        Boolean deleted = redis.delete(key);
        if (deleted == null) {
            throw new IllegalStateException("cache delete returned no result");
        }
        Long subscribers = redis.convertAndSend(CacheKeyBuilder.cacheEvictChannel(), key);
        if (subscribers == null || subscribers <= 0) {
            throw new IllegalStateException("cache eviction broadcast reached no subscribers");
        }
    }

    private static List<String> normalize(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("cache key must not be blank");
            }
            normalized.add(key.trim());
            if (normalized.size() > MAX_KEYS) {
                throw new IllegalArgumentException("too many exact cache keys");
            }
        }
        return List.copyOf(normalized);
    }
}
