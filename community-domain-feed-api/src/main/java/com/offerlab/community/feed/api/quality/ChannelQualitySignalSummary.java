package com.offerlab.community.feed.api.quality;

import java.util.Map;
import java.util.Objects;

public record ChannelQualitySignalSummary(
        Map<Integer, Long> qualifiedPostCounts,
        boolean available
) {
    public ChannelQualitySignalSummary {
        qualifiedPostCounts = qualifiedPostCounts == null ? Map.of() : qualifiedPostCounts.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey() > 0)
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> Math.max(0L, entry.getValue()),
                        Long::sum));
    }

    public static ChannelQualitySignalSummary unavailable() {
        return new ChannelQualitySignalSummary(Map.of(), false);
    }

    public static ChannelQualitySignalSummary available(Map<Integer, Long> qualifiedPostCounts) {
        return new ChannelQualitySignalSummary(qualifiedPostCounts, true);
    }
}
