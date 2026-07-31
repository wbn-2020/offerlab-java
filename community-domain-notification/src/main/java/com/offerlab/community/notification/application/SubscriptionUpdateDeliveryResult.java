package com.offerlab.community.notification.application;

record SubscriptionUpdateDeliveryResult(
        SubscriptionUpdateDeliveryCommand command,
        SubscriptionUpdateDeliveryMode deliveryMode,
        Status status,
        RuntimeException failure) {

    enum Status {
        DELIVERED_IMMEDIATE,
        DELIVERED_DIGEST,
        SUPPRESSED_MUTED,
        SUPPRESSED_GLOBAL,
        SUPPRESSED_FEATURE_DISABLED,
        POLICY_UNAVAILABLE,
        FAILED
    }

    boolean delivered() {
        return status == Status.DELIVERED_IMMEDIATE || status == Status.DELIVERED_DIGEST;
    }

    boolean failed() {
        return status == Status.FAILED;
    }
}
