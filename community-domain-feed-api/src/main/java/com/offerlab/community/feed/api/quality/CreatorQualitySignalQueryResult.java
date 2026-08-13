package com.offerlab.community.feed.api.quality;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record CreatorQualitySignalQueryResult(
        List<CreatorQualitySignalSnapshot> items,
        boolean available
) {
    public CreatorQualitySignalQueryResult {
        items = items == null ? List.of() : items.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.postId() != null && item.postId() > 0)
                .toList();
    }

    public static CreatorQualitySignalQueryResult unavailable() {
        return new CreatorQualitySignalQueryResult(List.of(), false);
    }

    public static CreatorQualitySignalQueryResult available(List<CreatorQualitySignalSnapshot> items) {
        return new CreatorQualitySignalQueryResult(items, true);
    }

    public record CreatorQualitySignalSnapshot(
            Long postId,
            long distinctReaderCount,
            LocalDateTime latestUpdatedAt
    ) {
        public CreatorQualitySignalSnapshot {
            distinctReaderCount = Math.max(0L, distinctReaderCount);
        }
    }
}
