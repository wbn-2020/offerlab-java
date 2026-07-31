package com.offerlab.community.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.notification.infrastructure.persistence.mapper.NotificationRetryTaskMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationRetryTaskPO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NotificationRetrySubscriptionDeliveryTest {

    @Test
    void policyUnavailableDuringResolvedReplayIsRescheduledInsteadOfMarkedDone() throws Exception {
        AtomicInteger doneCalls = new AtomicInteger();
        AtomicReference<Object[]> retryArgs = new AtomicReference<>();
        NotificationRetryTaskMapper taskMapper = mapper(doneCalls, retryArgs);
        ObjectMapper objectMapper = new ObjectMapper();
        NotificationRetryService retryService = new NotificationRetryService(
                taskMapper, null, objectMapper, null);
        injectDeliveryService(retryService, policyUnavailableDeliveryService());

        retryService.retryOne(subscriptionDeliveryTask(objectMapper));

        assertEquals(0, doneCalls.get());
        Object[] update = retryArgs.get();
        assertNotNull(update);
        assertEquals(NotificationRetryTaskMapper.STATUS_PENDING, update[2]);
        assertEquals(1, update[3]);
    }

    private static NotificationRetryTaskMapper mapper(
            AtomicInteger doneCalls,
            AtomicReference<Object[]> retryArgs) {
        return (NotificationRetryTaskMapper) Proxy.newProxyInstance(
                NotificationRetryTaskMapper.class.getClassLoader(),
                new Class<?>[]{NotificationRetryTaskMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "markDone" -> {
                        doneCalls.incrementAndGet();
                        yield 1;
                    }
                    case "updateRetry" -> {
                        retryArgs.set(args);
                        yield 1;
                    }
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static void injectDeliveryService(
            NotificationRetryService retryService,
            SubscriptionUpdateDeliveryService deliveryService) throws Exception {
        ObjectProvider<SubscriptionUpdateDeliveryService> provider =
                (ObjectProvider<SubscriptionUpdateDeliveryService>) Proxy.newProxyInstance(
                        ObjectProvider.class.getClassLoader(),
                        new Class<?>[]{ObjectProvider.class},
                        (proxy, method, args) -> "getIfAvailable".equals(method.getName())
                                ? deliveryService
                                : defaultValue(method.getReturnType()));
        Field field = NotificationRetryService.class
                .getDeclaredField("subscriptionUpdateDeliveryServiceProvider");
        field.setAccessible(true);
        field.set(retryService, provider);
    }

    private static SubscriptionUpdateDeliveryService policyUnavailableDeliveryService() {
        return new SubscriptionUpdateDeliveryService(null, null, null, null) {
            @Override
            SubscriptionUpdateDeliveryResult deliverResolved(
                    SubscriptionUpdateDeliveryCommand command,
                    SubscriptionUpdateDeliveryMode mode) {
                return new SubscriptionUpdateDeliveryResult(
                        command,
                        mode,
                        SubscriptionUpdateDeliveryResult.Status.POLICY_UNAVAILABLE,
                        null);
            }
        };
    }

    private static NotificationRetryTaskPO subscriptionDeliveryTask(ObjectMapper objectMapper) throws Exception {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("retryVersion", 1);
        content.put("deliveryMode", "DIGEST");
        content.put("receiverUid", 12L);
        content.put("sourceType", "TOPIC");
        content.put("sourceId", 100L);
        content.put("resourceType", "POST");
        content.put("resourceId", 501L);
        content.put("eventType", "TOPIC_POST_PUBLISHED");
        content.put("eventKey", "topic_post_published:501");
        content.put("actorUid", 9L);
        content.put("notificationKind", "SYSTEM");
        content.put("notificationTargetType", 1);
        content.put("notificationTargetId", 501L);
        content.put("safePayload", Map.of("targetPath", "/post/501"));
        content.put("occurredAt", Instant.parse("2026-07-31T08:00:00Z").toEpochMilli());

        NotificationRetryTaskPO task = new NotificationRetryTaskPO();
        task.setId(9001L);
        task.setDedupKey("subscription_update_delivery:TOPIC:100:topic_post_published:501:DIGEST");
        task.setScene("subscription_update_delivery");
        task.setRetryCount(0);
        task.setContentJson(objectMapper.writeValueAsString(content));
        return task;
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
}
