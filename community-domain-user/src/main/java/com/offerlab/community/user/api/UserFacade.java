package com.offerlab.community.user.api;

import com.offerlab.community.user.api.dto.UserBriefDTO;
import com.offerlab.community.user.api.dto.UserIntentDTO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 用户域对外暴露能力（其他模块调用入口）
 */
public interface UserFacade {

    UserBriefDTO getUserBrief(Long uid);

    Map<Long, UserBriefDTO> batchGetUserBriefs(Collection<Long> uids);

    Map<String, Long> findUserIdsByNicknames(Collection<String> nicknames);

    boolean isFollowing(Long fromUid, Long toUid);

    Map<Long, Boolean> batchIsFollowing(Long fromUid, Collection<Long> toUids);

    List<Long> getFollowerIds(Long uid, long cursor, int size);

    List<Long> getFollowingIds(Long uid, long cursor, int size);

    long getFollowerCount(Long uid);

    boolean isBigV(Long uid);

    UserIntentDTO getUserIntent(Long uid);

    boolean isProfileVisible(Long viewerUid, Long targetUid);

    boolean isIntentVisible(Long viewerUid, Long targetUid);

    boolean isSearchable(Long uid);

    boolean allowsInteractionNotification(Long uid);

    boolean allowsSystemNotification(Long uid);
}
