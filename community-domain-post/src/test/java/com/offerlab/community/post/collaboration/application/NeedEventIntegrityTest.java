package com.offerlab.community.post.collaboration.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.post.collaboration.api.CollaborationModels.NeedEventTimelineDTO;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationRows.NeedEventRow;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationRows.NeedRow;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeedEventIntegrityTest {

    @Test
    void timelineReportsAUserSafeIntegrityWarningWithoutInternalCounts() {
        AtomicReference<Object[]> listArgs = new AtomicReference<>();
        CollaborationMapper mapper = mapper((method, args) -> switch (method) {
            case "selectNeed" -> need(91L, 7L);
            case "listNeedEvents" -> {
                listArgs.set(args.clone());
                yield List.of(event(30L), event(20L));
            }
            case "countVisibleNeedEvents" -> 4L;
            case "countInvalidVisibleNeedEvents" -> 1L;
            case "listInvalidVisibleNeedEventIds" -> List.of(11L);
            default -> throw new UnsupportedOperationException(method);
        });

        NeedEventTimelineDTO result = service(mapper).listNeedEvents(91L, 7L, 0, 1);

        assertEquals(1, result.getItems().size());
        assertEquals(3L, result.getTotal());
        assertTrue(result.getHasMore());
        assertEquals("30", result.getNextCursor());
        assertTrue(result.getHistoryIntegrityWarning());
        assertNull(result.getDiagnostics());
        assertTrue(result.getItems().get(0).getHasActor());
        assertEquals(91L, listArgs.get()[0]);
        assertEquals(7L, listArgs.get()[1]);
        assertEquals(1, listArgs.get()[2]);
        assertEquals(0L, listArgs.get()[3]);
        assertEquals(2, listArgs.get()[4]);
    }

    @Test
    void emptyTimelineReturnsAnEmptyPageWithoutAnIntegrityWarning() {
        CollaborationMapper mapper = mapper((method, args) -> switch (method) {
            case "selectNeed" -> need(91L, 7L);
            case "listNeedEvents" -> null;
            case "countVisibleNeedEvents" -> 0L;
            case "countInvalidVisibleNeedEvents" -> 0L;
            default -> throw new UnsupportedOperationException(method);
        });

        NeedEventTimelineDTO result = service(mapper).listNeedEvents(91L, null, 0, 20);

        assertTrue(result.getItems().isEmpty());
        assertEquals(0L, result.getTotal());
        assertFalse(result.getHasMore());
        assertEquals(null, result.getNextCursor());
        assertFalse(result.getHistoryIntegrityWarning());
        assertNull(result.getDiagnostics());
    }

    @Test
    void negativeTimelineCursorIsRejectedInsteadOfSilentlyReset() {
        CollaborationMapper mapper = mapper((method, args) -> {
            throw new UnsupportedOperationException(method);
        });

        BizException error = assertThrows(BizException.class,
                () -> service(mapper).listNeedEvents(91L, 7L, -1, 20));

        assertEquals(10001, error.getCode());
        assertEquals("cursor must not be negative", error.getMessage());
    }

    @Test
    void completeTimelineReconciliationDoesNotExposeAWarning() {
        CollaborationMapper mapper = mapper((method, args) -> switch (method) {
            case "selectNeed" -> need(91L, 7L);
            case "listNeedEvents" -> List.of(event(10L));
            case "countVisibleNeedEvents" -> 1L;
            case "countInvalidVisibleNeedEvents" -> 0L;
            default -> throw new UnsupportedOperationException(method);
        });

        NeedEventTimelineDTO result = service(mapper).listNeedEvents(91L, 7L, 0, 20);

        assertEquals(1L, result.getTotal());
        assertFalse(result.getHistoryIntegrityWarning());
        assertNull(result.getDiagnostics());
    }

    @Test
    void publicTimelineEventDoesNotExposeParticipantOrTargetIdentifiers() {
        CollaborationMapper mapper = mapper((method, args) -> switch (method) {
            case "selectNeed" -> need(91L, 7L);
            case "listNeedEvents" -> List.of(event(10L));
            case "countVisibleNeedEvents" -> 1L;
            case "countInvalidVisibleNeedEvents" -> 0L;
            default -> throw new UnsupportedOperationException(method);
        });

        NeedEventTimelineDTO result = service(mapper).listNeedEvents(91L, 7L, 0, 20);
        Object publicEvent = result.getItems().get(0);
        List<String> fieldNames = java.util.Arrays.stream(publicEvent.getClass().getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toList();

        assertTrue(fieldNames.contains("hasActor"));
        assertFalse(fieldNames.contains("actorUid"));
        assertFalse(fieldNames.contains("needId"));
        assertFalse(fieldNames.contains("targetType"));
        assertFalse(fieldNames.contains("targetId"));
        assertFalse(fieldNames.contains("visibilityScope"));
    }

    private static CollaborationMapper mapper(MapperBehavior behavior) {
        return (CollaborationMapper) Proxy.newProxyInstance(
                NeedEventIntegrityTest.class.getClassLoader(),
                new Class<?>[] {CollaborationMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existingTableCount" -> 20;
                    case "existingCriticalColumnCount" -> 60;
                    case "toString" -> "CollaborationMapperStub";
                    case "hashCode" -> 0;
                    case "equals" -> proxy == args[0];
                    default -> behavior.invoke(method.getName(), args);
                });
    }

    private static CollaborationService service(CollaborationMapper mapper) {
        return new CollaborationService(mapper, null, null, null, null, null, null, null, null);
    }

    private static NeedRow need(Long id, Long creatorUid) {
        NeedRow row = new NeedRow();
        row.setId(id);
        row.setCreatorUid(creatorUid);
        row.setDomain(3);
        row.setStatus("OPEN");
        return row;
    }

    private static NeedEventRow event(Long id) {
        NeedEventRow row = new NeedEventRow();
        row.setId(id);
        row.setNeedId(91L);
        row.setEventType("CREATED");
        row.setActorUid(7L);
        row.setCreateTime(LocalDateTime.of(2026, 8, 14, 12, 0));
        return row;
    }

    @FunctionalInterface
    private interface MapperBehavior {
        Object invoke(String method, Object[] args);
    }
}
