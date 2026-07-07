package com.offerlab.community.interaction.api;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.interaction.api.dto.DiscussionFollowStatusDTO;
import com.offerlab.community.post.api.dto.PostBriefDTO;

import java.util.List;
import java.util.Set;

public interface DiscussionFollowFacade {

    DiscussionFollowStatusDTO status(Long uid, Long postId);

    DiscussionFollowStatusDTO follow(Long uid, Long postId);

    DiscussionFollowStatusDTO unfollow(Long uid, Long postId);

    PageResult<PostBriefDTO> listFollowedPosts(Long uid, long cursor, int size);

    List<Long> followerUidsForNotification(Long postId, Set<Long> excludedUids, int limit);

    void markNotified(Long postId, Long uid, Long commentId);
}
