package com.offerlab.community.search.api.dto;

import java.util.Set;

public record SearchTrustFilter(
        Boolean trustProfile,
        String freshnessStatus,
        Boolean resolved,
        Boolean sourceComplete
) {
    private static final Set<String> FRESHNESS_STATUSES = Set.of(
            "CURRENT", "POSSIBLY_STALE", "AWAITING_AUTHOR_CONFIRMATION", "UPDATED", "SUPERSEDED");

    public static SearchTrustFilter of(Boolean trustProfile, String freshnessStatus,
                                       Boolean resolved, Boolean sourceComplete) {
        String normalized = freshnessStatus == null ? null : freshnessStatus.trim().toUpperCase();
        if (normalized != null && normalized.isBlank()) {
            normalized = null;
        }
        if (normalized != null && !FRESHNESS_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("unsupported freshness status");
        }
        return new SearchTrustFilter(trustProfile, normalized, resolved, sourceComplete);
    }

    public boolean active() {
        return trustProfile != null || freshnessStatus != null || resolved != null || sourceComplete != null;
    }

    public static SearchTrustFilter empty() {
        return new SearchTrustFilter(null, null, null, null);
    }
}
