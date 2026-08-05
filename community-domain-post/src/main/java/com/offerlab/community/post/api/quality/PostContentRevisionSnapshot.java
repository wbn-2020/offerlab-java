package com.offerlab.community.post.api.quality;

import java.time.LocalDateTime;

/**
 * Opaque effective-revision boundary for internal aggregate consumers.
 */
public record PostContentRevisionSnapshot(
        Long postId,
        Status status,
        LocalDateTime windowStart,
        boolean hasEffectiveRevision,
        String revisionToken,
        Integer effectivePublishedPostVersion,
        LocalDateTime effectiveRevisionAt
) {
    public PostContentRevisionSnapshot {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (status != Status.FOUND && status != Status.NO_EFFECTIVE_REVISION) {
            hasEffectiveRevision = false;
            revisionToken = null;
            effectivePublishedPostVersion = null;
            effectiveRevisionAt = null;
        }
    }

    public enum Status {
        FOUND,
        NO_EFFECTIVE_REVISION,
        NOT_ELIGIBLE,
        UNAVAILABLE
    }
}
