package com.offerlab.community.post.api;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostCounterDTO;
import com.offerlab.community.post.api.dto.PostCreateCmd;
import com.offerlab.community.post.api.dto.PostVersionHistoryDTO;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.PostUpdateCmd;
import com.offerlab.community.post.api.dto.PublicPostUpdateDTO;
import com.offerlab.community.post.api.dto.TagDTO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface PostFacade {

    PostDTO getPost(Long postId);

    PostDTO getPost(Long postId, Long viewerUid);

    Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds);

    Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds, Long viewerUid);

    Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds, Long viewerUid, boolean includeTestData);

    default Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds, boolean includeTestData) {
        return batchGetPosts(postIds, null, includeTestData);
    }

    Map<Long, PostCounterDTO> batchGetCounters(Collection<Long> postIds);

    Map<Long, Long> batchCountPublicPublishedPostsByAuthors(Collection<Long> authorIds);

    Long publishPost(PostCreateCmd cmd);

    void updatePost(PostUpdateCmd cmd);

    void deletePost(Long postId, Long operatorUid);

    PageResult<PostBriefDTO> getPostsByAuthor(Long authorId, long cursor, int size);

    PageResult<PostBriefDTO> getLatest(long cursor, int size);

    PageResult<PostBriefDTO> getHot(String cursor, int size);

    default PageResult<PostBriefDTO> getHot(String cursor, int size, Integer domain) {
        return getHot(cursor, size);
    }


    List<PostVersionHistoryDTO> listPostVersions(Long postId, Long viewerUid, boolean moderator, int limit);

    default List<PublicPostUpdateDTO> listPublicUpdates(Long postId, int limit) {
        return List.of();
    }

    PageResult<PostBriefDTO> listPosts(Long authorId, Long tagId, Integer postType, Boolean featured, Integer domain, long cursor, int size);

    PageResult<PostBriefDTO> listPosts(Long authorId, Long tagId, Integer postType, Boolean featured, Integer domain,
                                       long cursor, int size, boolean includeTestData);

    default PageResult<PostBriefDTO> listPosts(Long authorId, Long tagId, Integer postType, long cursor, int size) {
        return listPosts(authorId, tagId, postType, null, null, cursor, size);
    }

    List<TagDTO> listTags();

    PageResult<PostBriefDTO> getPostsByTag(Long tagId, Integer postType, Boolean featured, long cursor, int size);

    default PageResult<PostBriefDTO> getPostsByTag(Long tagId, long cursor, int size) {
        return getPostsByTag(tagId, null, null, cursor, size);
    }
}
