package com.offerlab.community.incentive.api.quota;

import java.time.LocalDateTime;

public record EntitlementUsageDTO(
        Long usageId,
        Long entitlementId,
        Long uid,
        String benefitCode,
        String consumerCode,
        EntitlementUsageStatus status,
        long amount,
        String idempotencyKey,
        String requestFingerprint,
        String failureCode,
        LocalDateTime expiresAt,
        LocalDateTime confirmedAt,
        LocalDateTime releasedAt
) {
}
