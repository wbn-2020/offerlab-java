package com.offerlab.community.infra.redis.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁 - 基于 Redisson
 */
@Component
public class DistributedLock {
    private final RedissonClient redissonClient;

    public DistributedLock(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 获取锁
     */
    public RLock getLock(String key) {
        return redissonClient.getLock(key);
    }

    /**
     * 尝试加锁
     */
    public boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        RLock lock = getLock(key);
        return lock.tryLock(waitTime, leaseTime, unit);
    }

    /**
     * 释放锁
     */
    public void unlock(String key) {
        RLock lock = getLock(key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
