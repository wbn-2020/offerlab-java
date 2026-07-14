package com.offerlab.community.interaction.application;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.moderation.ModerationKeywordHit;
import com.offerlab.community.infra.mq.producer.EventPublisher;
import com.offerlab.community.infra.redis.cache.PostCounterRedis;
import com.offerlab.community.infra.review.ReviewQueueSourceActionHandler;
import com.offerlab.community.infra.tx.AfterCommitExecutor;
import com.offerlab.community.interaction.api.event.CommentCreatedEvent;
import com.offerlab.community.interaction.api.event.CommentUnavailableEvent;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.CommentMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentPO;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import com.offerlab.community.post.domain.repository.PostRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;

@Component
public class CommentModerationHitQueueActionHandler implements ReviewQueueSourceActionHandler {

    private static final String SOURCE_TYPE = "MODERATION_HIT";
    private static final int COMMENT_STATUS_NORMAL = 1;
    private static final int COMMENT_STATUS_REVIEWING = 2;
    private static final int COMMENT_STATUS_HIDDEN = 3;

    private final ContentModerationService moderationService;
    private final CommentMapper commentMapper;
    private final PostCounterMapper postCounterMapper;
    private final PostCounterRedis postCounterRedis;
    private final AfterCommitExecutor afterCommit;
    private final ApplicationEventPublisher events;
    private final PostRepository postRepo;
    private final DomainModeratorService domainModeratorService;
    private final EventPublisher eventPublisher;

    public CommentModerationHitQueueActionHandler(ContentModerationService moderationService,
                                                  CommentMapper commentMapper,
                                                  PostCounterMapper postCounterMapper,
                                                   PostCounterRedis postCounterRedis,
                                                   AfterCommitExecutor afterCommit,
                                                   ApplicationEventPublisher events,
                                                   PostRepository postRepo,
                                                   DomainModeratorService domainModeratorService,
                                                   EventPublisher eventPublisher) {
        this.moderationService = moderationService;
        this.commentMapper = commentMapper;
        this.postCounterMapper = postCounterMapper;
        this.postCounterRedis = postCounterRedis;
        this.afterCommit = afterCommit;
        this.events = events;
        this.postRepo = postRepo;
        this.domainModeratorService = domainModeratorService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public boolean supports(String sourceType) {
        return SOURCE_TYPE.equals(normalize(sourceType));
    }

    @Override
    public void handle(String sourceType, Long sourceId, String status, String result, String note, Long operatorUid) {
        if (!supports(sourceType) || sourceId == null || "CLOSED".equals(normalize(status))) {
            return;
        }
        ModerationKeywordHit hit = moderationService.findKeywordHit(sourceId);
        if (hit == null || !ContentModerationService.SCOPE_COMMENT.equalsIgnoreCase(hit.getScope())
                || !ContentModerationService.SOURCE_COMMENT.equalsIgnoreCase(hit.getSourceType())
                || hit.getSourceId() == null || hit.getSourceId() <= 0) {
            return;
        }
        CommentPO comment = commentMapper.selectById(hit.getSourceId());
        if (comment == null || (comment.getIsDeleted() != null && comment.getIsDeleted() != 0)) {
            throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
        }
        Post post = postRepo.findById(comment.getPostId())
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        domainModeratorService.requireModerateDomain(operatorUid, post.getDomain());
        boolean approved = "APPROVED".equals(normalize(status));
        int nextStatus = approved ? COMMENT_STATUS_NORMAL : COMMENT_STATUS_HIDDEN;
        int updated = commentMapper.update(null, new LambdaUpdateWrapper<CommentPO>()
                .eq(CommentPO::getId, comment.getId())
                .eq(CommentPO::getCommentStatus, COMMENT_STATUS_REVIEWING)
                .eq(CommentPO::getIsDeleted, 0)
                .set(CommentPO::getCommentStatus, nextStatus));
        if (updated <= 0) {
            return;
        }
        if (approved) {
            postCounterMapper.incrComment(comment.getPostId(), 1);
            afterCommit.execute(() -> postCounterRedis.incrComment(comment.getPostId(), 1),
                    "post comment moderation approve counter:" + comment.getPostId());
            events.publishEvent(CommentCreatedEvent.builder()
                    .uid(comment.getAuthorId())
                    .postId(comment.getPostId())
                    .postAuthorId(comment.getPostAuthorId())
                    .commentId(comment.getId())
                    .parentId(comment.getParentId())
                    .replyToUid(comment.getReplyToUid())
                    .content(comment.getContent())
                     .timestamp(Instant.now().toEpochMilli())
                     .build());
        } else {
            eventPublisher.publish(CommentUnavailableEvent.builder()
                    .commentId(comment.getId())
                    .postId(comment.getPostId())
                    .actorUid(operatorUid)
                    .reason("Comment was hidden by moderation")
                    .cascade(false)
                    .build());
        }
        moderationService.reviewKeywordHit(sourceId, approved ? "APPROVED" : "REJECTED", operatorUid, note);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
