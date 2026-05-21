package com.offerlab.community.infra.redis.cache;

import com.offerlab.community.post.api.dto.PostCounterDTO;
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

    /**
     * 增加浏览数
     */
    public void incrView(Long postId, long delta) {
        String key = getKey(postId);
        redisTemplate.opsForHash().increment(key, FIELD_VIEW, delta);
    }

    /**
     * 增加点赞数
     */
    public void incrLike(Long postId, long delta) {
        String key = getKey(postId);
        redisTemplate.opsForHash().increment(key, FIELD_LIKE, delta);
    }

    /**
     * 增加评论数
     */
    public void incrComment(Long postId, long delta) {
        String key = getKey(postId);
        redisTemplate.opsForHash().increment(key, FIELD_COMMENT, delta);
    }

    /**
     * 增加收藏数
     */
    public void incrFavorite(Long postId, long delta) {
        String key = getKey(postId);
        redisTemplate.opsForHash().increment(key, FIELD_FAVORITE, delta);
    }

    /**
     * 增加分享数
     */
    public void incrShare(Long postId, long delta) {
        String key = getKey(postId);
        redisTemplate.opsForHash().increment(key, FIELD_SHARE, delta);
    }

    /**
     * 获取单个帖子的计数器
     * 如果 Redis 中不存在，返回空 DTO（调用方需要从 DB 加载并回填）
     */
    public PostCounterDTO get(Long postId) {
        String key = getKey(postId);
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

        Map<String, String> entries = hashOps.entries(key);
        if (entries.isEmpty()) {
            return null;
        }

        return PostCounterDTO.builder()
                .postId(postId)
                .viewCount(parseLong(entries.get(FIELD_VIEW)))
                .likeCount(parseLong(entries.get(FIELD_LIKE)))
                .commentCount(parseLong(entries.get(FIELD_COMMENT)))
                .favoriteCount(parseLong(entries.get(FIELD_FAVORITE)))
                .shareCount(parseLong(entries.get(FIELD_SHARE)))
                .build();
    }

    /**
     * 批量获取计数器
     */
    public Map<Long, PostCounterDTO> batchGet(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, PostCounterDTO> result = new HashMap<>(postIds.size());
        for (Long postId : postIds) {
            PostCounterDTO dto = get(postId);
            if (dto != null) {
                result.put(postId, dto);
            }
        }
        return result;
    }

    /**
     * 初始化计数器（新帖子发布时调用）
     */
    public void init(Long postId) {
        String key = getKey(postId);
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        hashOps.putIfAbsent(key, FIELD_VIEW, "0");
        hashOps.putIfAbsent(key, FIELD_LIKE, "0");
        hashOps.putIfAbsent(key, FIELD_COMMENT, "0");
        hashOps.putIfAbsent(key, FIELD_FAVORITE, "0");
        hashOps.putIfAbsent(key, FIELD_SHARE, "0");
    }

    /**
     * 从 DB 数据回填 Redis
     */
    public void fillFromDb(Long postId, long viewCount, long likeCount, long commentCount,
                           long favoriteCount, long shareCount) {
        String key = getKey(postId);
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        Map<String, String> map = new HashMap<>();
        map.put(FIELD_VIEW, String.valueOf(viewCount));
        map.put(FIELD_LIKE, String.valueOf(likeCount));
        map.put(FIELD_COMMENT, String.valueOf(commentCount));
        map.put(FIELD_FAVORITE, String.valueOf(favoriteCount));
        map.put(FIELD_SHARE, String.valueOf(shareCount));
        hashOps.putAll(key, map);
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
