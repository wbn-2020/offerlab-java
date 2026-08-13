package com.offerlab.community.incentive.api.quota;

public record EntitlementCapabilityDTO(
        String benefitCode,
        String consumerCode,
        long availableQuantity,
        boolean available,
        String unavailableReason,
        String targetPath
) {
}
