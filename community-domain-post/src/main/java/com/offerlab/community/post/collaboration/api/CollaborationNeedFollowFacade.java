package com.offerlab.community.post.collaboration.api;

import com.offerlab.community.common.result.PageResult;

public interface CollaborationNeedFollowFacade {

    PageResult<Long> listActiveFollowerUids(Long needId, long cursor, int size);
}
