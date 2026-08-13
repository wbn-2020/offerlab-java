package com.offerlab.community.notification.application;

import com.offerlab.community.user.api.dto.UserSubscriptionPreferenceDTO;

import java.util.Locale;

enum SubscriptionUpdateDeliveryMode {
    IMMEDIATE,
    DIGEST,
    MUTED;

    static SubscriptionUpdateDeliveryMode fromPreference(UserSubscriptionPreferenceDTO preference) {
        if (preference == null || preference.getDeliveryMode() == null
                || preference.getDeliveryMode().isBlank()) {
            return IMMEDIATE;
        }
        try {
            return valueOf(preference.getDeliveryMode().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
