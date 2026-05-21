package com.offerlab.community.post.api;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostCounterDTO;
import com.offerlab.community.post.api.dto.PostCreateCmd;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.PostUpdateCmd;

import java.util.Collection;
import java.util.Map;

public interface PostFacade {

    PostDTO getPost(Long postId);

    Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds);

    Map<Long, PostCounterDTO> batchGetCounters(Collection<Long> postIds);

    Long publishPost(PostCreateCmd cmd);

    void updatePost(PostUpdateCmd cmd);

    void deletePost(Long postId, Long operatorUid);

    PageResult<PostBriefDTO> getPostsByAuthor(Long authorId, long cursor, int size);

    PageResult<PostBriefDTO> getLatest(long cursor, int size);
}
