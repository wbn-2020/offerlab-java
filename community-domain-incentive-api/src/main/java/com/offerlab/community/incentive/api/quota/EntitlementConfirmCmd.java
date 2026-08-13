package com.offerlab.community.incentive.api.quota;

public record EntitlementConfirmCmd(
        Long uid,
        Long usageId,
        String consumerCode
) {
}
