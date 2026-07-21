package com.offerlab.community.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.interaction.api.RevisitReadFacade;
import com.offerlab.community.notification.api.dto.UpdateDigestItemDTO;
import com.offerlab.community.notification.infrastructure.persistence.mapper.NotificationMessageMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationMessagePO;
import com.offerlab.community.post.api.PublicUpdateResourceFacade;
import com.offerlab.community.post.api.dto.PublicUpdateResourceDTO;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.UserSubscriptionPreferenceFacade;
import com.offerlab.community.user.api.dto.UserSubscriptionPreferenceDTO;
import com.offerlab.community.user.api.dto.UserSubscriptionPreferenceKeyDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateDigestSourcePreferenceTest {

    @Test
    void onlyDigestRelationshipSourcesEnterAndPreferencesAreReadOnce() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 10, 0);
        List<NotificationMessagePO> rows = List.of(
                discussionRow(40L, 101L, "discussion:digest", now),
                discussionRow(30L, 102L, "discussion:immediate", now.minusMinutes(1)),
                discussionRow(20L, 103L, "discussion:muted", now.minusMinutes(2)),
                directCommentRow(10L, 101L, "direct:comment", now.minusMinutes(3))
        );
        AtomicReference<Object[]> mapperArgs = new AtomicReference<>();
        NotificationMessageMapper mapper = proxy(NotificationMessageMapper.class, (method, args) -> switch (method) {
            case "tableExists", "dedupKeyColumnExists" -> 1;
            case "listUpdateDigestCandidates" -> {
                mapperArgs.set(args);
                yield rows;
            }
            default -> defaultValue(returnType(NotificationMessageMapper.class, method));
        });
        AtomicInteger globalPreferenceCalls = new AtomicInteger();
        UserFacade users = proxy(UserFacade.class, (method, args) -> {
            if (method.startsWith("allows")) {
                globalPreferenceCalls.incrementAndGet();
                return true;
            }
            return defaultValue(returnType(UserFacade.class, method));
        });
        AtomicInteger sourcePreferenceCalls = new AtomicInteger();
        AtomicReference<Collection<UserSubscriptionPreferenceKeyDTO>> requestedKeys = new AtomicReference<>();
        UserSubscriptionPreferenceFacade preferences = proxy(
                UserSubscriptionPreferenceFacade.class,
                (method, args) -> {
                    if (!"findEffective".equals(method)) {
                        return defaultValue(returnType(UserSubscriptionPreferenceFacade.class, method));
                    }
                    sourcePreferenceCalls.incrementAndGet();
                    @SuppressWarnings("unchecked")
                    Collection<UserSubscriptionPreferenceKeyDTO> keys =
                            (Collection<UserSubscriptionPreferenceKeyDTO>) args[1];
                    requestedKeys.set(keys);
                    return Map.of(
                            "DISCUSSION:101", preference(101L, "DIGEST"),
                            "DISCUSSION:102", preference(102L, "IMMEDIATE"),
                            "DISCUSSION:103", preference(103L, "MUTED")
                    );
                });
        AtomicInteger publicResourceCalls = new AtomicInteger();
        PublicUpdateResourceFacade publicResources = (sourceType, sourceId, fallbackPostId, requestedPath) -> {
            publicResourceCalls.incrementAndGet();
            return PublicUpdateResourceDTO.builder()
                    .sourceType("POST")
                    .sourceId(sourceId)
                    .title("Public discussion " + sourceId)
                    .canonicalPath("/post/" + sourceId)
                    .postId(Long.parseLong(sourceId))
                    .updatedAt(now)
                    .build();
        };
        RevisitReadFacade revisits = (uid, keys) -> Map.of();

        UpdateDigestQueryService service = new UpdateDigestQueryService(
                mapper, new ObjectMapper(), users, preferences, publicResources, revisits);
        PageResult<UpdateDigestItemDTO> page =
                service.list(8L, "POST", null, false, "0", 20);

        assertEquals(1, page.getItems().size());
        assertEquals("101", page.getItems().get(0).getSourceId());
        assertEquals(1, sourcePreferenceCalls.get());
        assertEquals(1, globalPreferenceCalls.get());
        assertEquals(1, publicResourceCalls.get());
        assertEquals(3, requestedKeys.get().size());
        assertTrue(requestedKeys.get().stream()
                .allMatch(key -> "DISCUSSION".equals(key.getSourceType())));
        assertEquals(List.of(101L, 102L, 103L), requestedKeys.get().stream()
                .map(UserSubscriptionPreferenceKeyDTO::getSourceId)
                .toList());

        Object[] sqlArgs = mapperArgs.get();
        assertEquals("POST", sqlArgs[1]);
        assertEquals(null, sqlArgs[2]);
        assertFalse(page.getHasMore());
    }

    @Test
    void missingSourceFactPreferenceDoesNotTurnDirectNotificationIntoDigest() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 10, 0);
        NotificationMessageMapper mapper = proxy(NotificationMessageMapper.class, (method, args) -> switch (method) {
            case "tableExists", "dedupKeyColumnExists" -> 1;
            case "listUpdateDigestCandidates" -> List.of(
                    directCommentRow(10L, 101L, "direct:comment", now));
            default -> defaultValue(returnType(NotificationMessageMapper.class, method));
        });
        UserFacade users = proxy(UserFacade.class, (method, args) ->
                method.startsWith("allows") ? true : defaultValue(returnType(UserFacade.class, method)));
        AtomicInteger sourcePreferenceCalls = new AtomicInteger();
        UserSubscriptionPreferenceFacade preferences = proxy(
                UserSubscriptionPreferenceFacade.class,
                (method, args) -> {
                    if ("findEffective".equals(method)) {
                        sourcePreferenceCalls.incrementAndGet();
                        return Map.of();
                    }
                    return defaultValue(returnType(UserSubscriptionPreferenceFacade.class, method));
                });
        AtomicInteger publicResourceCalls = new AtomicInteger();
        PublicUpdateResourceFacade publicResources = (sourceType, sourceId, fallbackPostId, requestedPath) -> {
            publicResourceCalls.incrementAndGet();
            return null;
        };

        UpdateDigestQueryService service = new UpdateDigestQueryService(
                mapper, new ObjectMapper(), users, preferences, publicResources, (uid, keys) -> Map.of());
        PageResult<UpdateDigestItemDTO> page =
                service.list(8L, "POST", "101", false, "0", 20);

        assertTrue(page.getItems().isEmpty());
        assertEquals(0, sourcePreferenceCalls.get());
        assertEquals(0, publicResourceCalls.get());
    }

    private static UserSubscriptionPreferenceDTO preference(long sourceId, String mode) {
        return UserSubscriptionPreferenceDTO.builder()
                .sourceType("DISCUSSION")
                .sourceId(sourceId)
                .deliveryMode(mode)
                .build();
    }

    private static NotificationMessagePO discussionRow(
            Long id, Long postId, String eventId, LocalDateTime time) {
        return row(id, postId, eventId, "discussion_follow_comment", time);
    }

    private static NotificationMessagePO directCommentRow(
            Long id, Long postId, String eventId, LocalDateTime time) {
        return row(id, postId, eventId, "comment", time);
    }

    private static NotificationMessagePO row(
            Long id, Long postId, String eventId, String action, LocalDateTime time) {
        NotificationMessagePO row = new NotificationMessagePO();
        row.setId(id);
        row.setReceiverUid(8L);
        row.setSenderUid(9L);
        row.setNotifType(2);
        row.setTargetType(2);
        row.setTargetId(id);
        row.setContentJson("""
                {
                  "action": "%s",
                  "postId": %d,
                  "eventId": "%s",
                  "targetPath": "/post/%d#comments"
                }
                """.formatted(action, postId, eventId, postId));
        row.setDedupKey("notification:" + eventId);
        row.setIsRead(0);
        row.setCreateTime(time);
        row.setIsDeleted(0);
        return row;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    if ("toString".equals(method.getName())) {
                        return type.getSimpleName() + "Stub";
                    }
                    return invocation.invoke(method.getName(), args);
                });
    }

    private static Class<?> returnType(Class<?> type, String name) {
        for (var method : type.getMethods()) {
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
}
