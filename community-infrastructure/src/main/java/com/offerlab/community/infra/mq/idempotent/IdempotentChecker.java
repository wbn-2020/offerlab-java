package com.offerlab.community.infra.mq.idempotent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 幂等消费检查器
 * 通过 Redis SETNX 实现消息级别的幂等性
 * 配合业务幂等键和数据库唯一索引形成三层防重
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentChecker {

    private final RedisTemplate<String, String> redisTemplate;

    /** 幂等键 TTL：24 小时 */
    private static final Duration TTL = Duration.ofHours(24);

    /**
     * 尝试消费消息
     * @param messageId 消息唯一标识
     * @param consumerName 消费者名称（用于区分不同消费者）
     * @return true 表示首次消费，应执行业务；false 表示重复消费，应跳过
     */
    public boolean tryConsume(String messageId, String consumerName) {
        String key = "idempotent:" + consumerName + ":" + messageId;
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", TTL);
        boolean isFirstTime = Boolean.TRUE.equals(ok);

        if (isFirstTime) {
            log.debug("idempotent check passed: consumer={} messageId={}", consumerName, messageId);
        } else {
            log.warn("duplicate message detected: consumer={} messageId={}", consumerName, messageId);
        }

        return isFirstTime;
    }

    public void release(String messageId, String consumerName) {
        String key = "idempotent:" + consumerName + ":" + messageId;
        Boolean deleted = redisTemplate.delete(key);
        log.warn("idempotent key released: consumer={} messageId={} deleted={}", consumerName, messageId, deleted);
    }
}
