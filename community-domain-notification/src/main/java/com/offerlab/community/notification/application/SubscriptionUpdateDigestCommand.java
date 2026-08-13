package com.offerlab.community.notification.application;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable delivery fact for a subscription update that must be shown in the digest only.
 */
public record SubscriptionUpdateDigestCommand(
        Long receiverUid,
        String sourceType,
        Long sourceId,
        String resourceType,
        Long resourceId,
        String eventType,
        String eventKey,
        Long actorUid,
        Map<String, Object> safePayload,
        Instant occurredAt) {
}
