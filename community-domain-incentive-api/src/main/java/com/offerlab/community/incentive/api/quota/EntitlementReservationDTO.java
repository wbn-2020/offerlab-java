package com.offerlab.community.incentive.api.quota;

import java.time.LocalDateTime;

public record EntitlementReservationDTO(
        Long usageId,
        Long entitlementId,
        EntitlementUsageStatus status,
        long amount,
        LocalDateTime expiresAt
) {
}
