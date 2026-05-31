package com.offerlab.community.feed.infrastructure;

import com.offerlab.community.infra.redis.lua.LuaScriptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 收件箱 / 时间线 Redis 操作
 * Key:
 *   feed:inbox:{uid}     用户收件箱
 *   feed:timeline:{uid}  用户发帖时间线（作者主页用）
 *   feed:latest:global   全站最新（截断 1w 条）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedInboxRedis {

    private static final String INBOX = "feed:inbox:";
    private static final String TIMELINE = "feed:timeline:";
    private static final String GLOBAL_LATEST = "feed:latest:global";
    private static final int GLOBAL_LATEST_CAP = 10000;

    private final StringRedisTemplate redis;
    private final LuaScriptLoader lua;

    @Value("${offerlab.feed.inbox-capacity:1000}")
    private int inboxCapacity;

    @Value("${offerlab.feed.inbox-ttl-seconds:604800}")
    private int inboxTtlSeconds;

    public void addToInbox(Long uid, Long postId, long ts) {
        try {
            redis.execute(
                    lua.get("feed_inbox_add"),
                    Collections.singletonList(INBOX + uid),
                    String.valueOf(postId),
                    String.valueOf(ts),
                    String.valueOf(inboxCapacity),
                    String.valueOf(inboxTtlSeconds)
            );
        } catch (Exception e) {
            log.warn("addToInbox failed: uid={} postId={} err={}", uid, postId, e.getMessage());
            throw new IllegalStateException("add feed inbox failed", e);
        }
    }

    public void addToAuthorTimeline(Long authorUid, Long postId, long ts) {
        try {
            String key = TIMELINE + authorUid;
            redis.opsForZSet().add(key, String.valueOf(postId), ts);
            redis.opsForZSet().removeRange(key, 0, -501); // 保最近 500
            redis.expire(key, java.time.Duration.ofDays(30));
        } catch (Exception e) {
            log.warn("addToAuthorTimeline failed: uid={} postId={}", authorUid, postId, e);
            throw new IllegalStateException("add author timeline failed", e);
        }
    }

    public void addToGlobalLatest(Long postId, long ts) {
        try {
            redis.opsForZSet().add(GLOBAL_LATEST, String.valueOf(postId), ts);
            // 异步裁剪：保留最近 cap
            redis.opsForZSet().removeRange(GLOBAL_LATEST, 0, -GLOBAL_LATEST_CAP - 1);
        } catch (Exception e) {
            log.warn("addToGlobalLatest failed", e);
            throw new IllegalStateException("add global latest failed", e);
        }
    }

    /** 收件箱按 score < cursor 倒序拉取 postId */
    public List<Long> readInbox(Long uid, double maxScoreExclusive, int size) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet()
                .reverseRangeByScoreWithScores(INBOX + uid, 0, maxScoreExclusive, 0, size);
        if (tuples == null || tuples.isEmpty()) return List.of();
        return tuples.stream()
                .map(t -> Long.parseLong(t.getValue()))
                .toList();
    }

    public Set<ZSetOperations.TypedTuple<String>> readInboxWithScore(Long uid, double maxScoreExclusive, int size) {
        return redis.opsForZSet().reverseRangeByScoreWithScores(INBOX + uid, 0, maxScoreExclusive, 0, size);
    }

    public Set<ZSetOperations.TypedTuple<String>> readGlobalLatest(double maxScoreExclusive, int size) {
        return redis.opsForZSet()
                .reverseRangeByScoreWithScores(GLOBAL_LATEST, 0, maxScoreExclusive, 0, size);
    }
}
