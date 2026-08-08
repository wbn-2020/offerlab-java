package com.offerlab.community.analytics.api.dto;

public record V45ActionResultDTO(
        String requestId,
        String actionType,
        String riskLevel,
        boolean accepted,
        boolean dryRun,
        boolean approvalRequired,
        boolean sideEffectCreated,
        String decision,
        String idempotencyKey) {
}
