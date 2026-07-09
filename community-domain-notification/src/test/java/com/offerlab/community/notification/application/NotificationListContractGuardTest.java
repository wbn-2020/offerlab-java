package com.offerlab.community.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.notification.infrastructure.persistence.mapper.NotificationMessageMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationMessagePO;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationListContractGuardTest {

    @Test
    void listNotificationsMustNotExposeInternalIdentifiers() {
        NotificationMessageMapper mapper = mapperStub(List.of(
                message(2002L, 7L, 12L, 1, 1, 101L, 0, LocalDateTime.of(2026, 6, 24, 0, 30))
        ));
        UserFacade userFacade = userFacadeStub(Map.of(12L, user(12L, "Bob")));
        NotificationFacadeImpl facade = new NotificationFacadeImpl(mapper, new SnowflakeIdGenerator(), new ObjectMapper(), userFacade);

        PageResult<Map<String, Object>> result = facade.listNotifications(7L, null, null, 20);

        assertEquals(1, result.getItems().size());
        Map<String, Object> item = result.getItems().get(0);
        assertEquals(2002L, item.get("id"));
        assertEquals("like", item.get("type"));
        assertTrue(item.containsKey("sender"), "public list item must keep sender info");
        assertTrue(item.containsKey("content"), "public list item must keep content payload");
        assertFalse(item.containsKey("receiverUid"), "receiver uid is internal");
        assertFalse(item.containsKey("senderUid"), "sender uid is internal");
        assertFalse(item.containsKey("notifType"), "raw notif type enum is internal");
        assertFalse(item.containsKey("targetType"), "target type is internal");
        assertFalse(item.containsKey("targetId"), "target id should not leak as a top-level contract field");
    }

    private static NotificationMessagePO message(Long id, Long receiverUid, Long senderUid,
                                                 Integer notifType, Integer targetType, Long targetId,
                                                 Integer isRead, LocalDateTime createTime) {
        NotificationMessagePO po = new NotificationMessagePO();
        po.setId(id);
        po.setReceiverUid(receiverUid);
        po.setSenderUid(senderUid);
        po.setNotifType(notifType);
        po.setTargetType(targetType);
        po.setTargetId(targetId);
        po.setContentJson("{\"action\":\"like\",\"targetType\":1,\"targetId\":101}");
        po.setIsRead(isRead);
        po.setCreateTime(createTime);
        po.setIsDeleted(0);
        return po;
    }

    private static UserBriefDTO user(Long uid, String nickname) {
        return UserBriefDTO.builder()
                .uid(uid)
                .nickname(nickname)
                .build();
    }

    private static NotificationMessageMapper mapperStub(List<NotificationMessagePO> rows) {
        return (NotificationMessageMapper) Proxy.newProxyInstance(
                NotificationMessageMapper.class.getClassLoader(),
                new Class[]{NotificationMessageMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "tableExists" -> 1;
                    case "listByUser" -> rows;
                    case "dedupKeyColumnExists" -> 1;
                    case "selectCount" -> 0L;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static UserFacade userFacadeStub(Map<Long, UserBriefDTO> users) {
        return (UserFacade) Proxy.newProxyInstance(
                UserFacade.class.getClassLoader(),
                new Class[]{UserFacade.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "batchGetUserBriefs" -> users;
                    default -> defaultValue(method.getReturnType());
                });
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
