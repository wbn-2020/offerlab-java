package com.offerlab.community.incentive.api.quota;

public interface EntitlementConsumerRuntimeAvailability {
    String benefitCode();

    String consumerCode();

    boolean available();

    String unavailableReason();
}
