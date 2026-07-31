package com.offerlab.community.infra.redis.cache;
import com.offerlab.community.common.utils.LogMask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
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
 * MySQL is the authoritative counter store; Redis is a read-through cache.
 * 数据结构：Hash
 * 字段：view, like, comment, favorite, share
 *
 * 读路径：Redis → 缺失时从 DB 加载并回填
 * 写路径：MySQL 提交后使 Redis 失效
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
    private static final List<String> COUNTER_FIELDS = List.of(
            FIELD_VIEW, FIELD_LIKE, FIELD_COMMENT, FIELD_FAVORITE, FIELD_SHARE);
    private static final int MAX_BATCH_GET_SIZE = 500;
    private static final Duration COUNTER_TTL = Duration.ofDays(30);
    private static final Duration COUNTER_EPOCH_TTL = COUNTER_TTL.plus(Duration.ofDays(1));
    private static final DefaultRedisScript<Long> FILL_IF_EPOCH = new DefaultRedisScript<>("""
            local actual = redis.call('GET', KEYS[2])
            if not actual then
              actual = '0'
            end
            if actual ~= ARGV[1] then
              return 0
            end
            redis.call('DEL', KEYS[1])
            redis.call('HSET', KEYS[1],
              'view', ARGV[2],
              'like', ARGV[3],
              'comment', ARGV[4],
              'favorite', ARGV[5],
              'share', ARGV[6])
            redis.call('EXPIRE', KEYS[1], ARGV[7])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> EVICT_AND_ADVANCE_EPOCH = new DefaultRedisScript<>("""
            local next = redis.call('INCR', KEYS[2])
            redis.call('EXPIRE', KEYS[2], ARGV[1])
            redis.call('DEL', KEYS[1])
            return next
            """, Long.class);

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
        invalidateAfterDbMutation(postId, FIELD_VIEW, delta);
    }

    /**
     * 增加点赞数
     */
    public void incrLike(Long postId, long delta) {
        invalidateAfterDbMutation(postId, FIELD_LIKE, delta);
    }

    /**
     * 增加评论数
     */
    public void incrComment(Long postId, long delta) {
        invalidateAfterDbMutation(postId, FIELD_COMMENT, delta);
    }

    /**
     * 增加收藏数
     */
    public void incrFavorite(Long postId, long delta) {
        invalidateAfterDbMutation(postId, FIELD_FAVORITE, delta);
    }

    /**
     * 增加分享数
     */
    public void incrShare(Long postId, long delta) {
        invalidateAfterDbMutation(postId, FIELD_SHARE, delta);
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
            if (!hasAllCounterFields(entries)) {
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
                if (!hasAllCounterFields(entries)) {
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
        fillFromDb(postId, 0L, 0L, 0L, 0L, 0L, currentEpoch(postId));
    }

    /**
     * Captures the invalidation generation for each cache miss before its database query.
     * A later mutation advances that generation, which prevents an old DB snapshot from
     * recreating a cache entry after the mutation has committed.
     */
    public Map<Long, Long> captureEpochs(Collection<Long> postIds) {
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
                    ValueOperations<String, String> valueOps = operations.opsForValue();
                    for (Long postId : normalizedIds) {
                        valueOps.get(epochKey(postId));
                    }
                    return null;
                }
            });
            Map<Long, Long> result = new LinkedHashMap<>(normalizedIds.size());
            for (int i = 0; i < normalizedIds.size(); i++) {
                Object value = rows != null && i < rows.size() ? rows.get(i) : null;
                result.put(normalizedIds.get(i), parseLong(value == null ? null : String.valueOf(value)));
            }
            return result;
        } catch (Exception e) {
            log.warn("post counter epoch capture degraded, size={} reason={}",
                    normalizedIds.size(), LogMask.message(e));
            return Map.of();
        }
    }

    /**
     * From a DB snapshot, populate Redis only when no mutation invalidated the snapshot.
     */
    public void fillFromDb(Long postId, long viewCount, long likeCount, long commentCount,
                           long favoriteCount, long shareCount) {
        fillFromDb(postId, viewCount, likeCount, commentCount, favoriteCount, shareCount,
                currentEpoch(postId));
    }

    /**
     * From a DB snapshot, populate Redis only when no mutation invalidated the snapshot.
     */
    public void fillFromDb(Long postId, long viewCount, long likeCount, long commentCount,
                           long favoriteCount, long shareCount, long expectedEpoch) {
        if (postId == null || postId <= 0) {
            return;
        }
        String key = getKey(postId);
        try {
            Long filled = redisTemplate.execute(FILL_IF_EPOCH, List.of(key, epochKey(postId)),
                    String.valueOf(expectedEpoch),
                    String.valueOf(viewCount),
                    String.valueOf(likeCount),
                    String.valueOf(commentCount),
                    String.valueOf(favoriteCount),
                    String.valueOf(shareCount),
                    String.valueOf(COUNTER_TTL.toSeconds()));
            if (!Long.valueOf(1L).equals(filled)) {
                log.debug("Skipping stale post counter cache fill, postId={}", LogMask.id(postId));
            }
        } catch (Exception e) {
            log.warn("post counter redis fill degraded, postId={} reason={}", LogMask.id(postId), LogMask.message(e));
        }
    }

    public void evict(Long postId) {
        if (postId == null || postId <= 0) {
            return;
        }
        try {
            redisTemplate.execute(EVICT_AND_ADVANCE_EPOCH,
                    List.of(getKey(postId), epochKey(postId)),
                    String.valueOf(COUNTER_EPOCH_TTL.toSeconds()));
        } catch (Exception e) {
            log.warn("post counter redis eviction degraded, postId={} reason={}",
                    LogMask.id(postId), LogMask.message(e));
        }
    }

    private void invalidateAfterDbMutation(Long postId, String field, long delta) {
        evict(postId);
        log.debug("post counter cache invalidated after DB mutation, postId={} field={} delta={}",
                LogMask.id(postId), field, delta);
    }

    private String getKey(Long postId) {
        return KEY_PREFIX + postId;
    }

    private String epochKey(Long postId) {
        return CacheKeyBuilder.cacheEpoch(getKey(postId));
    }

    private long currentEpoch(Long postId) {
        if (postId == null || postId <= 0) {
            return 0L;
        }
        try {
            return parseLong(redisTemplate.opsForValue().get(epochKey(postId)));
        } catch (Exception e) {
            log.warn("post counter epoch read degraded, postId={} reason={}",
                    LogMask.id(postId), LogMask.message(e));
            return 0L;
        }
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

    private boolean hasAllCounterFields(Map<String, String> entries) {
        return entries != null && COUNTER_FIELDS.stream().allMatch(entries::containsKey);
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
