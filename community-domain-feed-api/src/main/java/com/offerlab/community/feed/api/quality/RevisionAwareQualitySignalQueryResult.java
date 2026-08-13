package com.offerlab.community.feed.api.quality;

import java.util.List;
import java.util.Objects;

/**
 * Opaque, per-post aggregate of quality-expectation feedback split by revision window.
 */
public record RevisionAwareQualitySignalQueryResult(
        List<RevisionAwareQualitySignalSnapshot> items,
        boolean available
) {
    public RevisionAwareQualitySignalQueryResult {
        items = items == null ? List.of() : items.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.postId() != null && item.postId() > 0)
                .toList();
    }

    public static RevisionAwareQualitySignalQueryResult unavailable() {
        return new RevisionAwareQualitySignalQueryResult(List.of(), false);
    }

    public static RevisionAwareQualitySignalQueryResult available(
            List<RevisionAwareQualitySignalSnapshot> items) {
        return new RevisionAwareQualitySignalQueryResult(items, true);
    }

    public record RevisionAwareQualitySignalSnapshot(
            Long postId,
            String revisionToken,
            long priorRevisionDistinctReaderCount,
            long currentRevisionDistinctReaderCount
    ) {
        public RevisionAwareQualitySignalSnapshot {
            priorRevisionDistinctReaderCount = Math.max(0L, priorRevisionDistinctReaderCount);
            currentRevisionDistinctReaderCount = Math.max(0L, currentRevisionDistinctReaderCount);
        }
    }
}
