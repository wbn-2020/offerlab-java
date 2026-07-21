package com.offerlab.community.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.interaction.api.RevisitReadFacade;
import com.offerlab.community.interaction.api.dto.RevisitReadStateDTO;
import com.offerlab.community.notification.api.dto.UpdateDigestItemDTO;
import com.offerlab.community.notification.infrastructure.persistence.mapper.NotificationMessageMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationMessagePO;
import com.offerlab.community.post.api.PublicUpdateResourceFacade;
import com.offerlab.community.post.api.dto.PublicUpdateResourceDTO;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.UserSubscriptionPreferenceFacade;
import com.offerlab.community.user.api.dto.UserSubscriptionPreferenceDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateDigestDeduplicationTest {

    @Test
    void duplicateEventsAreDroppedAndDistinctEventsAggregateWithoutMutatingRevisit() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 19, 12, 0);
        List<NotificationMessagePO> rows = List.of(
                row(30L, "event:2", now, 0),
                row(20L, "event:1", now.minusMinutes(10), 1),
                row(10L, "event:1", now.minusMinutes(20), 0)
        );
        NotificationMessageMapper mapper = proxy(NotificationMessageMapper.class, (method, args) -> switch (method) {
            case "tableExists" -> 1;
            case "dedupKeyColumnExists" -> 1;
            case "listUpdateDigestCandidates" -> rows;
            default -> defaultValue(returnType(NotificationMessageMapper.class, method));
        });
        UserFacade users = proxy(UserFacade.class, (method, args) ->
                method.startsWith("allows") ? true : defaultValue(returnType(UserFacade.class, method)));
        UserSubscriptionPreferenceFacade preferences = proxy(
                UserSubscriptionPreferenceFacade.class,
                (method, args) -> "findEffective".equals(method)
                        ? Map.of("DISCUSSION:99", UserSubscriptionPreferenceDTO.builder()
                                .sourceType("DISCUSSION")
                                .sourceId(99L)
                                .deliveryMode("DIGEST")
                                .build())
                        : defaultValue(returnType(UserSubscriptionPreferenceFacade.class, method)));
        PublicUpdateResourceFacade publicResources = (sourceType, sourceId, fallbackPostId, requestedPath) ->
                PublicUpdateResourceDTO.builder()
                        .sourceType("POST")
                        .sourceId("99")
                        .title("公开讨论")
                        .canonicalPath("/post/99")
                        .postId(99L)
                        .updatedAt(now)
                        .build();
        RevisitReadFacade revisits = (uid, keys) -> Map.of(
                "POST:99",
                RevisitReadStateDTO.builder()
                        .itemId(7L)
                        .resourceKey("POST:99")
                        .status("OPEN")
                        .targetPath("/post/99")
                        .build()
        );

        UpdateDigestQueryService service = new UpdateDigestQueryService(
                mapper, new ObjectMapper(), users, preferences, publicResources, revisits);
        PageResult<UpdateDigestItemDTO> page = service.list(8L, "POST", null, false, "0", 20);

        assertEquals(1, page.getItems().size());
        UpdateDigestItemDTO item = page.getItems().get(0);
        assertEquals(2, item.getOccurrenceCount());
        assertEquals(List.of(30L, 20L), item.getNotificationIds());
        assertTrue(item.isNotificationUnread());
        assertEquals("event:2", item.getEventId());
        assertNotNull(item.getRevisit());
        assertEquals("OPEN", item.getRevisit().getStatus());
        assertFalse(Boolean.TRUE.equals(page.getHasMore()));
    }

    private static NotificationMessagePO row(Long id, String eventId, LocalDateTime time, int isRead) {
        NotificationMessagePO row = new NotificationMessagePO();
        row.setId(id);
        row.setReceiverUid(8L);
        row.setSenderUid(9L);
        row.setNotifType(2);
        row.setTargetType(2);
        row.setTargetId(id);
        row.setContentJson("""
                {
                  "action": "discussion_follow_comment",
                  "postId": 99,
                  "eventId": "%s",
                  "targetPath": "/post/99#comments"
                }
                """.formatted(eventId));
        row.setDedupKey("notification:" + eventId);
        row.setIsRead(isRead);
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
