package com.offerlab.community.user.api;

import java.util.Set;

/**
 * Shared capability contract for source-level update delivery preferences.
 *
 * <p>The source type supplied to this contract is expected to be normalized by
 * the owning API boundary. Unknown source types are intentionally treated as
 * unsupported so callers do not accidentally expose a writable preference.</p>
 */
public record DeliveryPreferenceCapability(
        boolean deliveryPreferenceSupported,
        String deliveryPreferenceUnsupportedReason) {

    public static final String UNSUPPORTED_REASON_NO_SOURCE_UPDATE_DELIVERY =
            "SOURCE_UPDATE_DELIVERY_NOT_AVAILABLE";

    private static final Set<String> SUPPORTED_SOURCE_TYPES =
            Set.of("TOPIC", "DISCUSSION", "NEED");

    private static final DeliveryPreferenceCapability SUPPORTED =
            new DeliveryPreferenceCapability(true, null);
    private static final DeliveryPreferenceCapability UNSUPPORTED =
            new DeliveryPreferenceCapability(false, UNSUPPORTED_REASON_NO_SOURCE_UPDATE_DELIVERY);

    public static DeliveryPreferenceCapability forSourceType(String sourceType) {
        return isSupported(sourceType) ? SUPPORTED : UNSUPPORTED;
    }

    public static boolean isSupported(String sourceType) {
        return SUPPORTED_SOURCE_TYPES.contains(sourceType);
    }
}
