package com.offerlab.community.analytics.infrastructure.persistence;

import java.time.LocalDateTime;

public record V45ActionLedgerRow(
        Long id,
        String requestId,
        String idempotencyKey,
        String actionType,
        String riskLevel,
        String decision,
        Boolean dryRun,
        Boolean sideEffectCreated,
        String decisionReason,
        Long requestedByUid,
        LocalDateTime decidedAt,
        LocalDateTime createTime) {
}
