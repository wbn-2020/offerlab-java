package com.offerlab.community.analytics.infrastructure.persistence;

import java.time.LocalDateTime;

public record V45OutboxRow(
        Long id,
        String requestId,
        String idempotencyKey,
        String eventType,
        String aggregateType,
        String aggregateId,
        String payloadJson,
        String status,
        Integer attemptCount,
        LocalDateTime nextAttemptAt,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
