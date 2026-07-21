package com.offerlab.community.interaction.api.enums;

public enum ContentSuggestionResolution {
    PENDING,
    ACCEPTED,
    PARTIAL,
    REJECTED,
    PLANNED;

    public static ContentSuggestionResolution fromDecision(ContentSuggestionDecision decision) {
        if (decision == null) {
            return PENDING;
        }
        return switch (decision) {
            case ACCEPTED, MERGED -> ACCEPTED;
            case PARTIAL_ACCEPTED -> PARTIAL;
            case REJECTED -> REJECTED;
            case PLANNED -> PLANNED;
        };
    }

    public ContentSuggestionDecision toCompatibleDecision() {
        return switch (this) {
            case PENDING -> null;
            case ACCEPTED -> ContentSuggestionDecision.ACCEPTED;
            case PARTIAL -> ContentSuggestionDecision.PARTIAL_ACCEPTED;
            case REJECTED -> ContentSuggestionDecision.REJECTED;
            case PLANNED -> ContentSuggestionDecision.PLANNED;
        };
    }

    public boolean canLinkToVersion() {
        return this == ACCEPTED || this == PARTIAL || this == PLANNED;
    }
}
