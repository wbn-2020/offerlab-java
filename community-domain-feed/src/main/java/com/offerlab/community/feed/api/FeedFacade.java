package com.offerlab.community.feed.api;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.feed.api.dto.CrossDomainRecommendationVO;
import com.offerlab.community.feed.api.dto.ChannelHotBoardVO;
import com.offerlab.community.feed.api.dto.FeedControlVO;
import com.offerlab.community.feed.api.dto.FeedFeedbackPreferenceVO;
import com.offerlab.community.feed.api.dto.FeedItemVO;

public interface FeedFacade {

    PageResult<FeedItemVO> getFollowingFeed(Long uid, String cursor, int size, Integer domain);

    PageResult<FeedItemVO> getRecommendFeed(Long uid, String cursor, int size, Integer domain);

    PageResult<CrossDomainRecommendationVO> getCrossDomainRecommendations(Long uid, String cursor, int size);

    PageResult<FeedItemVO> getLatestFeed(Long viewerUid, String cursor, int size, Integer domain);

    PageResult<FeedItemVO> getHotFeed(Long viewerUid, String cursor, int size, Integer domain);

    void recordFeedback(Long uid, Long postId, String action, String reason);

    default void recordFeedback(Long uid, Long postId, String action, String reason, String reasonCode) {
        recordFeedback(uid, postId, action, reason);
    }

    ChannelHotBoardVO getChannelHotBoard(Long viewerUid, Integer domain, int size);

    PageResult<FeedFeedbackPreferenceVO> listFeedbackPreferences(Long uid, String cursor, int size);

    FeedFeedbackPreferenceVO getFeedbackPreference(Long uid, Long postId);

    FeedControlVO blockAuthor(Long uid, Long authorUid);

    void unblockAuthor(Long uid, Long authorUid);

    PageResult<FeedControlVO> listControls(Long uid, String cursor, int size);

    void deleteControl(Long uid, Long controlId);
}
