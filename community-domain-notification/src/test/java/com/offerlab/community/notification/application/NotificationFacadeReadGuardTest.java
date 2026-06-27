package com.offerlab.community.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.notification.infrastructure.persistence.mapper.NotificationMessageMapper;
import com.offerlab.community.user.api.UserFacade;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationFacadeReadGuardTest {

    @Test
    void normalizeReadIdsDropsNullAndNonPositiveValues() throws Exception {
        NotificationFacadeImpl facade = new NotificationFacadeImpl(mapperStub(), new SnowflakeIdGenerator(), new ObjectMapper(), userFacadeStub());

        @SuppressWarnings("unchecked")
        List<Long> normalized = (List<Long>) normalizeMethod().invoke(facade, Arrays.asList(null, -1L, 0L, null));

        assertEquals(List.of(), normalized);
    }

    @Test
    void normalizeReadIdsDeduplicatesPositiveIdsAndCapsTheBatch() throws Exception {
        NotificationFacadeImpl facade = new NotificationFacadeImpl(mapperStub(), new SnowflakeIdGenerator(), new ObjectMapper(), userFacadeStub());
        List<Long> ids = new ArrayList<>();
        ids.add(null);
        ids.add(-2L);
        ids.add(0L);
        for (long id = 1; id <= 220; id++) {
            ids.add(id);
            ids.add(id);
        }

        @SuppressWarnings("unchecked")
        List<Long> normalized = (List<Long>) normalizeMethod().invoke(facade, ids);

        assertEquals(200, normalized.size());
        assertEquals(1L, normalized.get(0));
        assertEquals(200L, normalized.get(normalized.size() - 1));
    }

    private static Method normalizeMethod() throws Exception {
        Method method = NotificationFacadeImpl.class.getDeclaredMethod("normalizeReadIds", List.class);
        method.setAccessible(true);
        return method;
    }

    private static UserFacade userFacadeStub() {
        return (UserFacade) Proxy.newProxyInstance(
                UserFacade.class.getClassLoader(),
                new Class[]{UserFacade.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static NotificationMessageMapper mapperStub() {
        return (NotificationMessageMapper) Proxy.newProxyInstance(
                NotificationMessageMapper.class.getClassLoader(),
                new Class[]{NotificationMessageMapper.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}
