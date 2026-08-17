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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface PostFacade {

    PostDTO getPost(Long postId);

    PostDTO getPost(Long postId, Long viewerUid);

    /**
     * Internal read model with the persisted author and lifecycle metadata.
     * Callers must apply their own public or owner visibility policy.
     */
    PostDTO getPostMetadata(Long postId);

    /**
     * Returns a post to its author regardless of public publication state.
     * This narrow capability is for author-owned workflow metadata only.
     */
    default PostDTO getPostForAuthor(Long postId, Long authorUid) {
        PostDTO post = getPost(postId, authorUid);
        return post != null && java.util.Objects.equals(post.getAuthorId(), authorUid) ? post : null;
    }

    /**
     * Batch variant of the author workflow read. It may return unpublished posts, but only
     * when the persisted author matches {@code authorUid}.
     */
    default Map<Long, PostBriefDTO> batchGetPostsForAuthor(Collection<Long> postIds, Long authorUid) {
        return Map.of();
    }

    Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds);

    Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds, Long viewerUid);

    Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds, Long viewerUid, boolean includeTestData);

    default Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds, boolean includeTestData) {
        return batchGetPosts(postIds, null, includeTestData);
    }

    Map<Long, PostCounterDTO> batchGetCounters(Collection<Long> postIds);

    Map<Long, Long> batchCountPublicPublishedPostsByAuthors(Collection<Long> authorIds);

    Long publishPost(PostCreateCmd cmd);

    boolean updatePost(PostUpdateCmd cmd);

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

    default PageResult<PostBriefDTO> listPostsByKeyset(Long authorId, Long tagId, Integer postType,
                                                        Boolean featured, Integer domain,
                                                        LocalDateTime cursorTime, Long cursorId, int size) {
        long legacyCursor = cursorTime == null ? 0L : cursorTime.toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        return listPosts(authorId, tagId, postType, featured, domain, legacyCursor, size);
    }

    /**
     * Internal feed query ordered by {@code (create_time DESC, id DESC)}.
     * Implementations must apply the follow relation and public-post predicates in the database.
     */
    default List<PostBriefDTO> listFollowingPostsByKeyset(Long viewerUid, Integer domain,
                                                          LocalDateTime cursorTime, Long cursorId, int size) {
        return List.of();
    }

    default PageResult<PostBriefDTO> listPosts(Long authorId, Long tagId, Integer postType, long cursor, int size) {
        return listPosts(authorId, tagId, postType, null, null, cursor, size);
    }

    List<TagDTO> listTags();

    TagDTO getTag(Long tagId);

    PageResult<PostBriefDTO> getPostsByTag(Long tagId, Integer postType, Boolean featured, long cursor, int size);

    default PageResult<PostBriefDTO> getPostsByTag(Long tagId, long cursor, int size) {
        return getPostsByTag(tagId, null, null, cursor, size);
    }
}
