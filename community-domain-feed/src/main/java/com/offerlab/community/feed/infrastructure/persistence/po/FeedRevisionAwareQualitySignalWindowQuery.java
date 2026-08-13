package com.offerlab.community.feed.infrastructure.persistence.po;

import java.time.LocalDateTime;

public final class FeedRevisionAwareQualitySignalWindowQuery {
    private final Long postId;
    private final Long authorId;
    private final LocalDateTime windowStart;
    private final boolean hasEffectiveRevision;

    public FeedRevisionAwareQualitySignalWindowQuery(Long postId,
                                                      Long authorId,
                                                      LocalDateTime windowStart,
                                                      boolean hasEffectiveRevision) {
        this.postId = postId;
        this.authorId = authorId;
        this.windowStart = windowStart;
        this.hasEffectiveRevision = hasEffectiveRevision;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public LocalDateTime getWindowStart() {
        return windowStart;
    }

    public boolean isHasEffectiveRevision() {
        return hasEffectiveRevision;
    }
}
