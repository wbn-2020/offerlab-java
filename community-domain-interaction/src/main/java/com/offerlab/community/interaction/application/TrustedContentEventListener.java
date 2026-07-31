package com.offerlab.community.interaction.application;

import com.offerlab.community.interaction.api.event.CommentCreatedEvent;
import com.offerlab.community.interaction.api.event.CommentQualitySignalChangedEvent;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.CommentMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentPO;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.event.PostUpdatedEvent;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class TrustedContentEventListener {

    private static final String FOLDED_SIGNAL = "LOW_QUALITY_FOLDED";

    private final TrustedContentService trustedContentService;
    private final CommentMapper commentMapper;
    private final PostRepository postRepository;

    @TransactionalEventListener(
            phase = TransactionPhase.BEFORE_COMMIT,
            fallbackExecution = true)
    public void onPostUpdated(PostUpdatedEvent event) {
        if (event == null) {
            return;
        }
        trustedContentService.linkSuggestionsFromPostUpdate(
                event.getPostId(),
                event.getAuthorId(),
                event.getRespondedSuggestionIds(),
                event.getResultVersion());
    }

    @TransactionalEventListener(
            phase = TransactionPhase.BEFORE_COMMIT,
            fallbackExecution = true)
    public void onCommentCreated(CommentCreatedEvent event) {
        if (event == null || !isPositive(event.getCommentId())) {
            return;
        }
        CommentPO comment = commentMapper.selectById(event.getCommentId());
        PostDTO post = postSnapshot(comment == null ? null : comment.getPostId());
        if (comment == null || post == null) {
            return;
        }
        trustedContentService.onValidRootCommentCreated(post, comment);
    }

    @TransactionalEventListener(
            phase = TransactionPhase.BEFORE_COMMIT,
            fallbackExecution = true)
    public void onCommentQualitySignalChanged(CommentQualitySignalChangedEvent event) {
        if (event == null
                || !FOLDED_SIGNAL.equals(event.getSignalType())
                || event.getActive() == null
                || !isPositive(event.getCommentId())) {
            return;
        }
        CommentPO comment = commentMapper.selectById(event.getCommentId());
        if (!isRootComment(comment)) {
            return;
        }
        PostDTO post = postSnapshot(comment.getPostId());
        if (post == null) {
            return;
        }
        if (Boolean.TRUE.equals(event.getActive())) {
            trustedContentService.onRootCommentUnavailable(
                    post, comment.getId(), event.getOperatorUid());
        } else {
            trustedContentService.onRootCommentAvailable(
                    post, comment.getId(), event.getOperatorUid());
        }
    }

    private PostDTO postSnapshot(Long postId) {
        if (!isPositive(postId)) {
            return null;
        }
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) {
            return null;
        }
        return PostDTO.builder()
                .id(post.getId())
                .authorId(post.getAuthorId())
                .postType(post.getPostType())
                .visibility(post.getVisibility())
                .postStatus(post.getPostStatus())
                .build();
    }

    private static boolean isRootComment(CommentPO comment) {
        return comment != null
                && !Objects.equals(comment.getIsDeleted(), 1)
                && (comment.getRootId() == null || comment.getRootId() == 0)
                && (comment.getParentId() == null || comment.getParentId() == 0);
    }

    private static boolean isPositive(Long value) {
        return value != null && value > 0;
    }
}
