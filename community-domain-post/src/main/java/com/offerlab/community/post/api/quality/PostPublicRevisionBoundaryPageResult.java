package com.offerlab.community.post.api.quality;

import java.util.List;
import java.util.Objects;

/**
 * A bounded page from a stable, reader-data-free channel projection.
 * {@code available=false} is a source/readiness failure, never a no-revision result.
 */
public record PostPublicRevisionBoundaryPageResult(
        List<PostPublicRevisionBoundaryProjection> items,
        Long nextCursor,
        Long snapshotUpperBoundPostId,
        boolean available
) {
    public PostPublicRevisionBoundaryPageResult {
        items = items == null ? List.of() : items.stream()
                .filter(Objects::nonNull)
                .toList();
        if (items.size() > PostPublicRevisionBoundaryPageQuery.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page exceeds maximum size");
        }
        if (!available) {
            items = List.of();
            nextCursor = null;
        }
    }

    public static PostPublicRevisionBoundaryPageResult available(
            List<PostPublicRevisionBoundaryProjection> items,
            Long nextCursor,
            Long snapshotUpperBoundPostId) {
        return new PostPublicRevisionBoundaryPageResult(items, nextCursor, snapshotUpperBoundPostId, true);
    }

    public static PostPublicRevisionBoundaryPageResult unavailable() {
        return new PostPublicRevisionBoundaryPageResult(List.of(), null, null, false);
    }
}
