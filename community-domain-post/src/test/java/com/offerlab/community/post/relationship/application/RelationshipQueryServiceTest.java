package com.offerlab.community.post.relationship.application;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.post.relationship.api.RelationshipItemDTO;
import com.offerlab.community.post.relationship.api.RelationshipSummaryDTO;
import com.offerlab.community.post.relationship.infrastructure.persistence.RelationshipMapper;
import com.offerlab.community.post.relationship.infrastructure.persistence.RelationshipRows.RelationshipCountRow;
import com.offerlab.community.post.relationship.infrastructure.persistence.RelationshipRows.RelationshipRow;
import com.offerlab.community.user.api.UserRelationshipReadFacade;
import com.offerlab.community.user.api.dto.UserRelationshipItemDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationshipQueryServiceTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 7, 19, 12, 0);

    @Test
    void listMergesSourcesAndEmitsCompositeKeysetCursor() {
        FakeRelationshipMapper mapper = new FakeRelationshipMapper();
        mapper.rows = List.of(postRow("TOPIC", 90L, BASE.minusMinutes(1)));
        FakeUserFacade users = new FakeUserFacade();
        users.rows = List.of(userRow(80L, BASE.minusMinutes(2)));
        RelationshipQueryService service = new RelationshipQueryService(mapper, users);

        PageResult<RelationshipItemDTO> result = service.list(42L, null, "active", "0", 1);

        assertEquals(1, result.getItems().size());
        assertEquals("TOPIC", result.getItems().get(0).getSourceType());
        assertTrue(result.getHasMore());
        assertNotNull(result.getNextCursor());
        assertEquals(42L, mapper.lastUid);
        assertEquals(42L, users.lastUid);
        assertEquals("ACTIVE", mapper.lastMode);
        assertEquals("ACTIVE", users.lastMode);
        assertEquals("IMMEDIATE", result.getItems().get(0).getDeliveryMode());

        service.list(42L, null, "active", result.getNextCursor(), 1);

        assertEquals(BASE.minusMinutes(1), mapper.lastCursorTime);
        assertEquals(90L, mapper.lastCursorId);
        assertEquals("TOPIC", mapper.lastCursorSourceType);
        assertEquals(BASE.minusMinutes(1), users.lastCursorTime);
        assertEquals(90L, users.lastCursorId);
        assertEquals("TOPIC", users.lastCursorSourceType);
    }

    @Test
    void sourceFilterAndAccountUidStayServerSide() {
        FakeRelationshipMapper mapper = new FakeRelationshipMapper();
        FakeUserFacade users = new FakeUserFacade();
        RelationshipQueryService service = new RelationshipQueryService(mapper, users);

        service.list(101L, "need", "all", "0", 20);

        assertEquals(101L, mapper.lastUid);
        assertEquals("NEED", mapper.lastSourceType);
        assertEquals(0, users.calls);

        service.list(202L, "user", "all", "0", 20);

        assertEquals(202L, users.lastUid);
        assertEquals(1, users.calls);
        assertEquals("NEED", mapper.lastSourceType);
    }

    @Test
    void mutedModeStillUsesTheServerSidePreferenceFilter() {
        FakeRelationshipMapper mapper = new FakeRelationshipMapper();
        FakeUserFacade users = new FakeUserFacade();
        RelationshipQueryService service = new RelationshipQueryService(mapper, users);

        PageResult<RelationshipItemDTO> result = service.list(42L, null, "muted", "0", 20);

        assertTrue(result.getItems().isEmpty());
        assertEquals(1, mapper.listCalls);
        assertEquals(1, users.calls);
        assertEquals("MUTED", mapper.lastMode);
        assertEquals("MUTED", users.lastMode);
    }

    @Test
    void summaryCombinesOnlyVisibleRelationFacts() {
        FakeRelationshipMapper mapper = new FakeRelationshipMapper();
        mapper.counts = List.of(count("TOPIC", 2), count("NEED", 3), count("ACTIVITY", 99));
        FakeUserFacade users = new FakeUserFacade();
        users.count = 4;
        RelationshipQueryService service = new RelationshipQueryService(mapper, users);

        RelationshipSummaryDTO summary = service.summary(42L);

        assertEquals(9L, summary.getTotal());
        assertEquals(4L, summary.getCounts().get("USER"));
        assertEquals(2L, summary.getCounts().get("TOPIC"));
        assertEquals(3L, summary.getCounts().get("NEED"));
        assertFalse(summary.getCounts().containsKey("ACTIVITY"));
        assertEquals(0L, summary.getMutedCount());
        assertEquals(9L, summary.getImmediate());
    }

    @Test
    void invalidSourceModeAndCursorAreRejected() {
        RelationshipQueryService service = new RelationshipQueryService(
                new FakeRelationshipMapper(), new FakeUserFacade());

        assertThrows(RuntimeException.class,
                () -> service.list(42L, "unknown", "all", "0", 20));
        assertThrows(RuntimeException.class,
                () -> service.list(42L, "user", "unknown", "0", 20));
        assertThrows(RuntimeException.class,
                () -> service.list(42L, "user", "all", "broken", 20));
    }

    private static UserRelationshipItemDTO userRow(long id, LocalDateTime time) {
        return UserRelationshipItemDTO.builder()
                .relationId(id)
                .uid(id + 1000)
                .nickname("User " + id)
                .bio("Bio")
                .relationTime(time)
                .lastPublicUpdateAt(time)
                .build();
    }

    private static RelationshipRow postRow(String sourceType, long id, LocalDateTime time) {
        RelationshipRow row = new RelationshipRow();
        row.setSourceType(sourceType);
        row.setSourceId(id + 1000);
        row.setRelationId(id);
        row.setTitle(sourceType + " title");
        row.setSummary("summary");
        row.setTargetPath("/" + sourceType.toLowerCase());
        row.setRelationStatus("FOLLOWING");
        row.setSourceStatus("ACTIVE");
        row.setLastPublicUpdateAt(time);
        row.setRelationTime(time);
        return row;
    }

    private static RelationshipCountRow count(String sourceType, long value) {
        RelationshipCountRow row = new RelationshipCountRow();
        row.setSourceType(sourceType);
        row.setDeliveryMode("IMMEDIATE");
        row.setCount(value);
        return row;
    }

    private static final class FakeRelationshipMapper implements RelationshipMapper {
        private List<RelationshipRow> rows = List.of();
        private List<RelationshipCountRow> counts = List.of();
        private Long lastUid;
        private String lastSourceType;
        private LocalDateTime lastCursorTime;
        private Long lastCursorId;
        private String lastCursorSourceType;
        private String lastMode;
        private int listCalls;

        @Override
        public List<RelationshipRow> listPostRelationships(Long uid, String sourceType,
                                                             LocalDateTime cursorTime, Long cursorId,
                                                             String cursorSourceType, String mode, int limit) {
            lastUid = uid;
            lastSourceType = sourceType;
            lastCursorTime = cursorTime;
            lastCursorId = cursorId;
            lastCursorSourceType = cursorSourceType;
            lastMode = mode;
            listCalls++;
            return rows;
        }

        @Override
        public int existsPostRelationship(Long uid, String sourceType, Long sourceId) {
            return 0;
        }

        @Override
        public List<RelationshipCountRow> countPostRelationships(Long uid) {
            lastUid = uid;
            return counts;
        }
    }

    private static final class FakeUserFacade implements UserRelationshipReadFacade {
        private List<UserRelationshipItemDTO> rows = List.of();
        private Long lastUid;
        private LocalDateTime lastCursorTime;
        private Long lastCursorId;
        private String lastCursorSourceType;
        private String lastMode;
        private int calls;
        private long count;

        @Override
        public List<UserRelationshipItemDTO> listFollowing(Long uid, LocalDateTime cursorTime,
                                                            Long cursorId, String cursorSourceType,
                                                            String mode, int limit) {
            lastUid = uid;
            lastCursorTime = cursorTime;
            lastCursorId = cursorId;
            lastCursorSourceType = cursorSourceType;
            lastMode = mode;
            calls++;
            return rows;
        }

        @Override
        public long countFollowing(Long uid) {
            lastUid = uid;
            return count;
        }

        @Override
        public Map<String, Long> countFollowingByDeliveryMode(Long uid) {
            lastUid = uid;
            return Map.of("IMMEDIATE", count, "DIGEST", 0L, "MUTED", 0L);
        }
    }
}
