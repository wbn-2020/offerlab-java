package com.offerlab.community.feed.api;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.feed.api.dto.FeedItemVO;

public interface FeedFacade {

    PageResult<FeedItemVO> getFollowingFeed(Long uid, String cursor, int size);

    PageResult<FeedItemVO> getRecommendFeed(Long uid, String cursor, int size);

    PageResult<FeedItemVO> getLatestFeed(Long viewerUid, String cursor, int size);

    PageResult<FeedItemVO> getHotFeed(Long viewerUid, String cursor, int size);

    void recordFeedback(Long uid, Long postId, String action, String reason);
}
