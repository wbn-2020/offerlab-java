package com.offerlab.community.interaction.api;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.interaction.api.dto.CommentCreateCmd;
import com.offerlab.community.interaction.api.dto.CommentDTO;
import com.offerlab.community.interaction.api.dto.FavoriteFolderCreateCmd;
import com.offerlab.community.interaction.api.dto.FavoriteFolderDTO;
import com.offerlab.community.interaction.api.dto.FavoriteFolderSortCmd;
import com.offerlab.community.interaction.api.dto.FavoriteFolderUpdateCmd;
import com.offerlab.community.interaction.api.dto.FavoriteBatchMoveCmd;
import com.offerlab.community.interaction.api.dto.FavoriteMoveCmd;
import com.offerlab.community.post.api.dto.PostBriefDTO;

import java.util.List;
import java.util.Set;

public interface InteractionFacade {

    void like(Long uid, Long postId);

    void unlike(Long uid, Long postId);

    boolean hasLiked(Long uid, Long postId);

    boolean hasFavorited(Long uid, Long postId);

    Set<Long> likedPostIds(Long uid, List<Long> postIds);

    Set<Long> favoritedPostIds(Long uid, List<Long> postIds);

    void likeComment(Long uid, Long commentId);

    void unlikeComment(Long uid, Long commentId);

    void favorite(Long uid, Long postId);

    void favorite(Long uid, Long postId, Long folderId);

    void unfavorite(Long uid, Long postId);

    Long addComment(CommentCreateCmd cmd);

    default PageResult<CommentDTO> listComments(Long postId, Long viewerUid, long cursor, int size) {
        return listComments(postId, viewerUid, String.valueOf(cursor), size, "latest");
    }

    default PageResult<CommentDTO> listComments(Long postId, Long viewerUid, long cursor, int size, String sort) {
        return listComments(postId, viewerUid, String.valueOf(cursor), size, sort);
    }

    PageResult<CommentDTO> listComments(Long postId, Long viewerUid, String cursor, int size, String sort);

    PageResult<CommentDTO> listCommentReplies(Long postId, Long rootId, Long viewerUid, String cursor, int size);

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

    default PageResult<PostBriefDTO> listLikedPosts(Long uid, long cursor, int size) {
        return listLikedPosts(uid, String.valueOf(cursor), size);
    }

    PageResult<PostBriefDTO> listLikedPosts(Long uid, String cursor, int size);

    default PageResult<PostBriefDTO> listFavoritePosts(Long uid, long cursor, int size) {
        return listFavoritePosts(uid, String.valueOf(cursor), size);
    }

    PageResult<PostBriefDTO> listFavoritePosts(Long uid, String cursor, int size);

    List<FavoriteFolderDTO> listFavoriteFolders(Long uid);

    FavoriteFolderDTO createFavoriteFolder(Long uid, FavoriteFolderCreateCmd cmd);

    FavoriteFolderDTO updateFavoriteFolder(Long uid, Long folderId, FavoriteFolderUpdateCmd cmd);

    FavoriteFolderDTO sortFavoriteFolder(Long uid, Long folderId, FavoriteFolderSortCmd cmd);

    void deleteFavoriteFolder(Long uid, Long folderId, Long targetFolderId);

    default PageResult<PostBriefDTO> listFavoritePostsInFolder(Long uid, Long folderId, long cursor, int size) {
        return listFavoritePostsInFolder(uid, folderId, String.valueOf(cursor), size);
    }

    PageResult<PostBriefDTO> listFavoritePostsInFolder(Long uid, Long folderId, String cursor, int size);

    FavoriteFolderDTO moveFavorite(Long uid, Long postId, FavoriteMoveCmd cmd);

    FavoriteFolderDTO batchMoveFavorites(Long uid, FavoriteBatchMoveCmd cmd);

    FavoriteFolderDTO getPublicFavoriteFolder(Long folderId);

    List<FavoriteFolderDTO> listPublicFavoriteFoldersByUser(Long uid, int limit);

    default PageResult<PostBriefDTO> listPublicFavoritePostsInFolder(Long folderId, long cursor, int size) {
        return listPublicFavoritePostsInFolder(folderId, String.valueOf(cursor), size);
    }

    PageResult<PostBriefDTO> listPublicFavoritePostsInFolder(Long folderId, String cursor, int size);
}
