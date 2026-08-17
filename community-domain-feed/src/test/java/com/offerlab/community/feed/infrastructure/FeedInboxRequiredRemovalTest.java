package com.offerlab.community.feed.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeedInboxRequiredRemovalTest {

    @Test
    void removesExactPostMembersFromGlobalAuthorAndFollowerTimelines() {
        RecordingRedisTemplate redis = new RecordingRedisTemplate();
        redis.put("feed:latest:global", "101", "102", "999");
        redis.put("feed:timeline:11", "101", "102");
        redis.put("feed:inbox:21", "101", "999");
        redis.put("feed:inbox:22", "101", "102", "999");
        FeedInboxRedis feed = new FeedInboxRedis(redis, null);

        FeedInboxRedis.RemovalResult result =
                feed.removePostsRequired(Map.of(11L, List.of(101L, 102L)));

        assertEquals(2L, result.globalLatestRemoved());
        assertEquals(2L, result.authorTimelineRemoved());
        assertEquals(3L, result.followerInboxEntriesRemoved());
        assertEquals(2, result.followerInboxesChecked());
        assertEquals("feed:inbox:*", redis.scanOptions.getPattern());
    }

    @Test
    void failsClosedWhenRedisDoesNotReturnAnObservableResult() {
        RecordingRedisTemplate redis = new RecordingRedisTemplate();
        redis.returnNullRemoval = true;
        FeedInboxRedis feed = new FeedInboxRedis(redis, null);

        assertThrows(IllegalStateException.class,
                () -> feed.removePostsRequired(Map.of(11L, List.of(101L))));
    }

    @Test
    void removesPostsFromHistoricalInboxesWithoutCurrentFollowerEnumeration() {
        RecordingRedisTemplate redis = new RecordingRedisTemplate();
        redis.put("feed:inbox:former-follower", "101", "999");
        FeedInboxRedis feed = new FeedInboxRedis(redis, null);

        FeedInboxRedis.RemovalResult result =
                feed.removePostsRequired(Map.of(11L, List.of(101L)));

        assertEquals(1L, result.followerInboxEntriesRemoved());
        assertEquals(1, result.followerInboxesChecked());
    }

    @Test
    void scansCompletelyBeforeMutationAndFailsWhenTheBoundIsExceeded() {
        RecordingRedisTemplate redis = new RecordingRedisTemplate();
        redis.put("feed:latest:global", "101");
        redis.put("feed:inbox:21", "101");
        redis.put("feed:inbox:22", "101");
        FeedInboxRedis feed = new FeedInboxRedis(redis, null);
        feed.maxRequiredInboxKeys = 1;

        assertThrows(IllegalStateException.class,
                () -> feed.removePostsRequired(Map.of(11L, List.of(101L))));

        assertEquals(Set.of("101"), redis.values.get("feed:latest:global"));
        assertEquals(Set.of("101"), redis.values.get("feed:inbox:21"));
    }

    private static final class RecordingRedisTemplate extends StringRedisTemplate {
        private final Map<String, Set<String>> values = new HashMap<>();
        private boolean returnNullRemoval;
        private ScanOptions scanOptions;

        @Override
        @SuppressWarnings("unchecked")
        public Cursor<String> scan(ScanOptions options) {
            scanOptions = options;
            List<String> keys = new ArrayList<>(values.keySet().stream()
                    .filter(key -> key.startsWith("feed:inbox:"))
                    .sorted(Comparator.naturalOrder())
                    .toList());
            Iterator<String> iterator = keys.iterator();
            long[] position = {0L};
            boolean[] closed = {false};
            return (Cursor<String>) Proxy.newProxyInstance(
                    Cursor.class.getClassLoader(),
                    new Class<?>[]{Cursor.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "hasNext" -> iterator.hasNext();
                        case "next" -> {
                            position[0]++;
                            yield iterator.next();
                        }
                        case "close" -> {
                            closed[0] = true;
                            yield null;
                        }
                        case "isClosed" -> closed[0];
                        case "getPosition" -> position[0];
                        case "remove" -> {
                            iterator.remove();
                            yield null;
                        }
                        case "getCursorId" -> 0L;
                        case "getId" -> null;
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        @Override
        @SuppressWarnings("unchecked")
        public ZSetOperations<String, String> opsForZSet() {
            return (ZSetOperations<String, String>) Proxy.newProxyInstance(
                    ZSetOperations.class.getClassLoader(),
                    new Class<?>[]{ZSetOperations.class},
                    (proxy, method, args) -> {
                        if ("remove".equals(method.getName())) {
                            if (returnNullRemoval) {
                                return null;
                            }
                            String key = (String) args[0];
                            Object[] members = (Object[]) args[1];
                            Set<String> current = values.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
                            long removed = 0L;
                            for (Object member : members) {
                                if (current.remove(String.valueOf(member))) {
                                    removed++;
                                }
                            }
                            return removed;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private void put(String key, String... members) {
            values.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).addAll(List.of(members));
        }
    }
}
