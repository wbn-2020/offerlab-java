package com.offerlab.community.analytics.infrastructure.persistence;

import java.time.LocalDateTime;

public record V45IdempotencyRecordRow(
        Long requestedByUid,
        String idempotencyKey,
        String requestFingerprint,
        String resultJson,
        LocalDateTime completedAt) {
}
