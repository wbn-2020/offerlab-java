package com.offerlab.community.incentive.api.quota;

public record EntitlementReservationCmd(
        Long uid,
        String benefitCode,
        String consumerCode,
        long amount,
        String idempotencyKey,
        String requestFingerprint,
        String sourceType,
        String sourceRef,
        String reasonCode,
        long minimumTtlSeconds
) {
}
