package com.offerlab.community.notification.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One follower-update delivery unit. Its source key is the reason the receiver
 * is eligible, while its resource key is the public item eventually opened.
 */
record SubscriptionUpdateDeliveryCommand(
        Long receiverUid,
        String sourceType,
        Long sourceId,
        String resourceType,
        Long resourceId,
        String eventType,
        String eventKey,
        Long actorUid,
        SubscriptionUpdateNotificationKind notificationKind,
        Integer notificationTargetType,
        Long notificationTargetId,
        Map<String, Object> safePayload,
        Instant occurredAt) {

    int notificationType() {
        return notificationKind == null ? 0 : notificationKind.notificationType();
    }

    Long retrySenderUid() {
        return notificationKind == SubscriptionUpdateNotificationKind.SYSTEM ? 0L : actorUid;
    }

    Map<String, Object> payloadOrEmpty() {
        return safePayload == null ? Map.of() : new LinkedHashMap<>(safePayload);
    }
}
