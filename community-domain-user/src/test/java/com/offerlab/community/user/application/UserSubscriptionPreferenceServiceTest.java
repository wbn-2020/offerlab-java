package com.offerlab.community.user.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.user.api.dto.UserSubscriptionPreferenceDTO;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserFollowMapper;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserSubscriptionPreferenceMapper;
import com.offerlab.community.user.infrastructure.persistence.po.UserSubscriptionPreferencePO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserSubscriptionPreferenceServiceTest {

    @Test
    void recipientBatchReadUsesOneBoundedUidInQueryAndReturnsOnlyExplicitValidRows() {
        AtomicInteger mapperCalls = new AtomicInteger();
        AtomicReference<List<Long>> requestedUids = new AtomicReference<>();
        AtomicReference<String> requestedSourceType = new AtomicReference<>();
        AtomicReference<Long> requestedSourceId = new AtomicReference<>();
        UserSubscriptionPreferenceMapper mapper = proxy(UserSubscriptionPreferenceMapper.class, (proxy, method, args) -> {
            if ("findEffectiveForRecipients".equals(method.getName())) {
                mapperCalls.incrementAndGet();
                @SuppressWarnings("unchecked")
                List<Long> uids = List.copyOf((java.util.Collection<Long>) args[0]);
                requestedUids.set(uids);
                requestedSourceType.set((String) args[1]);
                requestedSourceId.set((Long) args[2]);
                return List.of(
                        preference(10L, "TOPIC", 101L, "DIGEST"),
                        preference(30L, "TOPIC", 101L, "MUTED"),
                        preference(40L, "TOPIC", 101L, "IMMEDIATE"),
                        preference(20L, "TOPIC", 101L, "INVALID"),
                        preference(10L, "NEED", 101L, "IMMEDIATE")
                );
            }
            return defaultValue(method.getReturnType());
        });

        UserSubscriptionPreferenceService service = service(mapper, followMapper());
        Map<Long, UserSubscriptionPreferenceDTO> preferences = service.findEffectiveForRecipients(
                List.of(10L, 20L, 10L, 30L), " topic ", 101L);

        assertEquals(1, mapperCalls.get());
        assertEquals(List.of(10L, 20L, 30L), requestedUids.get());
        assertEquals("TOPIC", requestedSourceType.get());
        assertEquals(101L, requestedSourceId.get());
        assertEquals(2, preferences.size());
        assertEquals("DIGEST", preferences.get(10L).getDeliveryMode());
        assertEquals("MUTED", preferences.get(30L).getDeliveryMode());
        assertTrue(preferences.get(10L).isDeliveryPreferenceSupported());
        assertEquals(null, preferences.get(10L).getDeliveryPreferenceUnsupportedReason());
    }

    @Test
    void legacyUnsupportedPreferenceRemainsReadableButNewWritesAreRejected() {
        UserSubscriptionPreferenceMapper mapper = proxy(UserSubscriptionPreferenceMapper.class, (proxy, method, args) -> {
            if ("findEffective".equals(method.getName())) {
                return preference(9L, "USER", 88L, "MUTED");
            }
            return defaultValue(method.getReturnType());
        });
        UserSubscriptionPreferenceService service = service(mapper, followMapper());

        UserSubscriptionPreferenceDTO legacy = service.get(9L, "user", 88L);

        assertEquals("MUTED", legacy.getDeliveryMode());
        assertFalse(legacy.isDeliveryPreferenceSupported());
        assertEquals("SOURCE_UPDATE_DELIVERY_NOT_AVAILABLE",
                legacy.getDeliveryPreferenceUnsupportedReason());

        for (String sourceType : List.of("USER", "SERIES")) {
            BizException exception = assertThrows(BizException.class,
                    () -> service.upsert(9L, sourceType, 88L, "MUTED", null));
            assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
            assertEquals("delivery preference is not supported for sourceType: " + sourceType,
                    exception.getMessage());
        }
    }

    @Test
    void recipientBatchReadAcceptsTheContractLimitAndRejectsLargerBatchesWithoutASecondQuery() {
        AtomicInteger mapperCalls = new AtomicInteger();
        UserSubscriptionPreferenceMapper mapper = proxy(UserSubscriptionPreferenceMapper.class, (proxy, method, args) -> {
            if ("findEffectiveForRecipients".equals(method.getName())) {
                mapperCalls.incrementAndGet();
            }
            return defaultValue(method.getReturnType());
        });
        UserSubscriptionPreferenceService service = service(mapper, followMapper());
        List<Long> contractLimitBatch = LongStream.rangeClosed(1L, 1000L).boxed().toList();
        List<Long> oversizedBatch = LongStream.rangeClosed(1L, 1001L).boxed().toList();

        assertTrue(service.findEffectiveForRecipients(contractLimitBatch, "TOPIC", 101L).isEmpty());

        BizException exception = assertThrows(BizException.class,
                () -> service.findEffectiveForRecipients(oversizedBatch, "TOPIC", 101L));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertEquals("receiverUids exceeds max batch size", exception.getMessage());
        assertEquals(1, mapperCalls.get());
    }

    private static UserSubscriptionPreferenceService service(
            UserSubscriptionPreferenceMapper preferenceMapper, UserFollowMapper followMapper) {
        return new UserSubscriptionPreferenceService(
                preferenceMapper,
                followMapper,
                new SnowflakeIdGenerator(1L, 1L),
                List.of());
    }

    private static UserFollowMapper followMapper() {
        return proxy(UserFollowMapper.class, (proxy, method, args) ->
                "existsActiveFollowing".equals(method.getName())
                        ? 1
                        : defaultValue(method.getReturnType()));
    }

    private static UserSubscriptionPreferencePO preference(
            Long uid, String sourceType, Long sourceId, String deliveryMode) {
        UserSubscriptionPreferencePO preference = new UserSubscriptionPreferencePO();
        preference.setUid(uid);
        preference.setSourceType(sourceType);
        preference.setSourceId(sourceId);
        preference.setDeliveryMode(deliveryMode);
        return preference;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
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
        if (type == double.class) {
            return 0D;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
