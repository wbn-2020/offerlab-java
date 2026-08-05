package com.offerlab.community.post.api.quality;

/**
 * Read-only boundary query for analytics and other authorized internal consumers.
 * Implementations must expose only opaque revision metadata.
 */
public interface PostContentRevisionQueryFacade {

    PostContentRevisionQueryResult query(PostContentRevisionQuery query);

    PostPublicRevisionBoundaryPageResult queryPublicRevisionBoundaryPage(
            PostPublicRevisionBoundaryPageQuery query);
}
