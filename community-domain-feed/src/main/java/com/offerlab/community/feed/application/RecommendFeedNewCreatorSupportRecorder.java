package com.offerlab.community.feed.application;

/**
 * Records recommend-feed response stats for new-creator support.
 * Delivery/hit counts are based on items returned in the recommend response page,
 * not viewport exposure or pixel visibility.
 */
@FunctionalInterface
public interface RecommendFeedNewCreatorSupportRecorder {

    void recordRecommendFeedResponse(Long viewerUid, Integer domain, int deliveredItemCount, int supportHitItemCount);
}
