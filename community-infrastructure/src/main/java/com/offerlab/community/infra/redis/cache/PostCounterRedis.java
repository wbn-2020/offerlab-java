package com.offerlab.community.infra.redis.cache;
import com.offerlab.community.common.utils.LogMask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 帖子计数器 Redis 操作
 *
 * Redis 是真值，MySQL 是兜底持久化
 * 数据结构：Hash
 * 字段：view, like, comment, favorite, share
 *
 * 读路径：Redis → 缺失时从 DB 加载并回填
 * 写路径：Redis 写主 + 双写 MySQL（过渡方案）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostCounterRedis {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "post:counter:";
    private static final String FIELD_VIEW = "view";
    private static final String FIELD_LIKE = "like";
    private static final String FIELD_COMMENT = "comment";
    private static final String FIELD_FAVORITE = "favorite";
    private static final String FIELD_SHARE = "share";

    public record CounterValue(Long postId,
                               Long viewCount,
                               Long likeCount,
                               Long commentCount,
                               Long favoriteCount,
                               Long shareCount) {
    }

    /**
     * 增加浏览数
     */
    public void incrView(Long postId, long delta) {
        increment(postId, FIELD_VIEW, delta);
    }

    /**
     * 增加点赞数
     */
    public void incrLike(Long postId, long delta) {
        increment(postId, FIELD_LIKE, delta);
    }

    /**
     * 增加评论数
     */
    public void incrComment(Long postId, long delta) {
        increment(postId, FIELD_COMMENT, delta);
    }

    /**
     * 增加收藏数
     */
    public void incrFavorite(Long postId, long delta) {
        increment(postId, FIELD_FAVORITE, delta);
    }

    /**
     * 增加分享数
     */
    public void incrShare(Long postId, long delta) {
        increment(postId, FIELD_SHARE, delta);
    }

    /**
     * 获取单个帖子的计数器
     * 如果 Redis 中不存在，返回空 DTO（调用方需要从 DB 加载并回填）
     */
    public CounterValue get(Long postId) {
        String key = getKey(postId);
        try {
            HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

            Map<String, String> entries = hashOps.entries(key);
            if (entries.isEmpty()) {
                return null;
            }

            return new CounterValue(
                    postId,
                    parseLong(entries.get(FIELD_VIEW)),
                    parseLong(entries.get(FIELD_LIKE)),
                    parseLong(entries.get(FIELD_COMMENT)),
                    parseLong(entries.get(FIELD_FAVORITE)),
                    parseLong(entries.get(FIELD_SHARE))
            );
        } catch (Exception e) {
            log.warn("post counter redis read degraded, postId={} reason={}", LogMask.id(postId), LogMask.message(e));
            return null;
        }
    }

    /**
     * 批量获取计数器
     */
    public Map<Long, CounterValue> batchGet(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, CounterValue> result = new HashMap<>(postIds.size());
        for (Long postId : postIds) {
            CounterValue value = get(postId);
            if (value != null) {
                result.put(postId, value);
            }
        }
        return result;
    }

    /**
     * 初始化计数器（新帖子发布时调用）
     */
    public void init(Long postId) {
        String key = getKey(postId);
        try {
            HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
            hashOps.putIfAbsent(key, FIELD_VIEW, "0");
            hashOps.putIfAbsent(key, FIELD_LIKE, "0");
            hashOps.putIfAbsent(key, FIELD_COMMENT, "0");
            hashOps.putIfAbsent(key, FIELD_FAVORITE, "0");
            hashOps.putIfAbsent(key, FIELD_SHARE, "0");
        } catch (Exception e) {
            log.warn("post counter redis init degraded, postId={} reason={}", LogMask.id(postId), LogMask.message(e));
        }
    }

    /**
     * 从 DB 数据回填 Redis
     */
    public void fillFromDb(Long postId, long viewCount, long likeCount, long commentCount,
                           long favoriteCount, long shareCount) {
        String key = getKey(postId);
        Map<String, String> map = new HashMap<>();
        map.put(FIELD_VIEW, String.valueOf(viewCount));
        map.put(FIELD_LIKE, String.valueOf(likeCount));
        map.put(FIELD_COMMENT, String.valueOf(commentCount));
        map.put(FIELD_FAVORITE, String.valueOf(favoriteCount));
        map.put(FIELD_SHARE, String.valueOf(shareCount));
        try {
            HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
            hashOps.putAll(key, map);
        } catch (Exception e) {
            log.warn("post counter redis fill degraded, postId={} reason={}", LogMask.id(postId), LogMask.message(e));
        }
    }

    private void increment(Long postId, String field, long delta) {
        String key = getKey(postId);
        try {
            redisTemplate.opsForHash().increment(key, field, delta);
        } catch (Exception e) {
            log.warn("post counter redis increment degraded, postId={} field={} delta={} reason={}",
                    LogMask.id(postId), field, delta, LogMask.message(e));
        }
    }

    private String getKey(Long postId) {
        return KEY_PREFIX + postId;
    }

    private long parseLong(String value) {
        if (value == null || value.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse long value: {}", value);
            return 0L;
        }
    }
}
