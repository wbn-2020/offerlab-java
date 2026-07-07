package com.offerlab.community.interaction.api;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.interaction.api.dto.CommentCreateCmd;
import com.offerlab.community.interaction.api.dto.CommentDTO;
import com.offerlab.community.interaction.api.dto.FavoriteFolderCreateCmd;
import com.offerlab.community.interaction.api.dto.FavoriteFolderDTO;
import com.offerlab.community.interaction.api.dto.FavoriteFolderUpdateCmd;
import com.offerlab.community.interaction.api.dto.FavoriteMoveCmd;
import com.offerlab.community.post.api.dto.PostBriefDTO;

import java.util.List;

public interface InteractionFacade {

    void like(Long uid, Long postId);

    void unlike(Long uid, Long postId);

    boolean hasLiked(Long uid, Long postId);

    boolean hasFavorited(Long uid, Long postId);

    void likeComment(Long uid, Long commentId);

    void unlikeComment(Long uid, Long commentId);

    void favorite(Long uid, Long postId);

    void favorite(Long uid, Long postId, Long folderId);

    void unfavorite(Long uid, Long postId);

    Long addComment(CommentCreateCmd cmd);

    default PageResult<CommentDTO> listComments(Long postId, Long viewerUid, long cursor, int size) {
        return listComments(postId, viewerUid, cursor, size, "latest");
    }

    PageResult<CommentDTO> listComments(Long postId, Long viewerUid, long cursor, int size, String sort);

    void deleteComment(Long commentId, Long operatorUid);

    default void markCommentHelpful(Long uid, Long commentId) {
        throw new UnsupportedOperationException("markCommentHelpful");
    }

    default void unmarkCommentHelpful(Long uid, Long commentId) {
        throw new UnsupportedOperationException("unmarkCommentHelpful");
    }

    default void pinComment(Long operatorUid, Long postId, Long commentId) {
        throw new UnsupportedOperationException("pinComment");
    }

    default void unpinComment(Long operatorUid, Long postId, Long commentId) {
        throw new UnsupportedOperationException("unpinComment");
    }

    default void featureComment(Long operatorUid, Long commentId) {
        throw new UnsupportedOperationException("featureComment");
    }

    default void unfeatureComment(Long operatorUid, Long commentId) {
        throw new UnsupportedOperationException("unfeatureComment");
    }

    default void foldComment(Long operatorUid, Long commentId, String reason) {
        throw new UnsupportedOperationException("foldComment");
    }

    default void unfoldComment(Long operatorUid, Long commentId) {
        throw new UnsupportedOperationException("unfoldComment");
    }

    PageResult<PostBriefDTO> listLikedPosts(Long uid, long cursor, int size);

    PageResult<PostBriefDTO> listFavoritePosts(Long uid, long cursor, int size);

    List<FavoriteFolderDTO> listFavoriteFolders(Long uid);

    FavoriteFolderDTO createFavoriteFolder(Long uid, FavoriteFolderCreateCmd cmd);

    FavoriteFolderDTO updateFavoriteFolder(Long uid, Long folderId, FavoriteFolderUpdateCmd cmd);

    void deleteFavoriteFolder(Long uid, Long folderId);

    PageResult<PostBriefDTO> listFavoritePostsInFolder(Long uid, Long folderId, long cursor, int size);

    FavoriteFolderDTO moveFavorite(Long uid, Long postId, FavoriteMoveCmd cmd);

    FavoriteFolderDTO getPublicFavoriteFolder(Long folderId);

    PageResult<PostBriefDTO> listPublicFavoritePostsInFolder(Long folderId, long cursor, int size);
}
