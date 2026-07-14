package com.offerlab.community.infra.redis.cache;

import java.time.Duration;
import java.util.function.Function;

/**
 * 多级缓存接口
 * L1: Caffeine（进程内）
 * L2: Redis（集中式）
 *
 * 架构：
 * 请求 → L1 hit → 返回
 *      → L1 miss → L2 hit → 回填 L1 → 返回
 *      → L2 miss → DB loader → 回填 L2 + L1 → 返回
 *
 * 失效策略：删 L2 + Pub/Sub 通知所有节点删 L1
 */
public interface MultiLevelCache<V> {

    /**
     * 获取缓存值
     * 查询路径：L1 → L2 → loader（DB）
     *
     * @param key    缓存 key
     * @param loader 缓存未命中时的加载函数（通常是 DB 查询）
     * @param type   值的类型，用于 JSON 反序列化
     * @return 缓存值，如果 loader 返回 null 则缓存空值（短 TTL 防穿透）
     */
    V get(String key, Function<String, V> loader, Class<V> type);

    /**
     * 删除缓存
     * 删除 L2 + 通过 Pub/Sub 通知所有节点删除 L1
     *
     * @param key 缓存 key
     */
    void evict(String key);

    /**
     * Write-through contract: refresh the current-node L1 immediately and write Redis L2 best-effort.
     *
     * @param key   cache key
     * @param value cache value
     * @param ttl   time-to-live
     */
    void put(String key, V value, Duration ttl);
}
