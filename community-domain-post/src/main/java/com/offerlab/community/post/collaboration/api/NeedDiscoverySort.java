package com.offerlab.community.post.collaboration.api;

import java.util.Locale;

public enum NeedDiscoverySort {
    LATEST,
    UPDATED,
    STALLED_FIRST;

    public static NeedDiscoverySort parse(String value) {
        if (value == null || value.isBlank()) {
            return LATEST;
        }
        if ("NEWEST".equalsIgnoreCase(value.trim())) {
            return LATEST;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("sort is invalid: " + value, ex);
        }
    }
}
