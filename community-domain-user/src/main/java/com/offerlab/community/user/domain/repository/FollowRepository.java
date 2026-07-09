package com.offerlab.community.user.domain.repository;

import com.offerlab.community.user.api.dto.FollowCursorDTO;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface FollowRepository {

    /** 创建关注（包含取关后再关注：恢复软删行） */
    boolean follow(Long fromUid, Long toUid);

    boolean unfollow(Long fromUid, Long toUid);

    boolean isFollowing(Long fromUid, Long toUid);

    Set<Long> followingTargets(Long fromUid, Collection<Long> toUids);

    /** 获取关注列表 ID（按时间倒序，游标为最后一条记录的 id） */
    List<Long> followingIds(Long uid, long cursor, int size);

    List<FollowCursorDTO> followingPage(Long uid, long cursor, int size);

    /** 获取粉丝列表 ID */
    List<Long> followerIds(Long uid, long cursor, int size);

    List<FollowCursorDTO> followerPage(Long uid, long cursor, int size);

    long followerCount(Long uid);

    long followingCount(Long uid);
}
