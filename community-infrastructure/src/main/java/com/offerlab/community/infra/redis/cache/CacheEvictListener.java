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

    // 全局 L1 缓存实例（所有 MultiLevelCache 共享）
    private static final Cache<String, Object> GLOBAL_L1_CACHE = Caffeine.newBuilder()
            .maximumSize(10_000)
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
}
