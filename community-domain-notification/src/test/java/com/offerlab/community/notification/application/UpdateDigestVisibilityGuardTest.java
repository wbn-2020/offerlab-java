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
        String digestService = read("src/main/java/com/offerlab/community/notification/application/SubscriptionUpdateDigestService.java");
        String mapper = read("src/main/java/com/offerlab/community/notification/infrastructure/persistence/mapper/SubscriptionUpdateDigestMapper.java");
        String dto = read("src/main/java/com/offerlab/community/notification/api/dto/UpdateDigestItemDTO.java");
        String application = read("../community-bootstrap/src/main/resources/application.yml");

        assertTrue(controller.contains("/api/v1/users/me/updates"));
        assertTrue(controller.contains("UserContext.require()"));
        assertTrue(controller.contains("@RateLimit"));
        assertTrue(controller.contains("unreadOnly"));
        assertTrue(controller.contains("sourceId"));
        assertTrue(controller.contains("resourceType"));
        assertTrue(controller.contains("resourceId"));
        assertTrue(controller.contains("subscriptionSourceType"));
        assertTrue(controller.contains("subscriptionSourceId"));

        assertTrue(mapper.contains("receiver_uid = #{uid}"));
        assertTrue(mapper.contains("t_subscription_update_digest"));
        assertTrue(mapper.contains("event_key"));
        assertTrue(mapper.contains("AND is_deleted = 0"));
        assertTrue(mapper.contains("ORDER BY occurred_at DESC, id DESC"));
        assertTrue(mapper.contains("@Param(\"subscriptionSourceType\")"));
        assertTrue(mapper.contains("@Param(\"subscriptionSourceId\")"));
        assertTrue(mapper.contains("INSERT IGNORE"));
        assertTrue(mapper.contains("requiredColumnCount"));

        assertTrue(service.contains("publicUpdateResourceFacade.resolvePublic"));
        assertTrue(service.contains("revisitReadFacade.findVisibleStates"));
        assertTrue(service.contains("notificationReadMutation"));
        assertTrue(service.contains("revisitMutation"));
        assertTrue(service.contains("subscriptionSourceFilterMatches"));
        assertTrue(service.contains("resourceMatches"));
        assertTrue(service.contains("requireUnreadFilterIsSupported"));
        assertTrue(service.contains("t_subscription_update_digest"));
        assertFalse(service.contains("NotificationMessageMapper"));
        assertFalse(service.contains("NotificationMessagePO"));
        assertFalse(service.contains("t_notif_message"));
        assertFalse(service.contains("subscriptionPreferenceFacade"));
        assertFalse(service.contains("markAsRead("));
        assertFalse(service.contains("complete("));
        assertFalse(service.contains("unreadOnlyIgnored"));

        assertTrue(application.contains("${OFFERLAB_SUBSCRIPTION_DELIVERY_ENABLED:true}"));
        assertTrue(application.contains("${OFFERLAB_SUBSCRIPTION_DELIVERY_TOPIC_ENABLED:true}"));
        assertTrue(application.contains("${OFFERLAB_SUBSCRIPTION_DELIVERY_DISCUSSION_ENABLED:true}"));
        assertTrue(application.contains("${OFFERLAB_SUBSCRIPTION_DELIVERY_NEED_ENABLED:true}"));

        assertTrue(digestService.contains("record(SubscriptionUpdateDigestCommand command)"));
        assertTrue(digestService.contains("SubscriptionUpdateDigestRecordResult.DUPLICATE"));
        assertTrue(digestService.contains("requiredColumnCount"));

        assertTrue(dto.contains("projectionType"));
        assertTrue(dto.contains("digestKey"));
        assertTrue(dto.contains("dedupKey"));
        assertTrue(dto.contains("eventId"));
        assertTrue(dto.contains("subscriptionSourceType"));
        assertTrue(dto.contains("subscriptionSourceId"));
        assertTrue(dto.contains("resourceType"));
        assertTrue(dto.contains("resourceId"));
        assertTrue(dto.contains("digestIds"));
        assertTrue(dto.contains("notificationIds"));
        assertTrue(dto.contains("notificationUnread"));
        assertTrue(dto.contains("RevisitStateDTO"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
