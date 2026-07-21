package com.offerlab.community.post.api;

import com.offerlab.community.post.api.dto.PublicUpdateResourceDTO;

/**
 * Read-only visibility revalidation used by notification projections.
 */
public interface PublicUpdateResourceFacade {

    PublicUpdateResourceDTO resolvePublic(String sourceType,
                                           String sourceId,
                                           Long fallbackPostId,
                                           String requestedPath);
}
