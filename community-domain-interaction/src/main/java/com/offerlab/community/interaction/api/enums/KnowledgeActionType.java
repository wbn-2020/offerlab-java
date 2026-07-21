package com.offerlab.community.interaction.api.enums;

import java.util.Locale;

public enum KnowledgeActionType {
    SUGGESTION_RESPONSE,
    STALE_SUGGESTION,
    FRESHNESS_CONFIRMATION,
    REFERENCE_REVIEW,
    RELATION_REVIEW,
    OUTCOME_REVISIT,
    MAINTENANCE_TASK;

    public static KnowledgeActionType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("type is required");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
