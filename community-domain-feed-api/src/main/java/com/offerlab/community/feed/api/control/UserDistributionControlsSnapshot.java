package com.offerlab.community.feed.api.control;

import java.util.Objects;
import java.util.Set;

public record UserDistributionControlsSnapshot(
        Set<Long> hiddenPostIds,
        Set<Long> blockedAuthorIds,
        Set<Integer> reducedDomainCodes,
        boolean available
) {
    public UserDistributionControlsSnapshot {
        hiddenPostIds = sanitizePositiveLongs(hiddenPostIds);
        blockedAuthorIds = sanitizePositiveLongs(blockedAuthorIds);
        reducedDomainCodes = sanitizePositiveIntegers(reducedDomainCodes);
    }

    public static UserDistributionControlsSnapshot unavailable() {
        return new UserDistributionControlsSnapshot(Set.of(), Set.of(), Set.of(), false);
    }

    public static UserDistributionControlsSnapshot emptyAvailable() {
        return new UserDistributionControlsSnapshot(Set.of(), Set.of(), Set.of(), true);
    }

    private static Set<Long> sanitizePositiveLongs(Set<Long> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .filter(value -> value > 0)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Set<Integer> sanitizePositiveIntegers(Set<Integer> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .filter(value -> value > 0)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
