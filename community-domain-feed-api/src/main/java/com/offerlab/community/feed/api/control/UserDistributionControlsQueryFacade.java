package com.offerlab.community.feed.api.control;

/**
 * Read-only viewer distribution controls for domains that render public content
 * outside the Feed module. Implementations must not expose control ownership.
 */
public interface UserDistributionControlsQueryFacade {

    UserDistributionControlsSnapshot snapshot(Long viewerUid);
}
