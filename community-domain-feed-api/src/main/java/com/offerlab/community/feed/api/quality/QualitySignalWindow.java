package com.offerlab.community.feed.api.quality;

import java.time.Instant;

/**
 * Opaque revision boundary supplied by the caller for one public post.
 */
public record QualitySignalWindow(
        Long postId,
        Long authorId,
        Instant windowStart,
        boolean hasEffectiveRevision,
        String revisionToken
) {
}
