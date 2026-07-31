package com.offerlab.community.post.collaboration.application;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.post.collaboration.api.CollaborationActionItemDTO;
import com.offerlab.community.post.collaboration.api.CollaborationActionSummaryDTO;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationActionQueryMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationActionQueryRows;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollaborationActionQueryServiceTest {

    @Test
    void summaryUsesFixedActionVocabularyAndMapperCounts() {
        FakeActionMapper mapper = new FakeActionMapper();
        CollaborationActionQueryService service = new CollaborationActionQueryService(mapper);

        CollaborationActionSummaryDTO summary = service.summary(42L);

        assertEquals(6, summary.getTotal());
        assertEquals(2, summary.getCounts().get("NEED_SUBMIT"));
        assertEquals(1, summary.getCounts().get("NEED_REVISE"));
        assertEquals(0, summary.getCounts().get("NEED_REVIEW"));
        assertEquals(3, summary.getCounts().get("OFFICE_HOUR_REVIEW"));
        assertFalse(summary.getDegraded());
    }

    @Test
    void listMapsActionRowsAndEmitsAKeysetCursor() {
        FakeActionMapper mapper = new FakeActionMapper();
        mapper.rows = List.of(action(9L, "NEED_SUBMIT"), action(8L, "NEED_SUBMIT"));
        CollaborationActionQueryService service = new CollaborationActionQueryService(mapper);

        PageResult<CollaborationActionItemDTO> result = service.list(42L, "need_submit", "0", 1);

        assertEquals(1, result.getItems().size());
        assertEquals(9L, result.getItems().get(0).getSourceId());
        assertEquals("NEED_SUBMIT", result.getItems().get(0).getActionType());
        assertTrue(result.getItems().get(0).getCanAct());
        assertTrue(result.getHasMore());
        assertTrue(result.getNextCursor() != null && !result.getNextCursor().isBlank());
        assertEquals("NEED_SUBMIT", mapper.lastActionType);
    }

    private static CollaborationActionQueryRows.ActionRow action(Long id, String type) {
        CollaborationActionQueryRows.ActionRow row = new CollaborationActionQueryRows.ActionRow();
        row.setActionType(type);
        row.setSourceType("NEED");
        row.setSourceId(id);
        row.setSourceStatus("CLAIMED");
        row.setTitle("Need " + id);
        row.setReason("NEED_READY_FOR_SUBMISSION");
        row.setTargetPath("/collaboration/needs/" + id);
        row.setUpdatedAt(LocalDateTime.of(2026, 7, 19, 12, 0).minusMinutes(id));
        row.setCanAct(1);
        return row;
    }

    private static final class FakeActionMapper implements CollaborationActionQueryMapper {
        private List<CollaborationActionQueryRows.ActionRow> rows = List.of();
        private String lastActionType;

        @Override
        public List<CollaborationActionQueryRows.ActionRow> listActions(
                Long uid, String actionType, LocalDateTime cursorTime, Long cursorId,
                String cursorActionType, int limit) {
            this.lastActionType = actionType;
            return rows;
        }

        @Override
        public List<CollaborationActionQueryRows.ActionCountRow> countActions(Long uid) {
            return List.of(count("NEED_SUBMIT", 2), count("NEED_REVISE", 1),
                    count("OFFICE_HOUR_REVIEW", 3));
        }

        private static CollaborationActionQueryRows.ActionCountRow count(String type, int value) {
            CollaborationActionQueryRows.ActionCountRow row = new CollaborationActionQueryRows.ActionCountRow();
            row.setActionType(type);
            row.setCount(value);
            return row;
        }
    }
}
