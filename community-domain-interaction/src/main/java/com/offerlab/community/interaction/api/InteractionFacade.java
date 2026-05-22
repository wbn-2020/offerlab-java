package com.offerlab.community.interaction.api;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.interaction.api.dto.CommentCreateCmd;
import com.offerlab.community.interaction.api.dto.CommentDTO;
import com.offerlab.community.post.api.dto.PostBriefDTO;

public interface InteractionFacade {

    void like(Long uid, Long postId);

    void unlike(Long uid, Long postId);

    boolean hasLiked(Long uid, Long postId);

    boolean hasFavorited(Long uid, Long postId);

    void likeComment(Long uid, Long commentId);

    void unlikeComment(Long uid, Long commentId);

    void favorite(Long uid, Long postId);

    void unfavorite(Long uid, Long postId);

    Long addComment(CommentCreateCmd cmd);

    PageResult<CommentDTO> listComments(Long postId, long cursor, int size);

    void deleteComment(Long commentId, Long operatorUid);

    PageResult<PostBriefDTO> listLikedPosts(Long uid, long cursor, int size);

    PageResult<PostBriefDTO> listFavoritePosts(Long uid, long cursor, int size);
}
