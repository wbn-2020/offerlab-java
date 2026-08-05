package com.offerlab.community.post.api.quality;

import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * Reader-data-free public content boundary for channel quality aggregation.
 */
public record PostPublicRevisionBoundaryProjection(
        Long postId,
        Integer domain,
        Long authorId,
        boolean publicEligible,
        LocalDateTime windowStart,
        String revisionToken,
        Status status
) {
    public PostPublicRevisionBoundaryProjection {
        if (postId == null || postId < 1) {
            throw new IllegalArgumentException("postId is required");
        }
        if (domain == null || domain < 1) {
            throw new IllegalArgumentException("domain is required");
        }
        if (authorId == null || authorId < 1) {
            throw new IllegalArgumentException("authorId is required");
        }
        if (!publicEligible) {
            throw new IllegalArgumentException("projection must be publicly eligible");
        }
        if (windowStart == null) {
            throw new IllegalArgumentException("windowStart is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (!StringUtils.hasText(revisionToken)) {
            throw new IllegalArgumentException("revisionToken is required");
        }
    }

    public enum Status {
        EFFECTIVE_REVISION,
        NO_EFFECTIVE_REVISION
    }
}
