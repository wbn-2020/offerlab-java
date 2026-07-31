package com.offerlab.community.interaction.application;

import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.interaction.api.dto.RevisitReadStateDTO;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.UserRevisitItemMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.UserRevisitItemPO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserRevisitResourcePriorityTest {

    @Test
    void exactResourceKeyWinsOverNewerPublicPostAlias() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 11, 0);
        UserRevisitItemPO alias = row(
                200L, "FAVORITE", "99", "/post/99", "OPEN", now);
        UserRevisitItemPO exact = row(
                100L, "POST", "99", "/post/99", "COMPLETED", now.minusDays(1));
        UserRevisitItemMapper mapper = proxy(UserRevisitItemMapper.class, (method, args) -> switch (method) {
            case "tableExists" -> 1;
            case "listVisibleByResourceKeys" -> List.of(alias, exact);
            default -> defaultValue(returnType(UserRevisitItemMapper.class, method));
        });
        UserRevisitService service = new UserRevisitService(mapper, new SnowflakeIdGenerator());

        Map<String, RevisitReadStateDTO> states =
                service.findVisibleStates(8L, List.of("POST:99"));

        assertEquals(1, states.size());
        assertEquals(100L, states.get("POST:99").getItemId());
        assertEquals("COMPLETED", states.get("POST:99").getStatus());
    }

    private static UserRevisitItemPO row(Long id,
                                         String sourceType,
                                         String sourceId,
                                         String targetPath,
                                         String status,
                                         LocalDateTime updateTime) {
        UserRevisitItemPO row = new UserRevisitItemPO();
        row.setId(id);
        row.setUid(8L);
        row.setSourceType(sourceType);
        row.setSourceId(sourceId);
        row.setTargetPath(targetPath);
        row.setRevisitStatus(status);
        row.setUpdateTime(updateTime);
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
