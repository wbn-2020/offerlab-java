package com.offerlab.community.interaction.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.mq.producer.EventPublisher;
import com.offerlab.community.infra.redis.cache.PostCounterRedis;
import com.offerlab.community.infra.tx.AfterCommitExecutor;
import com.offerlab.community.interaction.api.InteractionFacade;
import com.offerlab.community.interaction.api.dto.CommentCreateCmd;
import com.offerlab.community.interaction.api.dto.CommentDTO;
import com.offerlab.community.interaction.api.event.CommentCreatedEvent;
import com.offerlab.community.interaction.api.event.CommentLikedEvent;
import com.offerlab.community.interaction.api.event.PostFavoritedEvent;
import com.offerlab.community.interaction.api.event.PostLikedEvent;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.CommentMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.FavoriteMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.LikeMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentPO;
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

    private final LikeMapper likeMapper;
    private final FavoriteMapper favoriteMapper;
    private final CommentMapper commentMapper;
    private final PostCounterMapper postCounterMapper;
    private final PostCounterRedis postCounterRedis;
    private final PostFacade postFacade;
    private final UserFacade userFacade;
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
                .uid(uid).postId(postId).postAuthorId(post.getAuthorId())
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
                .last("LIMIT 1"));
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
    @Transactional
    public void likeComment(Long uid, Long commentId) {
        CommentPO comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getCommentStatus() == null || comment.getCommentStatus() != COMMENT_STATUS_NORMAL) {
            throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
        }
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
        LikePO po = likeMapper.selectOne(new LambdaQueryWrapper<LikePO>()
                .eq(LikePO::getUserId, uid)
                .eq(LikePO::getTargetType, TARGET_COMMENT)
                .eq(LikePO::getTargetId, commentId)
                .eq(LikePO::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (po == null) throw new BizException(ErrorCode.LIKE_NOT_EXISTS);
        if (likeMapper.softDeleteById(po.getId()) <= 0) {
            throw new BizException(ErrorCode.LIKE_NOT_EXISTS);
        }
        updateCommentLikeCount(commentId, -1);
    }

    @Override
    @Transactional
    public void favorite(Long uid, Long postId) {
        PostDTO post = postFacade.getPost(postId, uid);
        if (post == null) throw new BizException(ErrorCode.POST_NOT_FOUND);

        try {
            FavoritePO existing = favoriteMapper.selectAnyByUserPost(uid, postId);
            if (existing != null) {
                if (existing.getIsDeleted() == 0) {
                    throw new BizException(ErrorCode.FAVORITE_ALREADY_EXISTS);
                }
                if (favoriteMapper.restoreById(existing.getId()) <= 0) {
                    throw new BizException(ErrorCode.FAVORITE_ALREADY_EXISTS);
                }
            } else {
                FavoritePO po = new FavoritePO();
                po.setId(idGen.nextId());
                po.setUserId(uid);
                po.setPostId(postId);
                po.setFolderId(0L);
                po.setIsDeleted(0);
                favoriteMapper.insert(po);
            }
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }
        // MySQL 计数为权威，Redis 提交后增量刷新
        postCounterMapper.incrFavorite(postId, 1);
        afterCommit.execute(() -> postCounterRedis.incrFavorite(postId, 1), "post favorite counter:" + postId);
        events.publish(PostFavoritedEvent.builder()
                .uid(uid).postId(postId).postAuthorId(post.getAuthorId())
                .timestamp(Instant.now().toEpochMilli()).build());
    }

    @Override
    @Transactional
    public void unfavorite(Long uid, Long postId) {
        FavoritePO po = favoriteMapper.selectOne(new LambdaQueryWrapper<FavoritePO>()
                .eq(FavoritePO::getUserId, uid)
                .eq(FavoritePO::getPostId, postId)
                .eq(FavoritePO::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (po == null) throw new BizException(ErrorCode.FAVORITE_NOT_EXISTS);
        if (favoriteMapper.softDeleteById(po.getId()) <= 0) {
            throw new BizException(ErrorCode.FAVORITE_NOT_EXISTS);
        }
        // MySQL 计数为权威，Redis 提交后增量刷新
        postCounterMapper.incrFavorite(postId, -1);
        afterCommit.execute(() -> postCounterRedis.incrFavorite(postId, -1), "post unfavorite counter:" + postId);
    }

    @Override
    @Transactional
    public Long addComment(CommentCreateCmd cmd) {
        PostDTO post = postFacade.getPost(cmd.getPostId(), cmd.getAuthorUid());
        if (post == null) throw new BizException(ErrorCode.POST_NOT_FOUND);

        long id = idGen.nextId();
        CommentPO po = new CommentPO();
        po.setId(id);
        po.setPostId(cmd.getPostId());
        po.setPostAuthorId(post.getAuthorId());
        po.setAuthorId(cmd.getAuthorUid());
        po.setContent(cmd.getContent());
        po.setLikeCount(0);
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
            po.setReplyToUid(cmd.getReplyToUid() != null ? cmd.getReplyToUid() : parent.getAuthorId());
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
                    .timestamp(Instant.now().toEpochMilli())
                    .build());
        }
        return id;
    }

    @Override
    public PageResult<CommentDTO> listComments(Long postId, Long viewerUid, long cursor, int size) {
        int limit = clampPageSize(size);
        LambdaQueryWrapper<CommentPO> q = new LambdaQueryWrapper<CommentPO>()
                .eq(CommentPO::getPostId, postId)
                .eq(CommentPO::getRootId, 0L)             // 仅一级
                .eq(CommentPO::getCommentStatus, COMMENT_STATUS_NORMAL)
                .orderByDesc(CommentPO::getCreateTime)
                .last("LIMIT " + (limit + 1));
        if (cursor > 0) {
            q.lt(CommentPO::getCreateTime, java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(cursor), ZoneOffset.UTC));
        }
        List<CommentPO> roots = commentMapper.selectList(q);
        if (roots.isEmpty()) return PageResult.empty();
        boolean hasMore = roots.size() > limit;
        if (hasMore) {
            roots = roots.subList(0, limit);
        }

        List<Long> rootIds = roots.stream().map(CommentPO::getId).toList();
        List<CommentPO> replies = commentMapper.selectList(new LambdaQueryWrapper<CommentPO>()
                .eq(CommentPO::getPostId, postId)
                .in(CommentPO::getRootId, rootIds)
                .eq(CommentPO::getCommentStatus, COMMENT_STATUS_NORMAL)
                .orderByAsc(CommentPO::getCreateTime));
        List<CommentPO> all = new java.util.ArrayList<>(roots.size() + replies.size());
        all.addAll(roots);
        all.addAll(replies);

        Map<Long, UserBriefDTO> users = usersFor(all);
        Set<Long> likedCommentIds = likedCommentIds(viewerUid, all);
        Map<Long, List<CommentDTO>> repliesByRoot = replies.stream()
                .sorted(Comparator.comparing(CommentPO::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(po -> toDto(po, viewerUid, users, likedCommentIds))
                .collect(Collectors.groupingBy(CommentDTO::getRootId));
        List<CommentDTO> items = roots.stream()
                .map(po -> {
                    CommentDTO dto = toDto(po, viewerUid, users, likedCommentIds);
                    dto.setReplies(repliesByRoot.getOrDefault(po.getId(), List.of()));
                    return dto;
                })
                .toList();
        String next = hasMore ? String.valueOf(roots.get(roots.size() - 1).getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli()) : null;
        return PageResult.of(items, next, hasMore);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long operatorUid) {
        CommentPO po = commentMapper.selectById(commentId);
        if (po == null) throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
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
    public PageResult<PostBriefDTO> listLikedPosts(Long uid, long cursor, int size) {
        int limit = clampPageSize(size);
        LambdaQueryWrapper<LikePO> q = new LambdaQueryWrapper<LikePO>()
                .eq(LikePO::getUserId, uid)
                .eq(LikePO::getTargetType, TARGET_POST)
                .eq(LikePO::getIsDeleted, 0)
                .orderByDesc(LikePO::getCreateTime)
                .last("LIMIT " + (limit + 1));
        if (cursor > 0) {
            q.lt(LikePO::getCreateTime, java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(cursor), ZoneOffset.UTC));
        }
        List<LikePO> list = likeMapper.selectList(q);
        return postPage(list.stream().map(LikePO::getTargetId).toList(), list, limit);
    }

    @Override
    public PageResult<PostBriefDTO> listFavoritePosts(Long uid, long cursor, int size) {
        int limit = clampPageSize(size);
        LambdaQueryWrapper<FavoritePO> q = new LambdaQueryWrapper<FavoritePO>()
                .eq(FavoritePO::getUserId, uid)
                .eq(FavoritePO::getIsDeleted, 0)
                .orderByDesc(FavoritePO::getCreateTime)
                .last("LIMIT " + (limit + 1));
        if (cursor > 0) {
            q.lt(FavoritePO::getCreateTime, java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(cursor), ZoneOffset.UTC));
        }
        List<FavoritePO> list = favoriteMapper.selectList(q);
        return postPage(list.stream().map(FavoritePO::getPostId).toList(), list, limit);
    }

    private void updateCommentLikeCount(Long commentId, int delta) {
        if (commentMapper.incrLikeCount(commentId, delta) <= 0) {
            throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private PageResult<PostBriefDTO> postPage(List<Long> postIds, List<?> sourceRows, int size) {
        if (postIds.isEmpty()) return PageResult.empty();
        boolean hasMore = sourceRows.size() > size;
        List<Long> pagePostIds = hasMore ? postIds.subList(0, size) : postIds;
        Map<Long, PostBriefDTO> posts = postFacade.batchGetPosts(pagePostIds);
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
            return String.valueOf(po.getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli());
        }
        if (row instanceof FavoritePO po && po.getCreateTime() != null) {
            return String.valueOf(po.getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli());
        }
        return null;
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

    private static CommentDTO toDto(CommentPO po, Long viewerUid, Map<Long, UserBriefDTO> users, Set<Long> likedCommentIds) {
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
                .likeCount(po.getLikeCount())
                .myLiked(likedCommentIds.contains(po.getId()))
                .canDelete(viewerUid != null && (Objects.equals(viewerUid, po.getAuthorId()) || Objects.equals(viewerUid, po.getPostAuthorId())))
                .replies(List.of())
                .createTime(po.getCreateTime())
                .build();
    }
}
