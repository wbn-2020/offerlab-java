package com.offerlab.community.infra.redis.cache;
import com.offerlab.community.common.utils.LogMask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private static final int MAX_BATCH_GET_SIZE = 500;

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

        List<Long> normalizedIds = postIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .limit(MAX_BATCH_GET_SIZE)
                .toList();
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }

        try {
            List<Object> rows = redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings({"NullableProblems", "unchecked"})
                public Object execute(RedisOperations operations) {
                    HashOperations<String, String, String> hashOps = operations.opsForHash();
                    for (Long postId : normalizedIds) {
                        hashOps.entries(getKey(postId));
                    }
                    return null;
                }
            });
            if (rows == null || rows.isEmpty()) {
                return Map.of();
            }
            Map<Long, CounterValue> result = new LinkedHashMap<>(normalizedIds.size());
            for (int i = 0; i < normalizedIds.size() && i < rows.size(); i++) {
                Map<String, String> entries = normalizeEntries(rows.get(i));
                if (entries.isEmpty()) {
                    continue;
                }
                Long postId = normalizedIds.get(i);
                result.put(postId, toCounterValue(postId, entries));
            }
            return result;
        } catch (Exception e) {
            log.warn("post counter redis batch read degraded, size={} reason={}", normalizedIds.size(), LogMask.message(e));
            return Map.of();
        }
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

    private CounterValue toCounterValue(Long postId, Map<String, String> entries) {
        return new CounterValue(
                postId,
                parseLong(entries.get(FIELD_VIEW)),
                parseLong(entries.get(FIELD_LIKE)),
                parseLong(entries.get(FIELD_COMMENT)),
                parseLong(entries.get(FIELD_FAVORITE)),
                parseLong(entries.get(FIELD_SHARE))
        );
    }

    private Map<String, String> normalizeEntries(Object value) {
        if (!(value instanceof Map<?, ?> raw) || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new HashMap<>(raw.size());
        raw.forEach((field, count) -> {
            if (field != null && count != null) {
                normalized.put(String.valueOf(field), String.valueOf(count));
            }
        });
        return normalized;
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
