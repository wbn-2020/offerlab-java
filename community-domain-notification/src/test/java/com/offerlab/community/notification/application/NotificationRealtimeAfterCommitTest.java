package com.offerlab.community.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.tx.AfterCommitExecutor;
import com.offerlab.community.notification.infrastructure.persistence.mapper.NotificationMessageMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationMessagePO;
import com.offerlab.community.user.api.UserFacade;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationRealtimeAfterCommitTest {

    @Test
    void onlySuccessfulInsertPublishesAfterTheTransactionCommits() throws Exception {
        List<NotificationMessagePO> inserted = new ArrayList<>();
        NotificationMessageMapper mapper = mapper(inserted, 1);
        CapturingPublisher publisher = new CapturingPublisher();
        NotificationFacadeImpl facade = facade(mapper, publisher, true);

        TransactionSynchronizationManager.initSynchronization();
        try {
            facade.notifyLike(42L, 7L, 1, 99L);

            assertEquals(1, inserted.size());
            assertTrue(publisher.events.isEmpty(), "network publication must wait for transaction commit");

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            assertEquals(1, publisher.events.size());
            PublishedEvent event = publisher.events.get(0);
            assertEquals(42L, event.receiverUid());
            assertEquals(inserted.get(0).getId(), event.notificationId());
            assertEquals(1L, event.unread().get("total"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void duplicateAndPreferenceRejectedNotificationsDoNotPublish() throws Exception {
        CapturingPublisher duplicatePublisher = new CapturingPublisher();
        NotificationFacadeImpl duplicateFacade = facade(mapper(new ArrayList<>(), 0), duplicatePublisher, true);
        duplicateFacade.notifyLike(42L, 7L, 1, 99L);
        assertTrue(duplicatePublisher.events.isEmpty(), "insertIgnore duplicates must not emit realtime events");

        CapturingPublisher rejectedPublisher = new CapturingPublisher();
        NotificationFacadeImpl rejectedFacade = facade(mapper(new ArrayList<>(), 1), rejectedPublisher, false);
        rejectedFacade.notifyLike(42L, 7L, 1, 99L);
        assertTrue(rejectedPublisher.events.isEmpty(), "notification preferences must prevent realtime events");
    }

    private NotificationFacadeImpl facade(NotificationMessageMapper mapper,
                                          NotificationRealtimePublisher publisher,
                                          boolean allowLike) throws Exception {
        UserFacade userFacade = (UserFacade) Proxy.newProxyInstance(
                UserFacade.class.getClassLoader(),
                new Class<?>[]{UserFacade.class},
                (proxy, method, args) -> "allowsLikeNotification".equals(method.getName())
                        ? allowLike
                        : defaultValue(method.getReturnType())
        );
        NotificationFacadeImpl facade = new NotificationFacadeImpl(
                mapper, new SnowflakeIdGenerator(), new ObjectMapper(), userFacade);
        setField(facade, "afterCommitExecutor", new AfterCommitExecutor());
        setField(facade, "realtimePublisher", publisher);
        return facade;
    }

    private NotificationMessageMapper mapper(List<NotificationMessagePO> inserted, int insertResult) {
        return (NotificationMessageMapper) Proxy.newProxyInstance(
                NotificationMessageMapper.class.getClassLoader(),
                new Class<?>[]{NotificationMessageMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "tableExists", "dedupKeyColumnExists" -> 1;
                    case "insertIgnore" -> {
                        inserted.add((NotificationMessagePO) args[0]);
                        yield insertResult;
                    }
                    case "countUnreadGroupedByType" -> List.of(Map.of("notifType", 1, "unreadCount", 1));
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = NotificationFacadeImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        return null;
    }

    private record PublishedEvent(Long receiverUid, Long notificationId, Map<String, Long> unread) {
    }

    private static final class CapturingPublisher implements NotificationRealtimePublisher {
        private final List<PublishedEvent> events = new ArrayList<>();

        @Override
        public void publishNotification(Long receiverUid, Long notificationId, Map<String, Long> unread) {
            events.add(new PublishedEvent(receiverUid, notificationId, unread));
        }

        @Override
        public void publishUnreadCount(Long receiverUid, Map<String, Long> unread) {
            // This test exercises notification creation, not read synchronization.
        }
    }
}
