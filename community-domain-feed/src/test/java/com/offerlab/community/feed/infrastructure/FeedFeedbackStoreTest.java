package com.offerlab.community.feed.infrastructure;

import com.offerlab.community.feed.infrastructure.persistence.mapper.FeedFeedbackPreferenceMapper;
import com.offerlab.community.feed.infrastructure.persistence.po.FeedFeedbackPreferencePO;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedFeedbackStoreTest {

    @Test
    void feedbackFactsAreIsolatedByAccountAndRestoreIsIdempotent() {
        InMemoryMapper mapper = new InMemoryMapper(true);
        FeedFeedbackStore store = new FeedFeedbackStore(null, mapper, new SnowflakeIdGenerator(1, 1));

        store.record(11L, 101L, "HIDE", "not relevant", 1);
        store.record(12L, 101L, "LESS_LIKE_THIS", "less tech", 1);

        assertEquals(java.util.Set.of(101L), store.hiddenPostIds(11L));
        assertEquals(java.util.Set.of(), store.hiddenPostIds(12L));
        assertEquals(java.util.Set.of(), store.lessLikedDomains(11L));
        assertEquals(java.util.Set.of(1), store.lessLikedDomains(12L));

        store.record(11L, 101L, "RESTORE", null, null);
        store.record(11L, 101L, "RESTORE", null, null);
        store.record(12L, 101L, "RESTORE", null, null);

        assertEquals(java.util.Set.of(), store.hiddenPostIds(11L));
        assertEquals(java.util.Set.of(), store.lessLikedDomains(12L));
    }

    @Test
    void preferenceListUsesUpdateTimeAndIdCursorWithoutExposingUid() throws Exception {
        InMemoryMapper mapper = new InMemoryMapper(true);
        FeedFeedbackStore store = new FeedFeedbackStore(null, mapper, new SnowflakeIdGenerator(1, 1));

        store.record(21L, 201L, "HIDE", "first", 1);
        Thread.sleep(2L);
        store.record(21L, 202L, "LESS_LIKE_THIS", "second", 2);

        var firstPage = store.list(21L, null, 1);
        assertTrue(firstPage.getHasMore());
        assertNotNull(firstPage.getNextCursor());
        assertEquals(202L, firstPage.getItems().get(0).getPostId());

        var secondPage = store.list(21L, firstPage.getNextCursor(), 1);
        assertFalse(secondPage.getHasMore());
        assertEquals(201L, secondPage.getItems().get(0).getPostId());
    }

    @Test
    void restoringOnePostKeepsAnotherControlForTheSameDomain() {
        InMemoryMapper mapper = new InMemoryMapper(true);
        FeedFeedbackStore store = new FeedFeedbackStore(null, mapper, new SnowflakeIdGenerator(1, 1));

        store.record(25L, 251L, "LESS_LIKE_THIS", "first", 2);
        store.record(25L, 252L, "LESS_LIKE_THIS", "second", 2);
        store.record(25L, 251L, "RESTORE", null, null);

        assertEquals(java.util.Set.of(2), store.lessLikedDomains(25L));

        store.record(25L, 252L, "RESTORE", null, null);
        assertEquals(java.util.Set.of(), store.lessLikedDomains(25L));
    }

    @Test
    void unavailablePersistenceCannotSilentlyAcceptFeedbackWrites() {
        FeedFeedbackStore store = new FeedFeedbackStore(
                null,
                new InMemoryMapper(false),
                new SnowflakeIdGenerator(1, 1));

        assertThrows(IllegalStateException.class,
                () -> store.record(31L, 301L, "HIDE", null, 1));
    }

    private static final class InMemoryMapper implements FeedFeedbackPreferenceMapper {
        private final boolean tableReady;
        private final Map<String, FeedFeedbackPreferencePO> rows = new LinkedHashMap<>();

        private InMemoryMapper(boolean tableReady) {
            this.tableReady = tableReady;
        }

        @Override
        public int upsert(FeedFeedbackPreferencePO preference) {
            String key = key(preference.getUid(), preference.getPostId());
            FeedFeedbackPreferencePO existing = rows.get(key);
            if (existing != null) {
                preference.setId(existing.getId());
                preference.setCreateTime(existing.getCreateTime());
            }
            rows.put(key, copy(preference));
            return 1;
        }

        @Override
        public int deleteByPost(Long uid, Long postId) {
            return rows.remove(key(uid, postId)) == null ? 0 : 1;
        }

        @Override
        public List<FeedFeedbackPreferencePO> listActive(Long uid,
                                                        LocalDateTime now,
                                                        LocalDateTime cursorTime,
                                                        Long cursorId,
                                                        int limit) {
            List<FeedFeedbackPreferencePO> result = new ArrayList<>();
            rows.values().stream()
                    .filter(row -> row.getUid().equals(uid))
                    .filter(row -> row.getExpiresAt().isAfter(now))
                    .filter(row -> cursorTime == null
                            || row.getUpdateTime().isBefore(cursorTime)
                            || (row.getUpdateTime().equals(cursorTime) && row.getId() < cursorId))
                    .sorted(Comparator.comparing(FeedFeedbackPreferencePO::getUpdateTime).reversed()
                            .thenComparing(FeedFeedbackPreferencePO::getId, Comparator.reverseOrder()))
                    .limit(limit)
                    .map(InMemoryMapper::copy)
                    .forEach(result::add);
            return result;
        }

        @Override
        public FeedFeedbackPreferencePO findActive(Long uid, Long postId, LocalDateTime now) {
            FeedFeedbackPreferencePO row = rows.get(key(uid, postId));
            return row != null && row.getExpiresAt().isAfter(now) ? copy(row) : null;
        }

        @Override
        public java.util.Set<Long> listActiveHiddenPostIds(Long uid, LocalDateTime now) {
            return rows.values().stream()
                    .filter(row -> row.getUid().equals(uid))
                    .filter(row -> "HIDE".equals(row.getAction()))
                    .filter(row -> row.getExpiresAt().isAfter(now))
                    .map(FeedFeedbackPreferencePO::getPostId)
                    .collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public java.util.Set<Long> listActiveReducedDomainIds(Long uid, LocalDateTime now) {
            return rows.values().stream()
                    .filter(row -> row.getUid().equals(uid))
                    .filter(row -> "LESS_LIKE_THIS".equals(row.getAction()))
                    .filter(row -> "DOMAIN".equals(row.getTargetType()))
                    .filter(row -> row.getExpiresAt().isAfter(now))
                    .map(FeedFeedbackPreferencePO::getTargetId)
                    .collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public int countOtherActiveDomainControls(Long uid,
                                                  Long postId,
                                                  Integer domain,
                                                  LocalDateTime now) {
            return (int) rows.values().stream()
                    .filter(row -> row.getUid().equals(uid))
                    .filter(row -> !row.getPostId().equals(postId))
                    .filter(row -> "LESS_LIKE_THIS".equals(row.getAction()))
                    .filter(row -> "DOMAIN".equals(row.getTargetType()))
                    .filter(row -> Long.valueOf(domain).equals(row.getTargetId()))
                    .filter(row -> row.getExpiresAt().isAfter(now))
                    .count();
        }

        @Override
        public int tableExists() {
            return tableReady ? 1 : 0;
        }

        private static String key(Long uid, Long postId) {
            return uid + ":" + postId;
        }

        private static FeedFeedbackPreferencePO copy(FeedFeedbackPreferencePO source) {
            FeedFeedbackPreferencePO target = new FeedFeedbackPreferencePO();
            target.setId(source.getId());
            target.setUid(source.getUid());
            target.setPostId(source.getPostId());
            target.setAction(source.getAction());
            target.setTargetType(source.getTargetType());
            target.setTargetId(source.getTargetId());
            target.setReason(source.getReason());
            target.setExpiresAt(source.getExpiresAt());
            target.setCreateTime(source.getCreateTime());
            target.setUpdateTime(source.getUpdateTime());
            return target;
        }
    }
}
