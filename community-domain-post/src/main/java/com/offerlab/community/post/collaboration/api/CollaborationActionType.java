package com.offerlab.community.post.collaboration.api;

import java.util.Locale;

public enum CollaborationActionType {
    NEED_SUBMIT,
    NEED_REVISE,
    NEED_REVIEW,
    NEED_STALLED,
    OFFICE_HOUR_REVIEW;

    public static CollaborationActionType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("actionType is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("actionType is invalid: " + value, ex);
        }
    }
}
