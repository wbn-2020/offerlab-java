package com.offerlab.community.feed.infrastructure;

import com.offerlab.community.feed.api.quality.QualitySignalWindow;
import com.offerlab.community.feed.infrastructure.persistence.mapper.FeedFeedbackPreferenceMapper;
import com.offerlab.community.feed.infrastructure.persistence.po.FeedFeedbackPreferencePO;
import com.offerlab.community.feed.infrastructure.persistence.po.FeedQualitySignalAggregateRow;
import com.offerlab.community.feed.infrastructure.persistence.po.FeedRevisionAwareQualitySignalAggregateRow;
import com.offerlab.community.feed.infrastructure.persistence.po.FeedRevisionAwareQualitySignalWindowQuery;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;

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

    @Test
    void controlReadFailuresFailClosedEvenAfterTheTableReadinessIsCached() {
        InMemoryMapper mapper = new InMemoryMapper(true);
        FeedFeedbackStore store = new FeedFeedbackStore(null, mapper, new SnowflakeIdGenerator(1, 1));

        store.record(35L, 351L, "HIDE", null, 1);
        assertEquals(Set.of(351L), store.hiddenPostIds(35L));
        assertTrue(store.controlsAvailable());

        mapper.failControlReads();

        assertThrows(IllegalStateException.class, () -> store.hiddenPostIds(35L));
        assertThrows(IllegalStateException.class, () -> store.lessLikedDomains(35L));
        assertThrows(IllegalStateException.class, () -> store.blockedAuthorIds(35L));
        assertFalse(store.controlsAvailable());
    }

    @Test
    void authorControlsArePersistentIdempotentPrivateAndRemovable() {
        InMemoryMapper mapper = new InMemoryMapper(true);
        FeedFeedbackStore store = new FeedFeedbackStore(null, mapper, new SnowflakeIdGenerator(1, 1));

        var first = store.blockAuthor(41L, 410L);
        var repeated = store.blockAuthor(41L, 410L);

        assertEquals(first.getId(), repeated.getId());
        assertEquals("AUTHOR", first.getControlType());
        assertEquals(410L, first.getTargetId());
        assertEquals("BLOCK_AUTHOR", first.getAction());
        assertEquals(Set.of(410L), store.blockedAuthorIds(41L));
        assertEquals(Set.of(), store.blockedAuthorIds(42L));

        var page = store.listControls(41L, null, 10);
        assertEquals(1, page.getItems().size());
        assertEquals(first.getId(), page.getItems().get(0).getId());
        assertEquals(null, page.getItems().get(0).getExpiresAt());

        store.unblockAuthor(41L, 410L);
        store.unblockAuthor(41L, 410L);

        assertEquals(Set.of(), store.blockedAuthorIds(41L));
        assertEquals(0, store.listControls(41L, null, 10).getItems().size());
    }

    @Test
    void genericControlRemovalUsesPrivateStableControlId() {
        InMemoryMapper mapper = new InMemoryMapper(true);
        FeedFeedbackStore store = new FeedFeedbackStore(null, mapper, new SnowflakeIdGenerator(1, 1));

        store.record(51L, 510L, "HIDE", "hide", 1);
        var page = store.listControls(51L, null, 10);
        assertEquals(1, page.getItems().size());

        store.deleteControl(51L, page.getItems().get(0).getId());
        store.deleteControl(51L, page.getItems().get(0).getId());

        assertEquals(Set.of(), store.hiddenPostIds(51L));
        assertEquals(0, store.listControls(51L, null, 10).getItems().size());
    }

    @Test
    void genericControlRemovalDoesNotDeleteAConcurrentReplacement() {
        InMemoryMapper mapper = new InMemoryMapper(true);
        FeedFeedbackStore store = new FeedFeedbackStore(null, mapper, new SnowflakeIdGenerator(1, 1));

        store.record(55L, 550L, "HIDE", "old", 1);
        Long controlId = store.listControls(55L, null, 10).getItems().get(0).getId();
        mapper.beforeConditionalDelete(() -> store.record(55L, 550L, "LESS_LIKE_THIS", "new", 9));

        store.deleteControl(55L, controlId);

        assertEquals(Set.of(9), store.lessLikedDomains(55L));
        var controls = store.listControls(55L, null, 10).getItems();
        assertEquals(1, controls.size());
        assertEquals("LESS_LIKE_THIS", controls.get(0).getAction());
        assertEquals(9L, controls.get(0).getTargetId());
    }

    @Test
    void qualitySignalAggregateKeepsOnlyEligibleCurrentFeedbackAndNeverReturnsIdentity() {
        InMemoryMapper mapper = new InMemoryMapper(true);
        FeedFeedbackStore store = new FeedFeedbackStore(null, mapper, new SnowflakeIdGenerator(1, 1));
        for (long uid = 1L; uid <= 5L; uid++) {
            store.record(uid, 600L, "HIDE", "v30:quality_not_expected", 1);
        }
        store.record(6L, 600L, "HIDE", "v30:not_relevant", 1);
        store.record(7L, 601L, "LESS_LIKE_THIS", "v30:quality_not_expected", 1);
        store.record(7L, 601L, "RESTORE", null, 1);

        var result = store.findActiveQualitySignals(
                List.of(600L, 601L), LocalDateTime.now().minusDays(30), LocalDateTime.now());

        assertTrue(result.available());
        assertEquals(1, result.items().size());
        assertEquals(600L, result.items().get(0).postId());
        assertEquals(5L, result.items().get(0).distinctReaderCount());
    }

    @Test
    void revisionAwareQualitySignalsSplitAtTheBoundaryExcludeAuthorAndKeepOnlyOpaqueAggregates() {
        InMemoryMapper mapper = new InMemoryMapper(true);
        FeedFeedbackStore store = new FeedFeedbackStore(null, mapper, new SnowflakeIdGenerator(1, 1));
        Instant base = Instant.parse("2026-08-01T00:00:00Z");
        Instant boundary = Instant.parse("2026-08-01T01:00:00Z");
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        LocalDateTime baseTime = LocalDateTime.ofInstant(base, java.time.ZoneOffset.UTC);
        LocalDateTime boundaryTime = LocalDateTime.ofInstant(boundary, java.time.ZoneOffset.UTC);
        LocalDateTime nowTime = LocalDateTime.ofInstant(now, java.time.ZoneOffset.UTC);

        mapper.putQualitySignal(101L, 700L, "HIDE", "v30:quality_not_expected",
                baseTime.plusMinutes(5), nowTime.plusDays(1));
        mapper.putQualitySignal(102L, 700L, "LESS_LIKE_THIS", "v30:quality_not_expected",
                boundaryTime, nowTime.plusDays(1));
        mapper.putQualitySignal(103L, 700L, "HIDE", "v30:quality_not_expected",
                boundaryTime.plusSeconds(1), nowTime.plusDays(1));
        mapper.putQualitySignal(70L, 700L, "HIDE", "v30:quality_not_expected",
                boundaryTime.plusSeconds(2), nowTime.plusDays(1));
        mapper.putQualitySignal(104L, 700L, "HIDE", "v30:quality_not_expected",
                boundaryTime.plusSeconds(3), nowTime);
        mapper.putQualitySignal(105L, 700L, "HIDE", "v30:quality_not_expected",
                baseTime.minusSeconds(1), nowTime.plusDays(1));
        mapper.putQualitySignal(201L, 701L, "HIDE", "v30:quality_not_expected",
                baseTime, nowTime.plusDays(1));
        mapper.putQualitySignal(202L, 701L, "LESS_LIKE_THIS", "v30:quality_not_expected",
                boundaryTime, nowTime.plusDays(1));
        mapper.putQualitySignal(71L, 701L, "HIDE", "v30:quality_not_expected",
                boundaryTime, nowTime.plusDays(1));

        var result = store.findRevisionAwareActiveQualitySignals(
                List.of(
                        new QualitySignalWindow(700L, 70L, boundary, true, "revision-2"),
                        new QualitySignalWindow(701L, 71L, null, false, "revision-1")),
                base,
                now);

        assertTrue(result.available());
        assertEquals(2, result.items().size());
        assertEquals(700L, result.items().get(0).postId());
        assertEquals("revision-2", result.items().get(0).revisionToken());
        assertEquals(2L, result.items().get(0).priorRevisionDistinctReaderCount());
        assertEquals(1L, result.items().get(0).currentRevisionDistinctReaderCount());
        assertEquals(701L, result.items().get(1).postId());
        assertEquals("revision-1", result.items().get(1).revisionToken());
        assertEquals(0L, result.items().get(1).priorRevisionDistinctReaderCount());
        assertEquals(2L, result.items().get(1).currentRevisionDistinctReaderCount());
    }

    @Test
    void revisionAwareQualitySignalsProcessEveryBatchWithoutTruncation() {
        InMemoryMapper mapper = new InMemoryMapper(true);
        FeedFeedbackStore store = new FeedFeedbackStore(null, mapper, new SnowflakeIdGenerator(1, 1));
        Instant base = Instant.parse("2026-08-01T00:00:00Z");
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        mapper.putQualitySignal(7L, 801L, "HIDE", "v30:quality_not_expected",
                LocalDateTime.ofInstant(base, java.time.ZoneOffset.UTC).plusSeconds(1),
                LocalDateTime.ofInstant(now, java.time.ZoneOffset.UTC).plusDays(1));
        List<QualitySignalWindow> windows = LongStream.rangeClosed(701L, 801L)
                .mapToObj(postId -> new QualitySignalWindow(postId, postId + 10_000L,
                        null, false, "revision-" + postId))
                .toList();

        var result = store.findRevisionAwareActiveQualitySignals(windows, base, now);

        assertTrue(result.available());
        assertEquals(101, result.items().size());
        assertEquals(List.of(100, 1), mapper.revisionAggregateBatchSizes());
        assertEquals(801L, result.items().get(100).postId());
        assertEquals(1L, result.items().get(100).currentRevisionDistinctReaderCount());
    }

    @Test
    void revisionAwareQualitySignalsReturnUnavailableWhenAnyBatchFails() {
        InMemoryMapper mapper = new InMemoryMapper(true);
        mapper.failRevisionAggregateCall(2);
        FeedFeedbackStore store = new FeedFeedbackStore(null, mapper, new SnowflakeIdGenerator(1, 1));
        Instant base = Instant.parse("2026-08-01T00:00:00Z");
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        List<QualitySignalWindow> windows = LongStream.rangeClosed(901L, 1001L)
                .mapToObj(postId -> new QualitySignalWindow(postId, postId + 10_000L,
                        null, false, "revision-" + postId))
                .toList();

        var result = store.findRevisionAwareActiveQualitySignals(windows, base, now);

        assertFalse(result.available());
        assertEquals(0, result.items().size());
        assertEquals(List.of(100, 1), mapper.revisionAggregateBatchSizes());
    }

    private static final class InMemoryMapper implements FeedFeedbackPreferenceMapper {
        private final boolean tableReady;
        private final Map<String, FeedFeedbackPreferencePO> rows = new LinkedHashMap<>();
        private final List<Integer> revisionAggregateBatchSizes = new ArrayList<>();
        private boolean controlReadFailure;
        private Runnable beforeConditionalDelete;
        private int revisionAggregateFailureCall;

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
        public List<FeedQualitySignalAggregateRow> aggregateActiveQualitySignals(
                List<Long> postIds, LocalDateTime since, LocalDateTime now) {
            return rows.values().stream()
                    .filter(row -> postIds.contains(row.getPostId()))
                    .filter(row -> "v30:quality_not_expected".equals(row.getReason()))
                    .filter(row -> "HIDE".equals(row.getAction()) || "LESS_LIKE_THIS".equals(row.getAction()))
                    .filter(row -> active(row, now))
                    .filter(row -> !row.getUpdateTime().isBefore(since))
                    .collect(java.util.stream.Collectors.groupingBy(FeedFeedbackPreferencePO::getPostId))
                    .entrySet().stream()
                    .map(entry -> {
                        FeedQualitySignalAggregateRow aggregate = new FeedQualitySignalAggregateRow();
                        aggregate.setPostId(entry.getKey());
                        aggregate.setDistinctReaderCount(entry.getValue().stream()
                                .map(FeedFeedbackPreferencePO::getUid)
                                .distinct()
                                .count());
                        aggregate.setLatestUpdatedAt(entry.getValue().stream()
                                .map(FeedFeedbackPreferencePO::getUpdateTime)
                                .max(LocalDateTime::compareTo)
                                .orElse(null));
                        return aggregate;
                    })
                    .toList();
        }

        @Override
        public List<FeedRevisionAwareQualitySignalAggregateRow> aggregateRevisionAwareActiveQualitySignals(
                List<FeedRevisionAwareQualitySignalWindowQuery> windows,
                LocalDateTime baseWindowStart,
                LocalDateTime now) {
            revisionAggregateBatchSizes.add(windows.size());
            if (revisionAggregateFailureCall == revisionAggregateBatchSizes.size()) {
                throw new IllegalStateException("simulated revision-aware aggregate failure");
            }
            return windows.stream()
                    .map(window -> {
                        List<FeedFeedbackPreferencePO> matches = rows.values().stream()
                                .filter(row -> window.getPostId().equals(row.getPostId()))
                                .filter(row -> !window.getAuthorId().equals(row.getUid()))
                                .filter(row -> "v30:quality_not_expected".equals(row.getReason()))
                                .filter(row -> "HIDE".equals(row.getAction())
                                        || "LESS_LIKE_THIS".equals(row.getAction()))
                                .filter(row -> active(row, now))
                                .filter(row -> !row.getUpdateTime().isBefore(baseWindowStart))
                                .toList();
                        if (matches.isEmpty()) {
                            return null;
                        }
                        FeedRevisionAwareQualitySignalAggregateRow aggregate =
                                new FeedRevisionAwareQualitySignalAggregateRow();
                        aggregate.setPostId(window.getPostId());
                        aggregate.setPriorRevisionDistinctReaderCount(matches.stream()
                                .filter(row -> window.isHasEffectiveRevision()
                                        && !row.getUpdateTime().isAfter(window.getWindowStart()))
                                .map(FeedFeedbackPreferencePO::getUid)
                                .distinct()
                                .count());
                        aggregate.setCurrentRevisionDistinctReaderCount(matches.stream()
                                .filter(row -> !window.isHasEffectiveRevision()
                                        || row.getUpdateTime().isAfter(window.getWindowStart()))
                                .map(FeedFeedbackPreferencePO::getUid)
                                .distinct()
                                .count());
                        return aggregate;
                    })
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        @Override
        public List<FeedQualitySignalAggregateRow> countQualifiedQualitySignalPostsByDomain(
                List<Integer> domainCodes,
                int minimumDistinctReaders,
                LocalDateTime since,
                LocalDateTime now) {
            return List.of();
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
                    .filter(row -> !"AUTHOR".equals(row.getTargetType()))
                    .filter(row -> active(row, now))
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
            return row != null && !"AUTHOR".equals(row.getTargetType()) && active(row, now) ? copy(row) : null;
        }

        @Override
        public java.util.Set<Long> listActiveHiddenPostIds(Long uid, LocalDateTime now) {
            failIfControlReadUnavailable();
            return rows.values().stream()
                    .filter(row -> row.getUid().equals(uid))
                    .filter(row -> "HIDE".equals(row.getAction()))
                    .filter(row -> "POST".equals(row.getTargetType()))
                    .filter(row -> active(row, now))
                    .map(FeedFeedbackPreferencePO::getPostId)
                    .collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public java.util.Set<Long> listActiveReducedDomainIds(Long uid, LocalDateTime now) {
            failIfControlReadUnavailable();
            return rows.values().stream()
                    .filter(row -> row.getUid().equals(uid))
                    .filter(row -> "LESS_LIKE_THIS".equals(row.getAction()))
                    .filter(row -> "DOMAIN".equals(row.getTargetType()))
                    .filter(row -> active(row, now))
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
                    .filter(row -> active(row, now))
                    .count();
        }

        @Override
        public Set<Long> listActiveBlockedAuthorIds(Long uid, LocalDateTime now) {
            failIfControlReadUnavailable();
            return rows.values().stream()
                    .filter(row -> row.getUid().equals(uid))
                    .filter(row -> "BLOCK_AUTHOR".equals(row.getAction()))
                    .filter(row -> "AUTHOR".equals(row.getTargetType()))
                    .filter(row -> active(row, now))
                    .map(FeedFeedbackPreferencePO::getTargetId)
                    .collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public List<FeedFeedbackPreferencePO> listActiveControls(Long uid,
                                                                  LocalDateTime now,
                                                                  LocalDateTime cursorTime,
                                                                  Long cursorId,
                                                                  int limit) {
            List<FeedFeedbackPreferencePO> result = new ArrayList<>();
            rows.values().stream()
                    .filter(row -> row.getUid().equals(uid))
                    .filter(row -> active(row, now))
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
        public FeedFeedbackPreferencePO findActiveAuthorControl(Long uid, Long authorUid, LocalDateTime now) {
            return rows.values().stream()
                    .filter(row -> row.getUid().equals(uid))
                    .filter(row -> "BLOCK_AUTHOR".equals(row.getAction()))
                    .filter(row -> "AUTHOR".equals(row.getTargetType()))
                    .filter(row -> authorUid.equals(row.getTargetId()))
                    .filter(row -> active(row, now))
                    .findFirst()
                    .map(InMemoryMapper::copy)
                    .orElse(null);
        }

        @Override
        public FeedFeedbackPreferencePO findOwnedControlById(Long uid, Long id) {
            return rows.values().stream()
                    .filter(row -> row.getUid().equals(uid))
                    .filter(row -> id.equals(row.getId()))
                    .findFirst()
                    .map(InMemoryMapper::copy)
                    .orElse(null);
        }

        @Override
        public int deleteOwnedControlIfUnchanged(Long uid, FeedFeedbackPreferencePO control) {
            Runnable hook = beforeConditionalDelete;
            beforeConditionalDelete = null;
            if (hook != null) {
                hook.run();
            }
            String key = rows.entrySet().stream()
                    .filter(entry -> entry.getValue().getUid().equals(uid))
                    .filter(entry -> control.getId().equals(entry.getValue().getId()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            FeedFeedbackPreferencePO current = key == null ? null : rows.get(key);
            if (!sameControl(current, control)) {
                return 0;
            }
            return rows.remove(key) == null ? 0 : 1;
        }

        @Override
        public int deleteAuthorControl(Long uid, Long authorUid) {
            List<String> keys = rows.entrySet().stream()
                    .filter(entry -> entry.getValue().getUid().equals(uid))
                    .filter(entry -> "BLOCK_AUTHOR".equals(entry.getValue().getAction()))
                    .filter(entry -> "AUTHOR".equals(entry.getValue().getTargetType()))
                    .filter(entry -> authorUid.equals(entry.getValue().getTargetId()))
                    .map(Map.Entry::getKey)
                    .toList();
            keys.forEach(rows::remove);
            return keys.size();
        }

        @Override
        public int tableExists() {
            return tableReady ? 1 : 0;
        }

        private void failControlReads() {
            controlReadFailure = true;
        }

        private void beforeConditionalDelete(Runnable hook) {
            beforeConditionalDelete = hook;
        }

        private void putQualitySignal(Long uid,
                                      Long postId,
                                      String action,
                                      String reason,
                                      LocalDateTime updateTime,
                                      LocalDateTime expiresAt) {
            FeedFeedbackPreferencePO preference = new FeedFeedbackPreferencePO();
            preference.setId((long) rows.size() + 1);
            preference.setUid(uid);
            preference.setPostId(postId);
            preference.setAction(action);
            preference.setTargetType("POST");
            preference.setTargetId(postId);
            preference.setReason(reason);
            preference.setExpiresAt(expiresAt);
            preference.setCreateTime(updateTime);
            preference.setUpdateTime(updateTime);
            rows.put(key(uid, postId), preference);
        }

        private void failRevisionAggregateCall(int callNumber) {
            revisionAggregateFailureCall = callNumber;
        }

        private List<Integer> revisionAggregateBatchSizes() {
            return List.copyOf(revisionAggregateBatchSizes);
        }

        private void failIfControlReadUnavailable() {
            if (controlReadFailure) {
                throw new IllegalStateException("simulated control read failure");
            }
        }

        private static boolean sameControl(FeedFeedbackPreferencePO left, FeedFeedbackPreferencePO right) {
            return left != null
                    && java.util.Objects.equals(left.getId(), right.getId())
                    && java.util.Objects.equals(left.getPostId(), right.getPostId())
                    && java.util.Objects.equals(left.getAction(), right.getAction())
                    && java.util.Objects.equals(left.getTargetType(), right.getTargetType())
                    && java.util.Objects.equals(left.getTargetId(), right.getTargetId())
                    && java.util.Objects.equals(left.getReason(), right.getReason())
                    && java.util.Objects.equals(left.getExpiresAt(), right.getExpiresAt())
                    && java.util.Objects.equals(left.getUpdateTime(), right.getUpdateTime());
        }

        private static boolean active(FeedFeedbackPreferencePO row, LocalDateTime now) {
            return row.getExpiresAt() == null || row.getExpiresAt().isAfter(now);
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
