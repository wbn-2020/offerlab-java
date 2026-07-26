package com.offerlab.community.notification.application;

import com.offerlab.community.notification.api.NotificationFacade;
import com.offerlab.community.question.api.event.QuestionExtractionFinishedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionNotificationListenerReliabilityTest {

    @Test
    void listenerExecutesWhenCompletionEventIsPublishedOutsideATransaction() throws Exception {
        TransactionalEventListener annotation = QuestionNotificationListener.class
                .getMethod("onQuestionExtractionFinished", QuestionExtractionFinishedEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        assertTrue(annotation.fallbackExecution());
    }

    @Test
    void synchronousHandlerCreatesTheCompletionNotification() {
        AtomicInteger notifications = new AtomicInteger();
        QuestionNotificationListener listener = new QuestionNotificationListener(
                notificationFacade(notifications), null);

        listener.handleQuestionExtractionFinishedSynchronously(
                QuestionExtractionFinishedEvent.builder()
                        .taskId(11L)
                        .postId(22L)
                        .postAuthorUid(33L)
                        .success(true)
                        .questionCount(4)
                        .build());

        assertEquals(1, notifications.get());
    }

    private static NotificationFacade notificationFacade(AtomicInteger notifications) {
        return (NotificationFacade) Proxy.newProxyInstance(
                NotificationFacade.class.getClassLoader(),
                new Class<?>[]{NotificationFacade.class},
                (proxy, method, args) -> {
                    if ("notifySystem".equals(method.getName())) {
                        notifications.incrementAndGet();
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == int.class) {
            return 0;
        }
        return 0;
    }
}
