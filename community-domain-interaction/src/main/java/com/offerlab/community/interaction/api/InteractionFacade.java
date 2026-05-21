package com.offerlab.community.interaction.api;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.interaction.api.dto.CommentCreateCmd;
import com.offerlab.community.interaction.api.dto.CommentDTO;

public interface InteractionFacade {

    void like(Long uid, Long postId);

    void unlike(Long uid, Long postId);

    boolean hasLiked(Long uid, Long postId);

    void favorite(Long uid, Long postId);

    void unfavorite(Long uid, Long postId);

    Long addComment(CommentCreateCmd cmd);

    PageResult<CommentDTO> listComments(Long postId, long cursor, int size);

    void deleteComment(Long commentId, Long operatorUid);
}
