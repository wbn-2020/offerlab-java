package com.offerlab.community.notification.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateDigestVisibilityGuardTest {

    @Test
    void digestStaysSeparateFromNotificationAndRevisitState() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/notification/controller/UpdateDigestController.java");
        String service = read("src/main/java/com/offerlab/community/notification/application/UpdateDigestQueryService.java");
        String mapper = read("src/main/java/com/offerlab/community/notification/infrastructure/persistence/mapper/NotificationMessageMapper.java");
        String dto = read("src/main/java/com/offerlab/community/notification/api/dto/UpdateDigestItemDTO.java");

        assertTrue(controller.contains("/api/v1/users/me/updates"));
        assertTrue(controller.contains("UserContext.require()"));
        assertTrue(controller.contains("@RateLimit"));
        assertTrue(controller.contains("unreadOnly"));
        assertTrue(controller.contains("sourceId"));

        assertTrue(mapper.contains("receiver_uid = #{uid}"));
        assertTrue(mapper.contains("dedup_key"));
        assertTrue(mapper.contains("AND is_deleted = 0"));
        assertTrue(mapper.contains("AND is_read = 0"));
        assertTrue(mapper.contains("ORDER BY create_time DESC, id DESC"));
        assertTrue(mapper.contains("@Param(\"sourceType\")"));
        assertTrue(mapper.contains("@Param(\"sourceId\")"));
        assertTrue(mapper.contains("JSON_EXTRACT"));

        assertTrue(service.contains("publicUpdateResourceFacade.resolvePublic"));
        assertTrue(service.contains("revisitReadFacade.findVisibleStates"));
        assertTrue(service.contains("subscriptionPreferenceFacade.findEffective"));
        assertTrue(service.contains("\"DIGEST\".equals(preference.getDeliveryMode())"));
        assertTrue(service.contains("allowsCommentNotification"));
        assertTrue(service.contains("allowsSystemNotification"));
        assertTrue(service.contains("notificationReadMutation"));
        assertTrue(service.contains("revisitMutation"));
        assertTrue(service.contains("row.getReceiverUid()"));
        assertTrue(service.contains("normalizeSourceId"));
        assertTrue(service.contains("case \"POST\", \"TOPIC\", \"NEED\", \"COLLECTION\", \"SERIES\""));
        assertTrue(service.contains("sourceFilterMatches"));
        assertTrue(service.contains("resourceMatches"));
        assertFalse(service.contains("markAsRead("));
        assertFalse(service.contains("complete("));

        assertTrue(dto.contains("projectionType"));
        assertTrue(dto.contains("digestKey"));
        assertTrue(dto.contains("dedupKey"));
        assertTrue(dto.contains("eventId"));
        assertTrue(dto.contains("notificationIds"));
        assertTrue(dto.contains("notificationUnread"));
        assertTrue(dto.contains("RevisitStateDTO"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
