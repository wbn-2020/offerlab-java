package com.offerlab.community.incentive.api.quota;

public record EntitlementConsumerDescriptor(
        String benefitCode,
        String consumerCode,
        String displayName,
        String targetPath,
        boolean installed,
        boolean runtimeAvailable,
        String unavailableReason
) {
}
