package com.offerlab.community.interaction.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.mq.producer.EventPublisher;
import com.offerlab.community.infra.redis.cache.PostCounterRedis;
import com.offerlab.community.interaction.api.InteractionFacade;
import com.offerlab.community.interaction.api.dto.CommentCreateCmd;
import com.offerlab.community.interaction.api.dto.CommentDTO;
import com.offerlab.community.interaction.api.event.PostLikedEvent;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.CommentMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.FavoriteMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.LikeMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentPO;
import com.offerlab.community.interaction.infrastructure.persistence.po.FavoritePO;
import com.offerlab.community.interaction.infrastructure.persistence.po.LikePO;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionFacadeImpl implements InteractionFacade {

    private static final int TARGET_POST = 1;
    private static final int TARGET_COMMENT = 2;

    private final LikeMapper likeMapper;
    private final FavoriteMapper favoriteMapper;
    private final CommentMapper commentMapper;
    private final PostCounterMapper postCounterMapper;
    private final PostCounterRedis postCounterRedis;
    private final PostFacade postFacade;
    private final SnowflakeIdGenerator idGen;
    private final EventPublisher events;

    @Override
    @Transactional
    public void like(Long uid, Long postId) {
        PostDTO post = postFacade.getPost(postId);
        if (post == null) throw new BizException(ErrorCode.POST_NOT_FOUND);

        LikePO existing = likeMapper.selectOne(new LambdaQueryWrapper<LikePO>()
                .eq(LikePO::getUserId, uid)
                .eq(LikePO::getTargetType, TARGET_POST)
                .eq(LikePO::getTargetId, postId)
                .last("LIMIT 1"));
        if (existing != null) {
            if (existing.getIsDeleted() == 0) {
                throw new BizException(ErrorCode.LIKE_ALREADY_EXISTS);
            }
            existing.setIsDeleted(0);
            likeMapper.updateById(existing);
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
        // 双写：Redis 写主 + MySQL 兜底
        postCounterRedis.incrLike(postId, 1);
        postCounterMapper.incrLike(postId, 1);
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
        po.setIsDeleted(1);
        likeMapper.updateById(po);
        // 双写：Redis 写主 + MySQL 兜底
        postCounterRedis.incrLike(postId, -1);
        postCounterMapper.incrLike(postId, -1);
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
    @Transactional
    public void favorite(Long uid, Long postId) {
        PostDTO post = postFacade.getPost(postId);
        if (post == null) throw new BizException(ErrorCode.POST_NOT_FOUND);

        FavoritePO existing = favoriteMapper.selectOne(new LambdaQueryWrapper<FavoritePO>()
                .eq(FavoritePO::getUserId, uid)
                .eq(FavoritePO::getPostId, postId)
                .last("LIMIT 1"));
        if (existing != null) {
            if (existing.getIsDeleted() == 0) {
                throw new BizException(ErrorCode.FAVORITE_ALREADY_EXISTS);
            }
            existing.setIsDeleted(0);
            favoriteMapper.updateById(existing);
        } else {
            FavoritePO po = new FavoritePO();
            po.setId(idGen.nextId());
            po.setUserId(uid);
            po.setPostId(postId);
            po.setFolderId(0L);
            po.setIsDeleted(0);
            favoriteMapper.insert(po);
        }
        // 双写：Redis 写主 + MySQL 兜底
        postCounterRedis.incrFavorite(postId, 1);
        postCounterMapper.incrFavorite(postId, 1);
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
        po.setIsDeleted(1);
        favoriteMapper.updateById(po);
        // 双写：Redis 写主 + MySQL 兜底
        postCounterRedis.incrFavorite(postId, -1);
        postCounterMapper.incrFavorite(postId, -1);
    }

    @Override
    @Transactional
    public Long addComment(CommentCreateCmd cmd) {
        PostDTO post = postFacade.getPost(cmd.getPostId());
        if (post == null) throw new BizException(ErrorCode.POST_NOT_FOUND);

        long id = idGen.nextId();
        CommentPO po = new CommentPO();
        po.setId(id);
        po.setPostId(cmd.getPostId());
        po.setPostAuthorId(post.getAuthorId());
        po.setAuthorId(cmd.getAuthorUid());
        po.setContent(cmd.getContent());
        po.setLikeCount(0);
        po.setCommentStatus(1);

        if (cmd.getParentId() != null && cmd.getParentId() > 0) {
            CommentPO parent = commentMapper.selectById(cmd.getParentId());
            if (parent == null) throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
            po.setParentId(cmd.getParentId());
            po.setRootId(parent.getRootId() == 0 ? parent.getId() : parent.getRootId());
            po.setReplyToUid(cmd.getReplyToUid() != null ? cmd.getReplyToUid() : parent.getAuthorId());
        } else {
            po.setParentId(0L);
            po.setRootId(0L);
        }

        commentMapper.insert(po);
        // 双写：Redis 写主 + MySQL 兜底
        postCounterRedis.incrComment(cmd.getPostId(), 1);
        postCounterMapper.incrComment(cmd.getPostId(), 1);
        return id;
    }

    @Override
    public PageResult<CommentDTO> listComments(Long postId, long cursor, int size) {
        LambdaQueryWrapper<CommentPO> q = new LambdaQueryWrapper<CommentPO>()
                .eq(CommentPO::getPostId, postId)
                .eq(CommentPO::getRootId, 0L)             // 仅一级
                .eq(CommentPO::getCommentStatus, 1)
                .orderByDesc(CommentPO::getCreateTime)
                .last("LIMIT " + size);
        if (cursor > 0) {
            q.lt(CommentPO::getCreateTime, java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(cursor), ZoneOffset.UTC));
        }
        List<CommentPO> list = commentMapper.selectList(q);
        if (list.isEmpty()) return PageResult.empty();
        List<CommentDTO> items = list.stream().map(InteractionFacadeImpl::toDto).toList();
        boolean hasMore = list.size() == size;
        String next = hasMore ? String.valueOf(list.get(list.size() - 1).getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli()) : null;
        return PageResult.of(items, next, hasMore);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long operatorUid) {
        CommentPO po = commentMapper.selectById(commentId);
        if (po == null) throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
        if (!po.getAuthorId().equals(operatorUid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        commentMapper.deleteById(commentId);
        // 双写：Redis 写主 + MySQL 兜底
        postCounterRedis.incrComment(po.getPostId(), -1);
        postCounterMapper.incrComment(po.getPostId(), -1);
    }

    private static CommentDTO toDto(CommentPO po) {
        return CommentDTO.builder()
                .id(po.getId())
                .postId(po.getPostId())
                .authorId(po.getAuthorId())
                .rootId(po.getRootId())
                .parentId(po.getParentId())
                .replyToUid(po.getReplyToUid())
                .content(po.getContent())
                .likeCount(po.getLikeCount())
                .createTime(po.getCreateTime())
                .build();
    }
}
