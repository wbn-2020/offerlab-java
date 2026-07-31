package com.offerlab.community.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.interaction.api.RevisitReadFacade;
import com.offerlab.community.interaction.api.dto.RevisitReadStateDTO;
import com.offerlab.community.notification.api.dto.UpdateDigestItemDTO;
import com.offerlab.community.notification.infrastructure.persistence.mapper.SubscriptionUpdateDigestMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.SubscriptionUpdateDigestPO;
import com.offerlab.community.post.api.PublicUpdateResourceFacade;
import com.offerlab.community.post.api.dto.PublicUpdateResourceDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateDigestReadModelTest {

    @Test
    void readsDigestFactsAggregatesThemAndReturnsNewAndLegacyCompatibleFields() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 9, 0);
        List<SubscriptionUpdateDigestPO> rows = List.of(
                row(30L, "DISCUSSION", 700L, "POST", 501L,
                        "DISCUSSION_COMMENT_CREATED", "comment:2", now, "Second public reply"),
                row(20L, "DISCUSSION", 700L, "POST", 501L,
                        "DISCUSSION_COMMENT_CREATED", "comment:1", now.minusMinutes(10), "First public reply"),
                row(10L, "TOPIC", 701L, "POST", 502L,
                        "TOPIC_POST_PUBLISHED", "post:502", now.minusMinutes(20), "Another update")
        );
        AtomicReference<Object[]> mapperArgs = new AtomicReference<>();
        SubscriptionUpdateDigestMapper mapper = proxy((method, args) -> switch (method) {
            case "tableExists" -> 1;
            case "requiredColumnCount" -> 14;
            case "listByReceiver" -> {
                mapperArgs.set(args);
                yield rows;
            }
            default -> defaultValue(returnType(method));
        });
        UpdateDigestQueryService service = service(mapper, publicResources(now), revisits());

        PageResult<UpdateDigestItemDTO> page = service.list(
                8L, "POST", "501", "DISCUSSION", "700", false, "0", 20);

        assertEquals(1, page.getItems().size());
        UpdateDigestItemDTO item = page.getItems().get(0);
        assertEquals("UPDATE_DIGEST", item.getProjectionType());
        assertEquals("DISCUSSION", item.getSubscriptionSourceType());
        assertEquals("700", item.getSubscriptionSourceId());
        assertEquals("POST", item.getResourceType());
        assertEquals("501", item.getResourceId());
        assertEquals("POST", item.getSourceType());
        assertEquals("501", item.getSourceId());
        assertEquals(2, item.getOccurrenceCount());
        assertEquals(List.of(30L, 20L), item.getDigestIds());
        assertEquals(List.of(), item.getNotificationIds());
        assertFalse(item.isNotificationUnread());
        assertEquals("comment:2", item.getEventId());
        assertEquals("Second public reply", item.getSummary());
        assertNotNull(item.getRevisit());
        assertEquals("OPEN", item.getRevisit().getStatus());
        assertEquals("t_subscription_update_digest", page.getDiagnostics().get("digestReadModel"));

        Object[] sqlArgs = mapperArgs.get();
        assertEquals(8L, sqlArgs[0]);
        assertEquals("POST", sqlArgs[1]);
        assertEquals(501L, sqlArgs[2]);
        assertEquals("DISCUSSION", sqlArgs[3]);
        assertEquals(700L, sqlArgs[4]);
    }

    @Test
    void rejectsLegacyUnreadOnlyInsteadOfSilentlyReturningUnfilteredFacts() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 9, 0);
        SubscriptionUpdateDigestMapper mapper = proxy((method, args) -> switch (method) {
            case "tableExists" -> 1;
            case "requiredColumnCount" -> 14;
            default -> defaultValue(returnType(method));
        });
        UpdateDigestQueryService service = service(mapper, publicResources(now), (uid, keys) -> Map.of());

        BizException exception = assertThrows(BizException.class,
                () -> service.list(8L, null, null, true, "0", 20));

        assertEquals("更新摘要不支持 unreadOnly 筛选；摘要没有独立已读状态", exception.getMessage());
    }

    @Test
    void revalidatesVisibilityAndPreservesCursorPaginationWithoutNotificationRows() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 9, 0);
        SubscriptionUpdateDigestPO newest = row(
                30L, "DISCUSSION", 700L, "POST", 501L,
                "DISCUSSION_COMMENT_CREATED", "comment:2", now, "Newest");
        SubscriptionUpdateDigestPO older = row(
                20L, "TOPIC", 701L, "POST", 502L,
                "TOPIC_POST_PUBLISHED", "post:502", now.minusMinutes(5), "Older");
        AtomicReference<LocalDateTime> cursorTime = new AtomicReference<>();
        SubscriptionUpdateDigestMapper mapper = proxy((method, args) -> switch (method) {
            case "tableExists" -> 1;
            case "requiredColumnCount" -> 14;
            case "listByReceiver" -> {
                cursorTime.set((LocalDateTime) args[5]);
                yield args[5] == null ? List.of(newest, older) : List.of(older);
            }
            default -> defaultValue(returnType(method));
        });
        PublicUpdateResourceFacade publicResources = (type, id, fallbackPostId, path) -> {
            if ("501".equals(id)) {
                return null;
            }
            return PublicUpdateResourceDTO.builder()
                    .sourceType("POST")
                    .sourceId(id)
                    .postId(Long.parseLong(id))
                    .title("Public post " + id)
                    .canonicalPath("/post/" + id)
                    .updatedAt(now)
                    .build();
        };
        UpdateDigestQueryService service = service(mapper, publicResources, (uid, keys) -> Map.of());

        PageResult<UpdateDigestItemDTO> first = service.list(8L, null, null, false, "0", 1);
        assertEquals(1, first.getItems().size());
        assertEquals("502", first.getItems().get(0).getResourceId());
        assertFalse(Boolean.TRUE.equals(first.getHasMore()));
        assertNull(first.getNextCursor());
        assertNull(cursorTime.get());

        PageResult<UpdateDigestItemDTO> hiddenOnly = service.list(
                8L, "POST", "501", false, "0", 1);
        assertTrue(hiddenOnly.getItems().isEmpty());
        assertEquals(2, hiddenOnly.getDiagnostics().get("filteredCount"));
    }

    @Test
    void stopsAtTheNextAggregateAndUsesFactOccurredAtForTheCursor() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 9, 0);
        SubscriptionUpdateDigestPO newest = row(
                30L, "DISCUSSION", 700L, "POST", 501L,
                "DISCUSSION_COMMENT_CREATED", "comment:2", now, "Newest");
        SubscriptionUpdateDigestPO older = row(
                20L, "TOPIC", 701L, "POST", 502L,
                "TOPIC_POST_PUBLISHED", "post:502", now.minusMinutes(5), "Older");
        AtomicReference<LocalDateTime> cursorTime = new AtomicReference<>();
        SubscriptionUpdateDigestMapper mapper = proxy((method, args) -> switch (method) {
            case "tableExists" -> 1;
            case "requiredColumnCount" -> 14;
            case "listByReceiver" -> {
                cursorTime.set((LocalDateTime) args[5]);
                yield args[5] == null ? List.of(newest, older) : List.of(older);
            }
            default -> defaultValue(returnType(method));
        });
        UpdateDigestQueryService service = service(mapper, publicResources(now), (uid, keys) -> Map.of());

        PageResult<UpdateDigestItemDTO> first = service.list(8L, null, null, false, "0", 1);
        assertEquals(1, first.getItems().size());
        assertEquals("501", first.getItems().get(0).getResourceId());
        assertTrue(Boolean.TRUE.equals(first.getHasMore()));
        assertEquals(now.toInstant(ZoneOffset.UTC).toEpochMilli() + ":30", first.getNextCursor());

        PageResult<UpdateDigestItemDTO> second = service.list(
                8L, null, null, false, first.getNextCursor(), 1);
        assertEquals(1, second.getItems().size());
        assertEquals("502", second.getItems().get(0).getResourceId());
        assertEquals(now, cursorTime.get());
    }

    private static UpdateDigestQueryService service(SubscriptionUpdateDigestMapper mapper,
                                                    PublicUpdateResourceFacade publicResources,
                                                    RevisitReadFacade revisits) {
        ObjectMapper objectMapper = new ObjectMapper();
        SubscriptionUpdateDigestService digestService = new SubscriptionUpdateDigestService(
                mapper, new IncrementingIdGenerator(), objectMapper);
        return new UpdateDigestQueryService(
                mapper, digestService, objectMapper, publicResources, revisits);
    }

    private static PublicUpdateResourceFacade publicResources(LocalDateTime now) {
        return (type, id, fallbackPostId, path) -> PublicUpdateResourceDTO.builder()
                .sourceType(type)
                .sourceId(id)
                .postId("POST".equals(type) ? Long.parseLong(id) : fallbackPostId)
                .title("Public resource " + id)
                .canonicalPath("/post/" + id)
                .updatedAt(now)
                .build();
    }

    private static RevisitReadFacade revisits() {
        return (uid, keys) -> Map.of(
                "POST:501",
                RevisitReadStateDTO.builder()
                        .itemId(11L)
                        .resourceKey("POST:501")
                        .status("OPEN")
                        .targetPath("/post/501")
                        .build()
        );
    }

    private static SubscriptionUpdateDigestPO row(Long id,
                                                  String sourceType,
                                                  Long sourceId,
                                                  String resourceType,
                                                  Long resourceId,
                                                  String eventType,
                                                  String eventKey,
                                                  LocalDateTime occurredAt,
                                                  String summary) {
        SubscriptionUpdateDigestPO row = new SubscriptionUpdateDigestPO();
        row.setId(id);
        row.setReceiverUid(8L);
        row.setSourceType(sourceType);
        row.setSourceId(sourceId);
        row.setResourceType(resourceType);
        row.setResourceId(resourceId);
        row.setEventType(eventType);
        row.setEventKey(eventKey);
        row.setActorUid(9L);
        row.setPayloadJson("""
                {
                  "summary": "%s",
                  "targetPath": "/post/%d#comments",
                  "postId": %d
                }
                """.formatted(summary, resourceId, resourceId));
        row.setOccurredAt(occurredAt);
        row.setIsDeleted(0);
        return row;
    }

    @SuppressWarnings("unchecked")
    private static SubscriptionUpdateDigestMapper proxy(Invocation invocation) {
        return (SubscriptionUpdateDigestMapper) Proxy.newProxyInstance(
                SubscriptionUpdateDigestMapper.class.getClassLoader(),
                new Class<?>[]{SubscriptionUpdateDigestMapper.class},
                (proxy, method, args) -> {
                    if ("toString".equals(method.getName())) {
                        return "SubscriptionUpdateDigestMapperStub";
                    }
                    return invocation.invoke(method.getName(), args);
                });
    }

    private static Class<?> returnType(String name) {
        for (var method : SubscriptionUpdateDigestMapper.class.getMethods()) {
            if (method.getName().equals(name)) {
                return method.getReturnType();
            }
        }
        return Object.class;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args);
    }

    private static final class IncrementingIdGenerator extends SnowflakeIdGenerator {
        private long value;

        @Override
        public synchronized long nextId() {
            return ++value;
        }
    }
}
