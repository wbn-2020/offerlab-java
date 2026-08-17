package com.offerlab.community.feed.infrastructure;

import com.offerlab.community.common.utils.LogMask;
import com.offerlab.community.infra.redis.lua.LuaScriptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private static final Duration GLOBAL_LATEST_TTL = Duration.ofDays(30);
    private static final long SCORE_SEQUENCE_BASE = 4096L;
    private static final int REQUIRED_INVALIDATION_POST_LIMIT = 100;
    private static final long REQUIRED_INVALIDATION_SCAN_COUNT = 1000L;

    private final StringRedisTemplate redis;
    private final LuaScriptLoader lua;

    @Value("${offerlab.feed.inbox-capacity:1000}")
    private int inboxCapacity;

    @Value("${offerlab.feed.inbox-ttl-seconds:604800}")
    private int inboxTtlSeconds;

    @Value("${offerlab.feed.required-invalidation.max-inbox-keys:100000}")
    int maxRequiredInboxKeys = 100_000;

    public void addToInbox(Long uid, Long postId, long ts) {
        try {
            redis.execute(
                    lua.get("feed_inbox_add"),
                    Collections.singletonList(INBOX + uid),
                    String.valueOf(postId),
                    String.valueOf(stableScore(ts, postId)),
                    String.valueOf(inboxCapacity),
                    String.valueOf(inboxTtlSeconds)
            );
        } catch (Exception e) {
            log.warn("addToInbox failed: uid={} postId={} err={}",
                    LogMask.id(uid), LogMask.id(postId), LogMask.message(e));
            throw new IllegalStateException("add feed inbox failed", e);
        }
    }

    public void addToAuthorTimeline(Long authorUid, Long postId, long ts) {
        try {
            String key = TIMELINE + authorUid;
            redis.opsForZSet().add(key, String.valueOf(postId), stableScore(ts, postId));
            redis.opsForZSet().removeRange(key, 0, -501); // 保最近 500
            redis.expire(key, java.time.Duration.ofDays(30));
        } catch (Exception e) {
            log.warn("addToAuthorTimeline failed: uid={} postId={} err={}",
                    LogMask.id(authorUid), LogMask.id(postId), LogMask.message(e), e);
            throw new IllegalStateException("add author timeline failed", e);
        }
    }

    public void addToGlobalLatest(Long postId, long ts) {
        try {
            redis.opsForZSet().add(GLOBAL_LATEST, String.valueOf(postId), stableScore(ts, postId));
            // 异步裁剪：保留最近 cap
            redis.opsForZSet().removeRange(GLOBAL_LATEST, 0, -GLOBAL_LATEST_CAP - 1);
            redis.expire(GLOBAL_LATEST, GLOBAL_LATEST_TTL);
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

    public RemovalResult removePostsRequired(
            Map<Long, ? extends Collection<Long>> postIdsByAuthor) {
        Map<Long, List<Long>> normalizedByAuthor = normalizePostsByAuthor(postIdsByAuthor);
        if (normalizedByAuthor.isEmpty()) {
            return new RemovalResult(0L, 0L, 0L, 0);
        }
        List<String> inboxKeys = scanInboxKeysRequired();
        Object[] allMembers = normalizedByAuthor.values().stream()
                .flatMap(Collection::stream)
                .distinct()
                .map(String::valueOf)
                .toArray();
        try {
            long globalRemoved = removed(redis.opsForZSet().remove(GLOBAL_LATEST, allMembers));
            long authorTimelineRemoved = 0L;
            for (Map.Entry<Long, List<Long>> entry : normalizedByAuthor.entrySet()) {
                Object[] authorMembers = entry.getValue().stream().map(String::valueOf).toArray();
                authorTimelineRemoved += removed(redis.opsForZSet()
                        .remove(TIMELINE + entry.getKey(), authorMembers));
            }
            long inboxRemoved = 0L;
            for (String inboxKey : inboxKeys) {
                inboxRemoved += removed(redis.opsForZSet().remove(inboxKey, allMembers));
            }
            return new RemovalResult(globalRemoved, authorTimelineRemoved, inboxRemoved,
                    inboxKeys.size());
        } catch (RuntimeException e) {
            throw new IllegalStateException("required feed cache invalidation failed", e);
        }
    }

    public record RemovalResult(long globalLatestRemoved,
                                long authorTimelineRemoved,
                                long followerInboxEntriesRemoved,
                                int followerInboxesChecked) {
    }

    private static long removed(Long value) {
        if (value == null) {
            throw new IllegalStateException("feed cache removal returned no result");
        }
        return value;
    }

    private List<String> scanInboxKeysRequired() {
        if (maxRequiredInboxKeys <= 0) {
            throw new IllegalStateException("required inbox scan limit must be positive");
        }
        ScanOptions options = ScanOptions.scanOptions()
                .match(INBOX + "*")
                .count(REQUIRED_INVALIDATION_SCAN_COUNT)
                .build();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        int scannedEntries = 0;
        try (Cursor<String> cursor = redis.scan(options)) {
            if (cursor == null) {
                throw new IllegalStateException("required inbox scan returned no cursor");
            }
            while (cursor.hasNext()) {
                String key = cursor.next();
                scannedEntries++;
                if (key == null || !key.startsWith(INBOX)) {
                    throw new IllegalStateException("required inbox scan returned an unexpected key");
                }
                keys.add(key);
                if (scannedEntries > maxRequiredInboxKeys) {
                    throw new IllegalStateException("required inbox scan exceeds the controlled limit");
                }
            }
            return List.copyOf(keys);
        } catch (RuntimeException e) {
            throw new IllegalStateException("required inbox scan failed", e);
        }
    }

    private static Map<Long, List<Long>> normalizePostsByAuthor(
            Map<Long, ? extends Collection<Long>> postIdsByAuthor) {
        if (postIdsByAuthor == null || postIdsByAuthor.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Long>> normalized = new LinkedHashMap<>();
        LinkedHashSet<Long> allPostIds = new LinkedHashSet<>();
        for (Map.Entry<Long, ? extends Collection<Long>> entry : postIdsByAuthor.entrySet()) {
            Long authorUid = entry.getKey();
            if (authorUid == null || authorUid <= 0) {
                throw new IllegalArgumentException("authorUid must be positive");
            }
            List<Long> authorPostIds = normalizePositiveIds(
                    entry.getValue(), REQUIRED_INVALIDATION_POST_LIMIT, "postIds");
            if (authorPostIds.isEmpty()) {
                continue;
            }
            allPostIds.addAll(authorPostIds);
            if (allPostIds.size() > REQUIRED_INVALIDATION_POST_LIMIT) {
                throw new IllegalArgumentException("postIds exceeds the controlled limit");
            }
            normalized.put(authorUid, authorPostIds);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(normalized));
    }

    private static List<Long> normalizePositiveIds(Collection<Long> ids, int maxSize, String field) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException(field + " must contain positive ids");
            }
            normalized.add(id);
            if (normalized.size() > maxSize) {
                throw new IllegalArgumentException(field + " exceeds the controlled limit");
            }
        }
        return List.copyOf(normalized);
    }

    static double stableScore(long timestampMillis, Long postId) {
        long sequence = postId == null ? 0L : Math.floorMod(postId, SCORE_SEQUENCE_BASE);
        return Math.addExact(Math.multiplyExact(timestampMillis, SCORE_SEQUENCE_BASE), sequence);
    }

    public static long scoreTimestamp(double score) {
        if (score > 10_000_000_000_000D) {
            return (long) (score / SCORE_SEQUENCE_BASE);
        }
        return (long) score;
    }
}
