package com.offerlab.community.notification.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.notification.infrastructure.persistence.mapper.SubscriptionUpdateDigestMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.SubscriptionUpdateDigestPO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionUpdateDigestServiceTest {

    @Test
    void recordUsesDigestFactIdempotencyAndPersistsOnlyAllowedPayloadFields() throws Exception {
        AtomicReference<SubscriptionUpdateDigestPO> inserted = new AtomicReference<>();
        AtomicInteger insertCalls = new AtomicInteger();
        SubscriptionUpdateDigestMapper mapper = proxy((method, args) -> switch (method) {
            case "tableExists" -> 1;
            case "requiredColumnCount" -> 14;
            case "insertIgnore" -> {
                insertCalls.incrementAndGet();
                inserted.set((SubscriptionUpdateDigestPO) args[0]);
                yield 1;
            }
            default -> defaultValue(returnType(method));
        });
        SubscriptionUpdateDigestService service = new SubscriptionUpdateDigestService(
                mapper, new IncrementingIdGenerator(), new ObjectMapper());
        Instant occurredAt = Instant.parse("2026-07-31T08:30:00Z");

        SubscriptionUpdateDigestRecordResult result = service.record(
                new SubscriptionUpdateDigestCommand(
                        8L,
                        "discussion",
                        99L,
                        "post",
                        501L,
                        "discussion_comment_created",
                        "comment:501",
                        9L,
                        Map.of(
                                "summary", "  A new public reply  ",
                                "targetPath", "/post/501#comments",
                                "postId", "501",
                                "rawBody", "must not be persisted",
                                "unsafePath", "https://example.invalid"
                        ),
                        occurredAt
                ));

        assertEquals(SubscriptionUpdateDigestRecordResult.CREATED, result);
        assertEquals(1, insertCalls.get());
        SubscriptionUpdateDigestPO row = inserted.get();
        assertEquals(1L, row.getId());
        assertEquals("DISCUSSION", row.getSourceType());
        assertEquals(99L, row.getSourceId());
        assertEquals("POST", row.getResourceType());
        assertEquals(501L, row.getResourceId());
        assertEquals("DISCUSSION_COMMENT_CREATED", row.getEventType());
        assertEquals("comment:501", row.getEventKey());
        assertEquals(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC), row.getOccurredAt());

        Map<String, Object> payload = new ObjectMapper().readValue(
                row.getPayloadJson(), new TypeReference<>() {
                });
        assertEquals("A new public reply", payload.get("summary"));
        assertEquals("/post/501#comments", payload.get("targetPath"));
        assertEquals(501, ((Number) payload.get("postId")).intValue());
        assertFalse(payload.containsKey("rawBody"));
        assertFalse(payload.containsKey("unsafePath"));
    }

    @Test
    void duplicateAndUnavailableWritesAreExplicitAndDoNotTouchNormalNotifications() {
        AtomicInteger insertCalls = new AtomicInteger();
        SubscriptionUpdateDigestMapper mapper = proxy((method, args) -> switch (method) {
            case "tableExists" -> 1;
            case "requiredColumnCount" -> 14;
            case "insertIgnore" -> {
                insertCalls.incrementAndGet();
                yield 0;
            }
            default -> defaultValue(returnType(method));
        });
        SubscriptionUpdateDigestService service = new SubscriptionUpdateDigestService(
                mapper, new IncrementingIdGenerator(), new ObjectMapper());

        assertEquals(SubscriptionUpdateDigestRecordResult.DUPLICATE, service.record(command()));
        assertEquals(1, insertCalls.get());

        SubscriptionUpdateDigestMapper unavailableMapper = proxy((method, args) -> switch (method) {
            case "tableExists" -> 0;
            case "requiredColumnCount" -> 0;
            case "insertIgnore" -> throw new AssertionError("write must not run when table is unavailable");
            default -> defaultValue(returnType(method));
        });
        SubscriptionUpdateDigestService unavailable = new SubscriptionUpdateDigestService(
                unavailableMapper, new IncrementingIdGenerator(), new ObjectMapper());

        assertEquals(SubscriptionUpdateDigestRecordResult.TABLE_UNAVAILABLE, unavailable.record(command()));
        assertFalse(unavailable.isReady());
    }

    @Test
    void invalidDeliveryFactsFailBeforeAWrite() {
        AtomicInteger insertCalls = new AtomicInteger();
        SubscriptionUpdateDigestMapper mapper = proxy((method, args) -> switch (method) {
            case "tableExists" -> 1;
            case "requiredColumnCount" -> 14;
            case "insertIgnore" -> {
                insertCalls.incrementAndGet();
                yield 1;
            }
            default -> defaultValue(returnType(method));
        });
        SubscriptionUpdateDigestService service = new SubscriptionUpdateDigestService(
                mapper, new IncrementingIdGenerator(), new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () -> service.record(
                new SubscriptionUpdateDigestCommand(
                        8L, "USER", 99L, "POST", 501L,
                        "DISCUSSION_COMMENT_CREATED", "bad-source", 9L, Map.of(), Instant.now())));
        assertEquals(0, insertCalls.get());
    }

    private static SubscriptionUpdateDigestCommand command() {
        return new SubscriptionUpdateDigestCommand(
                8L, "DISCUSSION", 99L, "POST", 501L,
                "DISCUSSION_COMMENT_CREATED", "comment:501", 9L,
                Map.of("summary", "A new public reply"), Instant.parse("2026-07-31T08:30:00Z"));
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
