package com.offerlab.community.infra.redis.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 缓存失效监听器
 * 订阅 Redis Pub/Sub channel:cache:evict，收到消息时从本地 Caffeine 中删除对应 key
 *
 * 用于多节点场景下的 L1 缓存一致性保证
 *
 * 注意：这是一个全局单例，所有 MultiLevelCache 实例共享同一个 L1 缓存
 */
@Slf4j
@Component
public class CacheEvictListener implements MessageListener {

    private static final int MAX_SINGLE_ENTRY_WEIGHT = 2_000_000;

    // 全局 L1 缓存实例（所有 MultiLevelCache 共享）
    private static final Cache<String, Object> GLOBAL_L1_CACHE = Caffeine.newBuilder()
            .maximumWeight(20_000_000)
            .weigher(CacheEvictListener::estimateWeight)
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats()
            .build();

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String key = new String(message.getBody());
            log.debug("Received cache evict message for key: {}", key);
            GLOBAL_L1_CACHE.invalidate(key);
        } catch (Exception e) {
            log.error("Failed to process cache evict message", e);
        }
    }

    /**
     * 获取全局 L1 缓存实例
     */
    public static Cache<String, Object> getGlobalL1Cache() {
        return GLOBAL_L1_CACHE;
    }

    public static Object getGlobalL1Value(String key) {
        Object stored = GLOBAL_L1_CACHE.getIfPresent(key);
        return stored instanceof WeightedValue weighted ? weighted.value() : stored;
    }

    public static boolean putGlobalL1(String key, Object value, int serializedLength) {
        int weight = serializedWeight(key, serializedLength);
        if (weight > MAX_SINGLE_ENTRY_WEIGHT) {
            log.warn("Skip oversized L1 cache value, keyRef={}, estimatedWeight={}",
                    safeCacheKey(key), weight);
            return false;
        }
        GLOBAL_L1_CACHE.put(key, new WeightedValue(value, weight));
        return true;
    }

    public static void invalidateGlobalL1(String key) {
        GLOBAL_L1_CACHE.invalidate(key);
    }

    private static int estimateWeight(String key, Object value) {
        if (value instanceof WeightedValue weighted) {
            return weighted.weight();
        }
        return serializedWeight(key, fallbackSerializedLength(value));
    }

    private static int serializedWeight(String key, int serializedLength) {
        long keyWeight = key == null ? 0 : (long) key.length() * 2;
        long valueWeight = Math.max(1L, (long) Math.max(0, serializedLength) * 2);
        long total = Math.max(1L, keyWeight + valueWeight);
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private static int fallbackSerializedLength(Object value) {
        if (value == null) {
            return 1;
        }
        if (value instanceof CharSequence text) {
            return text.length();
        }
        if (value instanceof byte[] bytes) {
            return bytes.length;
        }
        return 512;
    }

    private static String safeCacheKey(String key) {
        return key == null ? "null" : "hash=" + Integer.toHexString(key.hashCode()) + ",len=" + key.length();
    }

    private record WeightedValue(Object value, int weight) {
    }
}
