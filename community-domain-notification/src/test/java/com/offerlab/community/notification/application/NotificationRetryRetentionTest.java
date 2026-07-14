package com.offerlab.community.notification.application;

import com.offerlab.community.notification.infrastructure.persistence.mapper.NotificationRetryTaskMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationRetryRetentionTest {

    @Test
    void cleansDoneAndFailedTasksInBoundedRetentionBatches() throws Exception {
        List<String> operations = new ArrayList<>();
        NotificationRetryTaskMapper mapper = (NotificationRetryTaskMapper) Proxy.newProxyInstance(
                NotificationRetryTaskMapper.class.getClassLoader(),
                new Class<?>[]{NotificationRetryTaskMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "tableExists" -> 1;
                    case "deleteTerminalBefore" -> {
                        operations.add("cleanup:" + args[0] + ":" + args[2]);
                        yield 0;
                    }
                    default -> defaultValue(method.getReturnType());
                });
        NotificationRetryService service = new NotificationRetryService(mapper, null, null, null);

        NotificationRetryService.class.getMethod("cleanupExpiredTasks").invoke(service);

        assertEquals(List.of("cleanup:1:1000", "cleanup:2:1000"), operations);
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
        return 0;
    }
}
