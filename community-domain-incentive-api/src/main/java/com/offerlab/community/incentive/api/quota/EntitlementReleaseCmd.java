package com.offerlab.community.incentive.api.quota;

public record EntitlementReleaseCmd(
        Long uid,
        Long usageId,
        String consumerCode,
        String failureCode
) {
}
