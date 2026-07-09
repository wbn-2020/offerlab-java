package com.offerlab.community.interaction.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.utils.SqlLimits;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.mq.producer.EventPublisher;
import com.offerlab.community.infra.redis.cache.PostCounterRedis;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.tx.AfterCommitExecutor;
import com.offerlab.community.interaction.api.InteractionFacade;
import com.offerlab.community.interaction.api.dto.CommentCreateCmd;
import com.offerlab.community.interaction.api.dto.CommentDTO;
import com.offerlab.community.interaction.api.dto.FavoriteBatchMoveCmd;
import com.offerlab.community.interaction.api.dto.FavoriteFolderCreateCmd;
import com.offerlab.community.interaction.api.dto.FavoriteFolderDTO;
import com.offerlab.community.interaction.api.dto.FavoriteFolderSortCmd;
import com.offerlab.community.interaction.api.dto.FavoriteFolderUpdateCmd;
import com.offerlab.community.interaction.api.dto.FavoriteMoveCmd;
import com.offerlab.community.interaction.api.event.CommentCreatedEvent;
import com.offerlab.community.interaction.api.event.CommentLikedEvent;
import com.offerlab.community.interaction.api.event.CommentQualitySignalChangedEvent;
import com.offerlab.community.interaction.api.event.PostFavoritedEvent;
import com.offerlab.community.interaction.api.event.PostLikedEvent;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.CommentHelpfulMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.CommentMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.CommentQualitySignalMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.FavoriteFolderMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.FavoriteMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.LikeMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentHelpfulPO;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentPO;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentQualitySignalPO;
import com.offerlab.community.interaction.infrastructure.persistence.po.FavoriteFolderPO;
import com.offerlab.community.interaction.infrastructure.persistence.po.FavoritePO;
import com.offerlab.community.interaction.infrastructure.persistence.po.LikePO;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionFacadeImpl implements InteractionFacade {

    private static final int TARGET_POST = 1;
    private static final int TARGET_COMMENT = 2;
    private static final int COMMENT_STATUS_NORMAL = 1;
    private static final int COMMENT_STATUS_REVIEWING = 2;
    private static final int COMMENT_REPLY_PREVIEW_LIMIT = 5;
    private static final String SORT_QUALITY = "quality";
    private static final String SIGNAL_AUTHOR_PINNED = "AUTHOR_PINNED";
    private static final String SIGNAL_FEATURED = "FEATURED";
    private static final String SIGNAL_LOW_QUALITY_FOLDED = "LOW_QUALITY_FOLDED";
    private static final String OPERATOR_ROLE_AUTHOR = "author";
    private static final String OPERATOR_ROLE_ADMIN = "admin";
    private static final String SOURCE_AUTHOR = "author";
    private static final String SOURCE_MODERATION = "moderation";
    private static final String DEFAULT_FOLD_REASON = "comment folded";
    private static final String DEFAULT_FAVORITE_FOLDER_NAME = "默认收藏夹";
    private static final int FAVORITE_FOLDER_PUBLIC = 1;
    private static final int FAVORITE_FOLDER_PRIVATE = 2;
    private static final int DEFAULT_SORT_ORDER = 0;

    private final LikeMapper likeMapper;
    private final FavoriteMapper favoriteMapper;
    private final FavoriteFolderMapper favoriteFolderMapper;
    private final CommentMapper commentMapper;
    private final CommentHelpfulMapper commentHelpfulMapper;
    private final CommentQualitySignalMapper commentQualitySignalMapper;
    private final PostCounterMapper postCounterMapper;
    private final PostCounterRedis postCounterRedis;
    private final PostFacade postFacade;
    private final UserFacade userFacade;
    private final AdminPermissionService adminPermissionService;
    private final SnowflakeIdGenerator idGen;
    private final EventPublisher events;
    private final AfterCommitExecutor afterCommit;

    @Override
    @Transactional
    public void like(Long uid, Long postId) {
        PostDTO post = postFacade.getPost(postId, uid);
        if (post == null) throw new BizException(ErrorCode.POST_NOT_FOUND);

        try {
            LikePO existing = likeMapper.selectAnyByUserTarget(uid, TARGET_POST, postId);
            if (existing != null) {
                if (existing.getIsDeleted() == 0) {
                    throw new BizException(ErrorCode.LIKE_ALREADY_EXISTS);
                }
                if (likeMapper.restoreById(existing.getId()) <= 0) {
                    throw new BizException(ErrorCode.LIKE_ALREADY_EXISTS);
                }
            } else {
                LikePO po = new LikePO();
                po.setId(idGen.nextId());
                po.setUserId(uid);
                po.setTargetType(TARGET_POST);
                po.setTargetId(postId);
                po.setTargetAuthorId(post.getAuthorId());
                po.setIsDeleted(0);
                likeMapper.insert(po);
            }
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.LIKE_ALREADY_EXISTS);
        }
        // MySQL 计数为权威，Redis 提交后增量刷新
        postCounterMapper.incrLike(postId, 1);
        afterCommit.execute(() -> postCounterRedis.incrLike(postId, 1), "post like counter:" + postId);
        events.publish(PostLikedEvent.builder()
                .uid(uid).postId(postId).postAuthorId(post.getAuthorId()).domain(post.getDomain())
                .timestamp(Instant.now().toEpochMilli()).build());
    }

    @Override
    @Transactional
    public void unlike(Long uid, Long postId) {
        LikePO po = likeMapper.selectOne(new LambdaQueryWrapper<LikePO>()
                .eq(LikePO::getUserId, uid)
                .eq(LikePO::getTargetType, TARGET_POST)
                .eq(LikePO::getTargetId, postId)
                .eq(LikePO::getIsDeleted, 0)
                .last(SqlLimits.limitOne()));
        if (po == null) throw new BizException(ErrorCode.LIKE_NOT_EXISTS);
        if (likeMapper.softDeleteById(po.getId()) <= 0) {
            throw new BizException(ErrorCode.LIKE_NOT_EXISTS);
        }
        // MySQL 计数为权威，Redis 提交后增量刷新
        postCounterMapper.incrLike(postId, -1);
        afterCommit.execute(() -> postCounterRedis.incrLike(postId, -1), "post unlike counter:" + postId);
    }

    @Override
    public boolean hasLiked(Long uid, Long postId) {
        Long cnt = likeMapper.selectCount(new LambdaQueryWrapper<LikePO>()
                .eq(LikePO::getUserId, uid)
                .eq(LikePO::getTargetType, TARGET_POST)
                .eq(LikePO::getTargetId, postId)
                .eq(LikePO::getIsDeleted, 0));
        return cnt != null && cnt > 0;
    }

    @Override
    public boolean hasFavorited(Long uid, Long postId) {
        Long cnt = favoriteMapper.selectCount(new LambdaQueryWrapper<FavoritePO>()
                .eq(FavoritePO::getUserId, uid)
                .eq(FavoritePO::getPostId, postId)
                .eq(FavoritePO::getIsDeleted, 0));
        return cnt != null && cnt > 0;
    }

    @Override
    public Set<Long> likedPostIds(Long uid, List<Long> postIds) {
        if (uid == null || postIds == null || postIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = postIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(likeMapper.selectActiveTargetIdsByUser(uid, TARGET_POST, ids));
    }

    @Override
    public Set<Long> favoritedPostIds(Long uid, List<Long> postIds) {
        if (uid == null || postIds == null || postIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = postIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(favoriteMapper.selectActivePostIdsByUser(uid, ids));
    }

    @Override
    @Transactional
    public void likeComment(Long uid, Long commentId) {
        CommentPO comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getCommentStatus() == null || comment.getCommentStatus() != COMMENT_STATUS_NORMAL) {
            throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
        }
        requirePostVisible(comment.getPostId(), uid);
        try {
            LikePO existing = likeMapper.selectAnyByUserTarget(uid, TARGET_COMMENT, commentId);
            if (existing != null) {
                if (existing.getIsDeleted() == 0) {
                    throw new BizException(ErrorCode.LIKE_ALREADY_EXISTS);
                }
                if (likeMapper.restoreById(existing.getId()) <= 0) {
                    throw new BizException(ErrorCode.LIKE_ALREADY_EXISTS);
                }
            } else {
                LikePO po = new LikePO();
                po.setId(idGen.nextId());
                po.setUserId(uid);
                po.setTargetType(TARGET_COMMENT);
                po.setTargetId(commentId);
                po.setTargetAuthorId(comment.getAuthorId());
                po.setIsDeleted(0);
                likeMapper.insert(po);
            }
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.LIKE_ALREADY_EXISTS);
        }
        updateCommentLikeCount(commentId, 1);
        events.publish(CommentLikedEvent.builder()
                .uid(uid)
                .commentId(commentId)
                .commentAuthorId(comment.getAuthorId())
                .postId(comment.getPostId())
                .timestamp(Instant.now().toEpochMilli())
                .build());
    }

    @Override
    @Transactional
    public void unlikeComment(Long uid, Long commentId) {
        CommentPO comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getCommentStatus() == null || comment.getCommentStatus() != COMMENT_STATUS_NORMAL) {
            throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
        }
        requirePostVisible(comment.getPostId(), uid);
        LikePO po = likeMapper.selectOne(new LambdaQueryWrapper<LikePO>()
                .eq(LikePO::getUserId, uid)
                .eq(LikePO::getTargetType, TARGET_COMMENT)
                .eq(LikePO::getTargetId, commentId)
                .eq(LikePO::getIsDeleted, 0)
                .last(SqlLimits.limitOne()));
        if (po == null) throw new BizException(ErrorCode.LIKE_NOT_EXISTS);
        if (likeMapper.softDeleteById(po.getId()) <= 0) {
            throw new BizException(ErrorCode.LIKE_NOT_EXISTS);
        }
        updateCommentLikeCount(commentId, -1);
    }

    @Override
    @Transactional
    public void favorite(Long uid, Long postId) {
        favorite(uid, postId, null);
    }

    @Override
    @Transactional
    public void favorite(Long uid, Long postId, Long folderId) {
        PostDTO post = postFacade.getPost(postId, uid);
        if (post == null) throw new BizException(ErrorCode.POST_NOT_FOUND);
        FavoriteFolderPO folder = resolveFavoriteFolder(uid, folderId);

        try {
            FavoritePO existing = favoriteMapper.selectAnyByUserPost(uid, postId);
            if (existing != null) {
                if (existing.getIsDeleted() == 0) {
                    throw new BizException(ErrorCode.FAVORITE_ALREADY_EXISTS);
                }
                if (favoriteMapper.restoreToFolder(existing.getId(), folder.getId(), DEFAULT_SORT_ORDER) <= 0) {
                    throw new BizException(ErrorCode.FAVORITE_ALREADY_EXISTS);
                }
            } else {
                FavoritePO po = new FavoritePO();
                po.setId(idGen.nextId());
                po.setUserId(uid);
                po.setPostId(postId);
                po.setFolderId(folder.getId());
                po.setSortOrder(DEFAULT_SORT_ORDER);
                po.setIsDeleted(0);
                favoriteMapper.insert(po);
            }
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }
        // MySQL 计数为权威，Redis 提交后增量刷新
        incrementFolderPostCount(uid, folder.getId(), 1);
        postCounterMapper.incrFavorite(postId, 1);
        afterCommit.execute(() -> postCounterRedis.incrFavorite(postId, 1), "post favorite counter:" + postId);
        events.publish(PostFavoritedEvent.builder()
                .uid(uid).postId(postId).postAuthorId(post.getAuthorId()).domain(post.getDomain())
                .timestamp(Instant.now().toEpochMilli()).build());
    }

    @Override
    @Transactional
    public void unfavorite(Long uid, Long postId) {
        FavoritePO po = favoriteMapper.selectOne(new LambdaQueryWrapper<FavoritePO>()
                .eq(FavoritePO::getUserId, uid)
                .eq(FavoritePO::getPostId, postId)
                .eq(FavoritePO::getIsDeleted, 0)
                .last(SqlLimits.limitOne()));
        if (po == null) throw new BizException(ErrorCode.FAVORITE_NOT_EXISTS);
        if (favoriteMapper.softDeleteById(po.getId()) <= 0) {
            throw new BizException(ErrorCode.FAVORITE_NOT_EXISTS);
        }
        // MySQL 计数为权威，Redis 提交后增量刷新
        decrementFolderPostCount(uid, po.getFolderId());
        postCounterMapper.incrFavorite(postId, -1);
        afterCommit.execute(() -> postCounterRedis.incrFavorite(postId, -1), "post unfavorite counter:" + postId);
    }

    @Override
    @Transactional
    public Long addComment(CommentCreateCmd cmd) {
        PostDTO post = postFacade.getPost(cmd.getPostId(), cmd.getAuthorUid());
        if (post == null) throw new BizException(ErrorCode.POST_NOT_FOUND);

        long id = cmd.getCommentId() == null || cmd.getCommentId() <= 0 ? idGen.nextId() : cmd.getCommentId();
        CommentPO po = new CommentPO();
        po.setId(id);
        po.setPostId(cmd.getPostId());
        po.setPostAuthorId(post.getAuthorId());
        po.setAuthorId(cmd.getAuthorUid());
        po.setContent(cmd.getContent());
        po.setLikeCount(0);
        po.setHelpfulCount(0);
        boolean reviewRequired = Boolean.TRUE.equals(cmd.getReviewRequired());
        po.setCommentStatus(reviewRequired ? COMMENT_STATUS_REVIEWING : COMMENT_STATUS_NORMAL);

        if (cmd.getParentId() != null && cmd.getParentId() > 0) {
            CommentPO parent = commentMapper.selectById(cmd.getParentId());
            if (parent == null || parent.getCommentStatus() == null || parent.getCommentStatus() != COMMENT_STATUS_NORMAL
                    || !Objects.equals(parent.getPostId(), cmd.getPostId())) {
                throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
            }
            po.setParentId(cmd.getParentId());
            po.setRootId(parent.getRootId() == null || parent.getRootId() == 0 ? parent.getId() : parent.getRootId());
            po.setReplyToUid(parent.getAuthorId());
        } else {
            po.setParentId(0L);
            po.setRootId(0L);
        }

        commentMapper.insert(po);
        if (!reviewRequired) {
            // MySQL 计数为权威，Redis 提交后增量刷新
            postCounterMapper.incrComment(cmd.getPostId(), 1);
            afterCommit.execute(() -> postCounterRedis.incrComment(cmd.getPostId(), 1), "post comment counter:" + cmd.getPostId());
            events.publish(CommentCreatedEvent.builder()
                    .uid(cmd.getAuthorUid())
                    .postId(cmd.getPostId())
                    .postAuthorId(post.getAuthorId())
                    .commentId(id)
                    .parentId(po.getParentId())
                    .replyToUid(po.getReplyToUid())
                    .content(cmd.getContent())
                    .domain(post.getDomain())
                    .timestamp(Instant.now().toEpochMilli())
                    .build());
        }
        return id;
    }

    @Override
    public PageResult<CommentDTO> listComments(Long postId, Long viewerUid, String cursor, int size, String sort) {
        requirePostVisible(postId, viewerUid);
        int limit = clampPageSize(size);
        boolean qualitySort = SORT_QUALITY.equalsIgnoreCase(sort == null ? "" : sort.trim());
        Cursor parsedCursor = parseCursor(cursor);
        LocalDateTime beforeCreateTime = parsedCursor.time();
        Long beforeId = parsedCursor.id();
        List<CommentPO> roots;
        if (qualitySort) {
            roots = commentMapper.selectQualityRoots(postId, beforeCreateTime, beforeId, limit + 1);
        } else {
            LambdaQueryWrapper<CommentPO> q = new LambdaQueryWrapper<CommentPO>()
                    .eq(CommentPO::getPostId, postId)
                    .eq(CommentPO::getRootId, 0L)             // 仅一级
                    .eq(CommentPO::getCommentStatus, COMMENT_STATUS_NORMAL)
                    .eq(CommentPO::getCommentStatus, COMMENT_STATUS_NORMAL)
                    .orderByDesc(CommentPO::getCreateTime)
                    .orderByDesc(CommentPO::getId)
                    .last(SqlLimits.limit(limit + 1, 1, 51));
            if (beforeCreateTime != null) {
                q.and(wrapper -> wrapper.lt(CommentPO::getCreateTime, beforeCreateTime)
                        .or(beforeId != null, nested -> nested.eq(CommentPO::getCreateTime, beforeCreateTime)
                                .lt(CommentPO::getId, beforeId)));
            }
            roots = commentMapper.selectList(q);
        }
        if (roots.isEmpty()) return PageResult.empty();
        boolean hasMore = roots.size() > limit;
        if (hasMore) {
            roots = roots.subList(0, limit);
        }

        List<Long> rootIds = roots.stream().map(CommentPO::getId).toList();
        List<CommentPO> replies = commentMapper.selectPreviewRepliesByRootIds(
                postId, rootIds, COMMENT_REPLY_PREVIEW_LIMIT);
        List<CommentPO> all = new java.util.ArrayList<>(roots.size() + replies.size());
        all.addAll(roots);
        all.addAll(replies);

        Map<Long, UserBriefDTO> users = usersFor(all);
        Set<Long> likedCommentIds = likedCommentIds(viewerUid, all);
        Set<Long> helpfulCommentIds = helpfulCommentIds(viewerUid, all);
        Map<Long, List<CommentQualitySignalPO>> signalsByComment = activeSignalsByComment(all);
        Map<Long, Long> replyCountByRoot = commentMapper.countRepliesByRootIds(postId, rootIds).stream()
                .collect(Collectors.toMap(
                        row -> mapLong(row, "rootId"),
                        row -> mapLong(row, "replyCount"),
                        Long::sum));
        Map<Long, List<CommentDTO>> repliesByRoot = replies.stream()
                .sorted(Comparator.comparing(CommentPO::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(po -> toDto(po, viewerUid, users, likedCommentIds, helpfulCommentIds, signalsByComment, Map.of()))
                .collect(Collectors.groupingBy(CommentDTO::getRootId));
        List<CommentDTO> items = roots.stream()
                .map(po -> {
                    CommentDTO dto = toDto(po, viewerUid, users, likedCommentIds, helpfulCommentIds, signalsByComment, replyCountByRoot);
                    List<CommentDTO> previewReplies = repliesByRoot.getOrDefault(po.getId(), List.of());
                    dto.setReplies(previewReplies);
                    dto.setReplyCount(Math.max(dto.getReplyCount(), dto.getReplies().size()));
                    dto.setHasMoreReplies(dto.getReplyCount() > dto.getReplies().size());
                    if (Boolean.TRUE.equals(dto.getHasMoreReplies()) && !previewReplies.isEmpty()) {
                        dto.setRepliesNextCursor(commentCursor(previewReplies.get(previewReplies.size() - 1)));
                    }
                    return dto;
                })
                .toList();
        String next = hasMore ? commentCursor(roots.get(roots.size() - 1)) : null;
        return PageResult.of(items, next, hasMore);
    }

    @Override
    public PageResult<CommentDTO> listCommentReplies(Long postId, Long rootId, Long viewerUid, String cursor, int size) {
        requirePostVisible(postId, viewerUid);
        if (rootId == null || rootId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        CommentPO root = requireNormalComment(rootId);
        requireCommentInPost(root, postId);
        if (root.getRootId() != null && root.getRootId() > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int limit = clampPageSize(size);
        Cursor parsedCursor = parseCursor(cursor);
        List<CommentPO> rows = commentMapper.selectRepliesByRootId(
                postId, rootId, parsedCursor.time(), parsedCursor.id(), limit + 1);
        if (rows.isEmpty()) {
            return PageResult.empty();
        }
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }
        Map<Long, UserBriefDTO> users = usersFor(rows);
        Set<Long> likedCommentIds = likedCommentIds(viewerUid, rows);
        Set<Long> helpfulCommentIds = helpfulCommentIds(viewerUid, rows);
        Map<Long, List<CommentQualitySignalPO>> signalsByComment = activeSignalsByComment(rows);
        List<CommentDTO> items = rows.stream()
                .map(po -> toDto(po, viewerUid, users, likedCommentIds, helpfulCommentIds, signalsByComment, Map.of()))
                .toList();
        String next = hasMore ? commentCursor(rows.get(rows.size() - 1)) : null;
        return PageResult.of(items, next, hasMore);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long operatorUid) {
        CommentPO po = commentMapper.selectById(commentId);
        if (po == null) throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
        requirePostVisible(po.getPostId(), operatorUid);
        if (!Objects.equals(po.getAuthorId(), operatorUid) && !Objects.equals(po.getPostAuthorId(), operatorUid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        LambdaQueryWrapper<CommentPO> deleteQuery = new LambdaQueryWrapper<CommentPO>()
                .eq(CommentPO::getPostId, po.getPostId())
                .eq(CommentPO::getCommentStatus, COMMENT_STATUS_NORMAL);
        if (po.getRootId() == null || po.getRootId() == 0L) {
            deleteQuery.and(q -> q.eq(CommentPO::getId, po.getId()).or().eq(CommentPO::getRootId, po.getId()));
        } else {
            deleteQuery.eq(CommentPO::getId, po.getId());
        }
        int deletedCount = commentMapper.delete(deleteQuery);
        if (deletedCount <= 0) {
            return;
        }
        long deleted = deletedCount;
        // MySQL 计数为权威，Redis 提交后增量刷新
        postCounterMapper.incrComment(po.getPostId(), -deleted);
        afterCommit.execute(() -> postCounterRedis.incrComment(po.getPostId(), -deleted), "post comment delete counter:" + po.getPostId());
    }

    @Override
    @Transactional
    public void markCommentHelpful(Long uid, Long commentId) {
        CommentPO comment = requireNormalComment(commentId);
        requirePostVisible(comment.getPostId(), uid);
        try {
            CommentHelpfulPO existing = commentHelpfulMapper.selectByUidAndComment(uid, commentId);
            if (existing != null) {
                if (existing.getIsDeleted() != null && existing.getIsDeleted() == 0
                        && existing.getHelpfulStatus() != null && existing.getHelpfulStatus() == 1) {
                    throw new BizException(ErrorCode.DUPLICATE_OPERATION);
                }
                if (commentHelpfulMapper.restoreById(existing.getId()) <= 0) {
                    throw new BizException(ErrorCode.DUPLICATE_OPERATION);
                }
            } else {
                CommentHelpfulPO po = new CommentHelpfulPO();
                po.setId(idGen.nextId());
                po.setUid(uid);
                po.setPostId(comment.getPostId());
                po.setCommentId(commentId);
                po.setHelpfulStatus(1);
                po.setIsDeleted(0);
                commentHelpfulMapper.insert(po);
            }
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        updateCommentHelpfulCount(commentId, 1);
    }

    @Override
    @Transactional
    public void unmarkCommentHelpful(Long uid, Long commentId) {
        CommentPO comment = requireNormalComment(commentId);
        requirePostVisible(comment.getPostId(), uid);
        CommentHelpfulPO existing = commentHelpfulMapper.selectByUidAndComment(uid, commentId);
        if (existing == null || existing.getIsDeleted() == null || existing.getIsDeleted() != 0
                || existing.getHelpfulStatus() == null || existing.getHelpfulStatus() != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        if (commentHelpfulMapper.softDeleteById(existing.getId()) <= 0) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        updateCommentHelpfulCount(commentId, -1);
    }

    @Override
    @Transactional
    public void pinComment(Long operatorUid, Long postId, Long commentId) {
        CommentPO comment = requireNormalComment(commentId);
        requireCommentInPost(comment, postId);
        PostDTO post = requirePostVisible(postId, operatorUid);
        requireAuthorOrModerator(operatorUid, post);
        commentQualitySignalMapper.deactivateOtherActiveByPostAndType(postId, SIGNAL_AUTHOR_PINNED, commentId);
        setQualitySignal(comment, operatorUid, SIGNAL_AUTHOR_PINNED, true, null, operatorRole(operatorUid, post), signalSource(operatorUid, post));
    }

    @Override
    @Transactional
    public void unpinComment(Long operatorUid, Long postId, Long commentId) {
        CommentPO comment = requireNormalComment(commentId);
        requireCommentInPost(comment, postId);
        PostDTO post = requirePostVisible(postId, operatorUid);
        requireAuthorOrModerator(operatorUid, post);
        setQualitySignal(comment, operatorUid, SIGNAL_AUTHOR_PINNED, false, null, operatorRole(operatorUid, post), signalSource(operatorUid, post));
    }

    @Override
    @Transactional
    public void featureComment(Long operatorUid, Long commentId) {
        CommentPO comment = requireNormalComment(commentId);
        PostDTO post = requirePostVisible(comment.getPostId(), operatorUid);
        requireAuthorOrModerator(operatorUid, post);
        setQualitySignal(comment, operatorUid, SIGNAL_FEATURED, true, null, operatorRole(operatorUid, post), signalSource(operatorUid, post));
    }

    @Override
    @Transactional
    public void unfeatureComment(Long operatorUid, Long commentId) {
        CommentPO comment = requireNormalComment(commentId);
        PostDTO post = requirePostVisible(comment.getPostId(), operatorUid);
        requireAuthorOrModerator(operatorUid, post);
        setQualitySignal(comment, operatorUid, SIGNAL_FEATURED, false, null, operatorRole(operatorUid, post), signalSource(operatorUid, post));
    }

    @Override
    @Transactional
    public void foldComment(Long operatorUid, Long commentId, String reason) {
        CommentPO comment = requireNormalComment(commentId);
        requirePostVisible(comment.getPostId(), operatorUid);
        requireModerator(operatorUid);
        String foldReason = normalizeReason(reason);
        setQualitySignal(comment, operatorUid, SIGNAL_LOW_QUALITY_FOLDED, true, foldReason, OPERATOR_ROLE_ADMIN, SOURCE_MODERATION);
    }

    @Override
    @Transactional
    public void unfoldComment(Long operatorUid, Long commentId) {
        CommentPO comment = requireNormalComment(commentId);
        requirePostVisible(comment.getPostId(), operatorUid);
        requireModerator(operatorUid);
        setQualitySignal(comment, operatorUid, SIGNAL_LOW_QUALITY_FOLDED, false, null, OPERATOR_ROLE_ADMIN, SOURCE_MODERATION);
    }

    @Override
    public PageResult<PostBriefDTO> listLikedPosts(Long uid, String cursor, int size) {
        int limit = clampPageSize(size);
        Cursor parsedCursor = parseCursor(cursor);
        LambdaQueryWrapper<LikePO> q = new LambdaQueryWrapper<LikePO>()
                .eq(LikePO::getUserId, uid)
                .eq(LikePO::getTargetType, TARGET_POST)
                .eq(LikePO::getIsDeleted, 0)
                .orderByDesc(LikePO::getCreateTime)
                .orderByDesc(LikePO::getId)
                .last(SqlLimits.limit(limit + 1, 1, 51));
        if (parsedCursor.time() != null) {
            q.and(wrapper -> wrapper.lt(LikePO::getCreateTime, parsedCursor.time())
                    .or(parsedCursor.id() != null, nested -> nested.eq(LikePO::getCreateTime, parsedCursor.time())
                            .lt(LikePO::getId, parsedCursor.id())));
        }
        List<LikePO> list = likeMapper.selectList(q);
        return postPage(list.stream().map(LikePO::getTargetId).toList(), list, limit, uid);
    }

    @Override
    public PageResult<PostBriefDTO> listFavoritePosts(Long uid, String cursor, int size) {
        int limit = clampPageSize(size);
        Cursor parsedCursor = parseCursor(cursor);
        LambdaQueryWrapper<FavoritePO> q = new LambdaQueryWrapper<FavoritePO>()
                .eq(FavoritePO::getUserId, uid)
                .eq(FavoritePO::getIsDeleted, 0)
                .orderByDesc(FavoritePO::getCreateTime)
                .orderByDesc(FavoritePO::getId)
                .last(SqlLimits.limit(limit + 1, 1, 51));
        if (parsedCursor.time() != null) {
            q.and(wrapper -> wrapper.lt(FavoritePO::getCreateTime, parsedCursor.time())
                    .or(parsedCursor.id() != null, nested -> nested.eq(FavoritePO::getCreateTime, parsedCursor.time())
                            .lt(FavoritePO::getId, parsedCursor.id())));
        }
        List<FavoritePO> list = favoriteMapper.selectList(q);
        return postPage(list.stream().map(FavoritePO::getPostId).toList(), list, limit, uid);
    }

    @Override
    @Transactional
    public List<FavoriteFolderDTO> listFavoriteFolders(Long uid) {
        ensureDefaultFolder(uid);
        return favoriteFolderMapper.selectActiveByUserId(uid).stream()
                .map(this::toFavoriteFolderDTO)
                .toList();
    }

    @Override
    @Transactional
    public FavoriteFolderDTO createFavoriteFolder(Long uid, FavoriteFolderCreateCmd cmd) {
        String name = normalizeFolderName(cmd.getName());
        requireUniqueFavoriteFolderName(uid, name, null);
        FavoriteFolderPO po = new FavoriteFolderPO();
        po.setId(idGen.nextId());
        po.setUserId(uid);
        po.setName(name);
        po.setDescription(normalizeFolderDescription(cmd.getDescription()));
        po.setVisibility(normalizeFolderVisibility(cmd.getVisibility(), cmd.getPrivateFolder(), FAVORITE_FOLDER_PRIVATE));
        po.setSortOrder(DEFAULT_SORT_ORDER);
        po.setIsDefault(0);
        po.setPostCount(0L);
        po.setIsDeleted(0);
        favoriteFolderMapper.insert(po);
        return toFavoriteFolderDTO(favoriteFolderMapper.selectById(po.getId()));
    }

    @Override
    @Transactional
    public FavoriteFolderDTO updateFavoriteFolder(Long uid, Long folderId, FavoriteFolderUpdateCmd cmd) {
        FavoriteFolderPO folder = requireOwnedFolder(uid, folderId);
        LambdaUpdateWrapper<FavoriteFolderPO> update = new LambdaUpdateWrapper<FavoriteFolderPO>()
                .eq(FavoriteFolderPO::getId, folder.getId())
                .eq(FavoriteFolderPO::getUserId, uid)
                .eq(FavoriteFolderPO::getIsDeleted, 0);
        boolean changed = false;
        if (cmd.getName() != null && !cmd.getName().isBlank()) {
            String name = normalizeFolderName(cmd.getName());
            requireUniqueFavoriteFolderName(uid, name, folder.getId());
            update.set(FavoriteFolderPO::getName, name);
            changed = true;
        }
        if (cmd.getDescription() != null) {
            update.set(FavoriteFolderPO::getDescription, normalizeFolderDescription(cmd.getDescription()));
            changed = true;
        }
        Integer requestedVisibility = normalizeFolderVisibility(cmd.getVisibility(), cmd.getPrivateFolder(), null);
        if (requestedVisibility != null) {
            int visibility = isDefaultFolder(folder) ? FAVORITE_FOLDER_PRIVATE : requestedVisibility;
            update.set(FavoriteFolderPO::getVisibility, visibility);
            changed = true;
        }
        if (changed) {
            favoriteFolderMapper.update(null, update);
        }
        return toFavoriteFolderDTO(favoriteFolderMapper.selectById(folder.getId()));
    }

    @Override
    @Transactional
    public FavoriteFolderDTO sortFavoriteFolder(Long uid, Long folderId, FavoriteFolderSortCmd cmd) {
        if (cmd == null || cmd.getSortOrder() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        FavoriteFolderPO folder = requireOwnedFolder(uid, folderId);
        favoriteFolderMapper.update(null, new LambdaUpdateWrapper<FavoriteFolderPO>()
                .eq(FavoriteFolderPO::getId, folder.getId())
                .eq(FavoriteFolderPO::getUserId, uid)
                .eq(FavoriteFolderPO::getIsDeleted, 0)
                .set(FavoriteFolderPO::getSortOrder, cmd.getSortOrder()));
        return toFavoriteFolderDTO(favoriteFolderMapper.selectById(folder.getId()));
    }

    @Override
    @Transactional
    public void deleteFavoriteFolder(Long uid, Long folderId, Long targetFolderId) {
        FavoriteFolderPO folder = requireOwnedFolder(uid, folderId);
        if (isDefaultFolder(folder)) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "默认收藏夹不可删除");
        }
        FavoriteFolderPO target = resolveFavoriteFolder(uid, targetFolderId);
        if (Objects.equals(folder.getId(), target.getId())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "迁移目标不能是当前收藏夹");
        }
        long activeFavorites = countActiveFavoritesInFolder(uid, folderId);
        if (activeFavorites > 0) {
            favoriteMapper.moveFolderFavorites(uid, folder.getId(), target.getId(), DEFAULT_SORT_ORDER);
        }
        favoriteFolderMapper.update(null, new LambdaUpdateWrapper<FavoriteFolderPO>()
                .eq(FavoriteFolderPO::getId, folderId)
                .eq(FavoriteFolderPO::getUserId, uid)
                .eq(FavoriteFolderPO::getIsDeleted, 0)
                .set(FavoriteFolderPO::getIsDeleted, 1));
        syncFolderPostCount(uid, target.getId());
    }

    @Override
    @Transactional
    public PageResult<PostBriefDTO> listFavoritePostsInFolder(Long uid, Long folderId, String cursor, int size) {
        FavoriteFolderPO folder = resolveFavoriteFolder(uid, folderId);
        int limit = clampPageSize(size);
        Cursor parsedCursor = parseCursor(cursor);
        LambdaQueryWrapper<FavoritePO> q = new LambdaQueryWrapper<FavoritePO>()
                .eq(FavoritePO::getUserId, uid)
                .eq(FavoritePO::getFolderId, folder.getId())
                .eq(FavoritePO::getIsDeleted, 0)
                .orderByDesc(FavoritePO::getCreateTime)
                .orderByDesc(FavoritePO::getId)
                .last(SqlLimits.limit(limit + 1, 1, 51));
        if (parsedCursor.time() != null) {
            q.and(wrapper -> wrapper.lt(FavoritePO::getCreateTime, parsedCursor.time())
                    .or(parsedCursor.id() != null, nested -> nested.eq(FavoritePO::getCreateTime, parsedCursor.time())
                            .lt(FavoritePO::getId, parsedCursor.id())));
        }
        List<FavoritePO> list = favoriteMapper.selectList(q);
        return postPage(list.stream().map(FavoritePO::getPostId).toList(), list, limit, uid);
    }

    @Override
    @Transactional
    public FavoriteFolderDTO moveFavorite(Long uid, Long postId, FavoriteMoveCmd cmd) {
        FavoritePO favorite = favoriteMapper.selectOne(new LambdaQueryWrapper<FavoritePO>()
                .eq(FavoritePO::getUserId, uid)
                .eq(FavoritePO::getPostId, postId)
                .eq(FavoritePO::getIsDeleted, 0)
                .last(SqlLimits.limitOne()));
        if (favorite == null) throw new BizException(ErrorCode.FAVORITE_NOT_EXISTS);
        FavoriteFolderPO target = resolveFavoriteFolder(uid, cmd == null ? null : cmd.getFolderId());
        Long sourceFolderId = favorite.getFolderId();
        if (Objects.equals(sourceFolderId, target.getId())) {
            return toFavoriteFolderDTO(target);
        }
        if (favoriteMapper.moveToFolder(favorite.getId(), uid, sourceFolderId, target.getId(), DEFAULT_SORT_ORDER) <= 0) {
            throw new BizException(ErrorCode.FAVORITE_NOT_EXISTS);
        }
        syncFolderPostCounts(uid, sourceFolderId, target.getId());
        return toFavoriteFolderDTO(favoriteFolderMapper.selectById(target.getId()));
    }

    @Override
    @Transactional
    public FavoriteFolderDTO batchMoveFavorites(Long uid, FavoriteBatchMoveCmd cmd) {
        if (cmd == null || cmd.getPostIds() == null || cmd.getPostIds().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        List<Long> postIds = cmd.getPostIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(51)
                .toList();
        if (postIds.isEmpty() || postIds.size() > 50) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "单次最多移动 50 条收藏");
        }
        FavoriteFolderPO target = resolveFavoriteFolder(uid, cmd.getFolderId());
        List<FavoritePO> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<FavoritePO>()
                .eq(FavoritePO::getUserId, uid)
                .eq(FavoritePO::getIsDeleted, 0)
                .in(FavoritePO::getPostId, postIds));
        if (favorites.isEmpty()) {
            throw new BizException(ErrorCode.FAVORITE_NOT_EXISTS);
        }
        Set<Long> touchedFolderIds = favorites.stream()
                .map(FavoritePO::getFolderId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toCollection(HashSet::new));
        touchedFolderIds.add(target.getId());
        for (FavoritePO favorite : favorites) {
            if (Objects.equals(favorite.getFolderId(), target.getId())) {
                continue;
            }
            if (favoriteMapper.moveToFolder(favorite.getId(), uid, favorite.getFolderId(), target.getId(), DEFAULT_SORT_ORDER) <= 0) {
                throw new BizException(ErrorCode.FAVORITE_NOT_EXISTS);
            }
        }
        syncFolderPostCounts(uid, touchedFolderIds.toArray(Long[]::new));
        return toFavoriteFolderDTO(favoriteFolderMapper.selectById(target.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public FavoriteFolderDTO getPublicFavoriteFolder(Long folderId) {
        return toFavoriteFolderDTO(requirePublicFolder(folderId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FavoriteFolderDTO> listPublicFavoriteFoldersByUser(Long uid, int limit) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 6 : limit, 20));
        return favoriteFolderMapper.selectPublicByUserId(uid, safeLimit).stream()
                .map(this::toFavoriteFolderDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<PostBriefDTO> listPublicFavoritePostsInFolder(Long folderId, String cursor, int size) {
        FavoriteFolderPO folder = requirePublicFolder(folderId);
        int limit = clampPageSize(size);
        Cursor parsedCursor = parseCursor(cursor);
        LambdaQueryWrapper<FavoritePO> q = new LambdaQueryWrapper<FavoritePO>()
                .eq(FavoritePO::getUserId, folder.getUserId())
                .eq(FavoritePO::getFolderId, folder.getId())
                .eq(FavoritePO::getIsDeleted, 0)
                .orderByDesc(FavoritePO::getCreateTime)
                .orderByDesc(FavoritePO::getId)
                .last(SqlLimits.limit(limit + 1, 1, 51));
        if (parsedCursor.time() != null) {
            q.and(wrapper -> wrapper.lt(FavoritePO::getCreateTime, parsedCursor.time())
                    .or(parsedCursor.id() != null, nested -> nested.eq(FavoritePO::getCreateTime, parsedCursor.time())
                            .lt(FavoritePO::getId, parsedCursor.id())));
        }
        List<FavoritePO> list = favoriteMapper.selectList(q);
        return publicPostPage(list.stream().map(FavoritePO::getPostId).toList(), list, limit);
    }

    private FavoriteFolderPO resolveFavoriteFolder(Long uid, Long folderId) {
        if (folderId == null || folderId <= 0) {
            return ensureDefaultFolder(uid);
        }
        return requireOwnedFolder(uid, folderId);
    }

    private FavoriteFolderPO ensureDefaultFolder(Long uid) {
        FavoriteFolderPO folder = favoriteFolderMapper.selectDefaultByUserId(uid);
        if (folder == null) {
            try {
                favoriteFolderMapper.insertDefaultIfMissing(
                        idGen.nextId(),
                        uid,
                        DEFAULT_FAVORITE_FOLDER_NAME,
                        null,
                        FAVORITE_FOLDER_PRIVATE,
                        DEFAULT_SORT_ORDER);
            } catch (DuplicateKeyException ignored) {
                // Concurrent request created the active default folder.
            }
            folder = favoriteFolderMapper.selectDefaultByUserId(uid);
        }
        if (folder == null) {
            throw new BizException(ErrorCode.DATABASE_ERROR);
        }
        migrateLegacyFavoritesToDefault(uid, folder.getId());
        syncFolderPostCount(uid, folder.getId());
        return favoriteFolderMapper.selectActiveByIdForUser(folder.getId(), uid);
    }

    private FavoriteFolderPO requireOwnedFolder(Long uid, Long folderId) {
        FavoriteFolderPO folder = favoriteFolderMapper.selectActiveByIdForUser(folderId, uid);
        if (folder == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return folder;
    }

    private FavoriteFolderPO requirePublicFolder(Long folderId) {
        FavoriteFolderPO folder = favoriteFolderMapper.selectById(folderId);
        if (folder == null
                || Objects.equals(folder.getIsDeleted(), 1)
                || !Objects.equals(folder.getVisibility(), FAVORITE_FOLDER_PUBLIC)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return folder;
    }

    private void migrateLegacyFavoritesToDefault(Long uid, Long defaultFolderId) {
        favoriteMapper.update(null, new LambdaUpdateWrapper<FavoritePO>()
                .eq(FavoritePO::getUserId, uid)
                .eq(FavoritePO::getFolderId, 0L)
                .eq(FavoritePO::getIsDeleted, 0)
                .set(FavoritePO::getFolderId, defaultFolderId));
    }

    private void syncFolderPostCount(Long uid, Long folderId) {
        if (favoriteFolderMapper.recountPostCount(folderId, uid) <= 0) {
            throw new BizException(ErrorCode.DATABASE_ERROR);
        }
    }

    private void syncFolderPostCounts(Long uid, Long... folderIds) {
        if (folderIds == null || folderIds.length == 0) {
            return;
        }
        java.util.Arrays.stream(folderIds)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .sorted()
                .forEach(folderId -> syncFolderPostCount(uid, folderId));
    }

    private long countActiveFavoritesInFolder(Long uid, Long folderId) {
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<FavoritePO>()
                .eq(FavoritePO::getUserId, uid)
                .eq(FavoritePO::getFolderId, folderId)
                .eq(FavoritePO::getIsDeleted, 0));
        return count == null ? 0L : count;
    }

    private void incrementFolderPostCount(Long uid, Long folderId, int delta) {
        if (folderId == null || folderId <= 0) {
            return;
        }
        favoriteFolderMapper.changePostCount(folderId, uid, delta);
    }

    private void decrementFolderPostCount(Long uid, Long folderId) {
        incrementFolderPostCount(uid, folderId, -1);
    }

    private static boolean isDefaultFolder(FavoriteFolderPO folder) {
        return folder != null && Objects.equals(folder.getIsDefault(), 1);
    }

    private void requireUniqueFavoriteFolderName(Long uid, String name, Long excludeId) {
        if (favoriteFolderMapper.countActiveByName(uid, name, excludeId) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "favorite folder name already exists");
        }
    }

    private static String normalizeFolderName(String name) {
        if (name == null || name.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String trimmed = name.trim();
        return trimmed.length() > 50 ? trimmed.substring(0, 50) : trimmed;
    }

    private static String normalizeFolderDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    private static Integer normalizeFolderVisibility(String visibility, Boolean privateFolder, Integer defaultValue) {
        if (privateFolder != null) {
            return privateFolder ? FAVORITE_FOLDER_PRIVATE : FAVORITE_FOLDER_PUBLIC;
        }
        if (visibility == null || visibility.isBlank()) {
            return defaultValue;
        }
        String normalized = visibility.trim().toLowerCase();
        if ("public".equals(normalized) || "1".equals(normalized)) {
            return FAVORITE_FOLDER_PUBLIC;
        }
        if ("private".equals(normalized) || "2".equals(normalized)) {
            return FAVORITE_FOLDER_PRIVATE;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private FavoriteFolderDTO toFavoriteFolderDTO(FavoriteFolderPO po) {
        if (po == null) {
            return null;
        }
        return FavoriteFolderDTO.builder()
                .id(po.getId())
                .userId(po.getUserId())
                .name(po.getName())
                .description(po.getDescription())
                .visibility(po.getVisibility())
                .sortOrder(po.getSortOrder())
                .defaultFolder(Objects.equals(po.getIsDefault(), 1))
                .privateFolder(!Objects.equals(po.getVisibility(), FAVORITE_FOLDER_PUBLIC))
                .postCount(po.getPostCount() == null ? 0L : po.getPostCount())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private void updateCommentLikeCount(Long commentId, int delta) {
        if (commentMapper.incrLikeCount(commentId, delta) <= 0) {
            throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private void updateCommentHelpfulCount(Long commentId, int delta) {
        if (commentMapper.incrHelpfulCount(commentId, delta) <= 0) {
            throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private CommentPO requireNormalComment(Long commentId) {
        CommentPO comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getCommentStatus() == null || comment.getCommentStatus() != COMMENT_STATUS_NORMAL) {
            throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
        }
        return comment;
    }

    private static void requireCommentInPost(CommentPO comment, Long postId) {
        if (!Objects.equals(comment.getPostId(), postId)) {
            throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private void requireAuthorOrModerator(Long uid, PostDTO post) {
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (Objects.equals(uid, post.getAuthorId()) || canModerate(uid)) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN);
    }

    private void requireModerator(Long uid) {
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!canModerate(uid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean canModerate(Long uid) {
        return adminPermissionService.isAdmin(uid)
                || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR)
                || adminPermissionService.isLocalOpenMode();
    }

    private static String operatorRole(Long operatorUid, PostDTO post) {
        return Objects.equals(operatorUid, post.getAuthorId()) ? OPERATOR_ROLE_AUTHOR : OPERATOR_ROLE_ADMIN;
    }

    private static String signalSource(Long operatorUid, PostDTO post) {
        return Objects.equals(operatorUid, post.getAuthorId()) ? SOURCE_AUTHOR : SOURCE_MODERATION;
    }

    private void setQualitySignal(CommentPO comment, Long operatorUid, String signalType, boolean active,
                                  String reason, String operatorRole, String source) {
        CommentQualitySignalPO existing = commentQualitySignalMapper.selectByCommentAndType(comment.getId(), signalType);
        if (existing != null) {
            commentQualitySignalMapper.updateSignalStatus(existing.getId(), active ? 1 : 0, operatorUid, operatorRole, reason, source);
        } else if (active) {
            CommentQualitySignalPO po = new CommentQualitySignalPO();
            po.setId(idGen.nextId());
            po.setPostId(comment.getPostId());
            po.setCommentId(comment.getId());
            po.setRootId(commentRootId(comment));
            po.setSignalType(signalType);
            po.setSignalStatus(1);
            po.setOperatorUid(operatorUid);
            po.setOperatorRole(operatorRole);
            po.setReason(reason);
            po.setSource(source);
            po.setIsDeleted(0);
            commentQualitySignalMapper.insert(po);
        } else {
            return;
        }
        publishQualitySignalEvent(comment, operatorUid, signalType, active, reason);
    }

    private void publishQualitySignalEvent(CommentPO comment, Long operatorUid, String signalType, boolean active, String reason) {
        events.publish(CommentQualitySignalChangedEvent.builder()
                .postId(comment.getPostId())
                .postAuthorUid(comment.getPostAuthorId())
                .commentId(comment.getId())
                .operatorUid(operatorUid)
                .commentAuthorUid(comment.getAuthorId())
                .signalType(signalType)
                .active(active)
                .reason(reason)
                .timestamp(Instant.now().toEpochMilli())
                .build());
    }

    private static Long commentRootId(CommentPO comment) {
        return comment.getRootId() == null || comment.getRootId() == 0 ? comment.getId() : comment.getRootId();
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return DEFAULT_FOLD_REASON;
        }
        String trimmed = reason.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    private PostDTO requirePostVisible(Long postId, Long viewerUid) {
        PostDTO post = postFacade.getPost(postId, viewerUid);
        if (post == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private PageResult<PostBriefDTO> postPage(List<Long> postIds, List<?> sourceRows, int size, Long viewerUid) {
        if (postIds.isEmpty()) return PageResult.empty();
        boolean hasMore = sourceRows.size() > size;
        List<Long> pagePostIds = hasMore ? postIds.subList(0, size) : postIds;
        Map<Long, PostBriefDTO> posts = postFacade.batchGetPosts(pagePostIds, viewerUid);
        List<PostBriefDTO> items = pagePostIds.stream()
                .map(posts::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        String next = hasMore ? extractCreateTimeCursor(sourceRows.get(size - 1)) : null;
        return PageResult.of(items, next, hasMore);
    }

    private PageResult<PostBriefDTO> publicPostPage(List<Long> postIds, List<?> sourceRows, int size) {
        if (postIds.isEmpty()) return PageResult.empty();
        boolean hasMore = sourceRows.size() > size;
        List<Long> pagePostIds = hasMore ? postIds.subList(0, size) : postIds;
        Map<Long, PostBriefDTO> posts = postFacade.batchGetPosts(pagePostIds, null, false);
        List<PostBriefDTO> items = pagePostIds.stream()
                .map(posts::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        String next = hasMore ? extractCreateTimeCursor(sourceRows.get(size - 1)) : null;
        return PageResult.of(items, next, hasMore);
    }

    private static int clampPageSize(int size) {
        return Math.max(1, Math.min(size, 50));
    }

    private static String extractCreateTimeCursor(Object row) {
        if (row instanceof LikePO po && po.getCreateTime() != null) {
            return timeIdCursor(po.getCreateTime(), po.getId());
        }
        if (row instanceof FavoritePO po && po.getCreateTime() != null) {
            return timeIdCursor(po.getCreateTime(), po.getId());
        }
        return null;
    }

    private static String commentCursor(CommentPO po) {
        return po == null ? null : timeIdCursor(po.getCreateTime(), po.getId());
    }

    private static String commentCursor(CommentDTO dto) {
        return dto == null ? null : timeIdCursor(dto.getCreateTime(), dto.getId());
    }

    private static String timeIdCursor(LocalDateTime createTime, Long id) {
        if (createTime == null) {
            return null;
        }
        long millis = createTime.toInstant(ZoneOffset.UTC).toEpochMilli();
        return id == null || id <= 0 ? String.valueOf(millis) : millis + ":" + id;
    }

    private static Cursor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank() || "0".equals(cursor.trim())) {
            return Cursor.empty();
        }
        String trimmed = cursor.trim();
        String[] parts = trimmed.split(":", 2);
        try {
            long millis = Long.parseLong(parts[0]);
            if (millis <= 0) {
                return Cursor.empty();
            }
            Long id = null;
            if (parts.length > 1 && !parts[1].isBlank()) {
                long parsedId = Long.parseLong(parts[1]);
                if (parsedId > 0) {
                    id = parsedId;
                }
            }
            return new Cursor(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC), id);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private record Cursor(LocalDateTime time, Long id) {
        private static Cursor empty() {
            return new Cursor(null, null);
        }
    }

    private Map<Long, UserBriefDTO> usersFor(List<CommentPO> comments) {
        Set<Long> uids = new HashSet<>();
        for (CommentPO comment : comments) {
            if (comment.getAuthorId() != null) {
                uids.add(comment.getAuthorId());
            }
            if (comment.getReplyToUid() != null && comment.getReplyToUid() > 0) {
                uids.add(comment.getReplyToUid());
            }
        }
        return userFacade.batchGetUserBriefs(uids);
    }

    private Set<Long> likedCommentIds(Long viewerUid, List<CommentPO> comments) {
        if (viewerUid == null || comments == null || comments.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = comments.stream().map(CommentPO::getId).toList();
        return likeMapper.selectList(new LambdaQueryWrapper<LikePO>()
                        .eq(LikePO::getUserId, viewerUid)
                        .eq(LikePO::getTargetType, TARGET_COMMENT)
                        .in(LikePO::getTargetId, ids)
                        .eq(LikePO::getIsDeleted, 0))
                .stream()
                .map(LikePO::getTargetId)
                .collect(Collectors.toSet());
    }

    private Set<Long> helpfulCommentIds(Long viewerUid, List<CommentPO> comments) {
        if (viewerUid == null || comments == null || comments.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = comments.stream().map(CommentPO::getId).toList();
        return new HashSet<>(commentHelpfulMapper.selectActiveCommentIdsByUid(viewerUid, ids));
    }

    private Map<Long, List<CommentQualitySignalPO>> activeSignalsByComment(List<CommentPO> comments) {
        if (comments == null || comments.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = comments.stream().map(CommentPO::getId).toList();
        return commentQualitySignalMapper.selectActiveByCommentIds(ids).stream()
                .collect(Collectors.groupingBy(CommentQualitySignalPO::getCommentId));
    }

    private static CommentDTO toDto(CommentPO po, Long viewerUid, Map<Long, UserBriefDTO> users,
                                    Set<Long> likedCommentIds, Set<Long> helpfulCommentIds,
                                    Map<Long, List<CommentQualitySignalPO>> signalsByComment,
                                    Map<Long, Long> replyCountByRoot) {
        List<CommentQualitySignalPO> signals = signalsByComment.getOrDefault(po.getId(), List.of());
        boolean authorReply = Objects.equals(po.getAuthorId(), po.getPostAuthorId());
        boolean authorPinned = hasSignal(signals, SIGNAL_AUTHOR_PINNED);
        boolean featured = hasSignal(signals, SIGNAL_FEATURED);
        CommentQualitySignalPO foldSignal = findSignal(signals, SIGNAL_LOW_QUALITY_FOLDED);
        boolean folded = foldSignal != null;
        int likeCount = po.getLikeCount() == null ? 0 : po.getLikeCount();
        int helpfulCount = po.getHelpfulCount() == null ? 0 : po.getHelpfulCount();
        long replyCount = replyCountByRoot.getOrDefault(po.getId(), 0L);
        double hotScore = hotScore(authorPinned, featured, authorReply, helpfulCount, likeCount, replyCount, folded);
        return CommentDTO.builder()
                .id(po.getId())
                .postId(po.getPostId())
                .authorId(po.getAuthorId())
                .author(users.get(po.getAuthorId()))
                .rootId(po.getRootId())
                .parentId(po.getParentId())
                .replyToUid(po.getReplyToUid())
                .replyToUser(po.getReplyToUid() == null ? null : users.get(po.getReplyToUid()))
                .content(po.getContent())
                .likeCount(likeCount)
                .myLiked(likedCommentIds.contains(po.getId()))
                .authorReply(authorReply)
                .authorPinned(authorPinned)
                .featured(featured)
                .helpfulCount(helpfulCount)
                .myHelpful(helpfulCommentIds.contains(po.getId()))
                .hotScore(hotScore)
                .folded(folded)
                .foldReason(foldSignal == null ? null : foldSignal.getReason())
                .qualityBadges(qualityBadges(authorReply, authorPinned, featured, helpfulCount, folded))
                .canDelete(viewerUid != null && (Objects.equals(viewerUid, po.getAuthorId()) || Objects.equals(viewerUid, po.getPostAuthorId())))
                .replyCount(Math.toIntExact(Math.min(replyCount, Integer.MAX_VALUE)))
                .hasMoreReplies(replyCount > 0)
                .replies(List.of())
                .createTime(po.getCreateTime())
                .build();
    }

    private static Long mapLong(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase());
        }
        if (value == null && "rootId".equals(key)) {
            value = row.get("root_id");
        }
        if (value == null && "replyCount".equals(key)) {
            value = row.get("reply_count");
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static boolean hasSignal(List<CommentQualitySignalPO> signals, String signalType) {
        return findSignal(signals, signalType) != null;
    }

    private static CommentQualitySignalPO findSignal(List<CommentQualitySignalPO> signals, String signalType) {
        return signals.stream()
                .filter(signal -> signalType.equals(signal.getSignalType()))
                .findFirst()
                .orElse(null);
    }

    private static double hotScore(boolean authorPinned, boolean featured, boolean authorReply,
                                   int helpfulCount, int likeCount, long replyCount, boolean folded) {
        double score = 0;
        if (authorPinned) score += 1000;
        if (featured) score += 400;
        if (authorReply) score += 120;
        score += helpfulCount * 10.0;
        score += likeCount * 2.0;
        score += replyCount * 3.0;
        if (folded) score -= 500;
        return score;
    }

    private static List<String> qualityBadges(boolean authorReply, boolean authorPinned, boolean featured,
                                              int helpfulCount, boolean folded) {
        List<String> badges = new java.util.ArrayList<>();
        if (authorPinned) badges.add("AUTHOR_PINNED");
        if (featured) badges.add("FEATURED");
        if (authorReply) badges.add("AUTHOR_REPLY");
        if (helpfulCount > 0) badges.add("HELPFUL");
        if (folded) badges.add("FOLDED");
        return badges;
    }
}
