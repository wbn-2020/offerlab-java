package com.offerlab.community.feed.api;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.feed.api.dto.CrossDomainRecommendationVO;
import com.offerlab.community.feed.api.dto.FeedItemVO;

public interface FeedFacade {

    PageResult<FeedItemVO> getFollowingFeed(Long uid, String cursor, int size, Integer domain);

    PageResult<FeedItemVO> getRecommendFeed(Long uid, String cursor, int size, Integer domain);

    PageResult<CrossDomainRecommendationVO> getCrossDomainRecommendations(Long uid, String cursor, int size);

    PageResult<FeedItemVO> getLatestFeed(Long viewerUid, String cursor, int size, Integer domain);

    PageResult<FeedItemVO> getHotFeed(Long viewerUid, String cursor, int size, Integer domain);

    void recordFeedback(Long uid, Long postId, String action, String reason);
}
