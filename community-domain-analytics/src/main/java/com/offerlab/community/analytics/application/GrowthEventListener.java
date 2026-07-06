package com.offerlab.community.analytics.application;

import com.offerlab.community.interaction.api.event.CommentCreatedEvent;
import com.offerlab.community.interaction.api.event.PostFavoritedEvent;
import com.offerlab.community.interaction.api.event.PostLikedEvent;
import com.offerlab.community.post.api.event.OperationCurationSelectedEvent;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.post.api.event.PublicPostViewedEvent;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.user.api.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GrowthEventListener {

    private final GrowthEventService growthEventService;
    private final PostMapper postMapper;

    @EventListener
    public void onPublicPostViewed(PublicPostViewedEvent event) {
        if (event == null || event.getPostId() == null) {
            return;
        }
        growthEventService.recordTrustedEvent(
                GrowthEventService.PUBLIC_POST_VIEW,
                event.getViewerUid(),
                event.getDomain(),
                event.getPostId(),
                "POST",
                String.valueOf(event.getPostId()),
                "post.detail");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {
        if (event == null || event.getUid() == null) {
            return;
        }
        growthEventService.recordTrustedEvent(
                GrowthEventService.USER_REGISTER,
                event.getUid(),
                null,
                null,
                "USER",
                String.valueOf(event.getUid()),
                "user.register");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostLiked(PostLikedEvent event) {
        if (event == null || event.getUid() == null || event.getPostId() == null) {
            return;
        }
        growthEventService.recordTrustedEvent(
                GrowthEventService.POST_LIKE,
                event.getUid(),
                event.getDomain(),
                event.getPostId(),
                "POST",
                String.valueOf(event.getPostId()),
                "interaction.like");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostFavorited(PostFavoritedEvent event) {
        if (event == null || event.getUid() == null || event.getPostId() == null) {
            return;
        }
        growthEventService.recordTrustedEvent(
                GrowthEventService.POST_FAVORITE,
                event.getUid(),
                event.getDomain(),
                event.getPostId(),
                "POST",
                String.valueOf(event.getPostId()),
                "interaction.favorite");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(CommentCreatedEvent event) {
        if (event == null || event.getUid() == null || event.getCommentId() == null) {
            return;
        }
        growthEventService.recordTrustedEvent(
                GrowthEventService.POST_COMMENT,
                event.getUid(),
                event.getDomain(),
                event.getPostId(),
                "COMMENT",
                String.valueOf(event.getCommentId()),
                "interaction.comment");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostPublished(PostPublishedEvent event) {
        if (event == null || event.getAuthorId() == null || event.getPostId() == null) {
            return;
        }
        Map<String, Object> contribution = postMapper.aggregatePublicContributionByAuthor(event.getAuthorId());
        if (asLong(contribution == null ? null : contribution.get("postCount")) != 1L) {
            return;
        }
        growthEventService.recordTrustedEvent(
                GrowthEventService.FIRST_POST_PUBLISHED,
                event.getAuthorId(),
                event.getDomain(),
                event.getPostId(),
                "POST",
                String.valueOf(event.getPostId()),
                "post.publish");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOperationCurationSelected(OperationCurationSelectedEvent event) {
        if (event == null || event.getAuthorUid() == null || event.getContentId() == null) {
            return;
        }
        growthEventService.recordTrustedEvent(
                GrowthEventService.OPERATION_CURATION_SELECTED,
                event.getAuthorUid(),
                null,
                event.getContentId(),
                "POST",
                operationTargetValue(event),
                "operation.curation");
    }

    private static String operationTargetValue(OperationCurationSelectedEvent event) {
        return String.join(":",
                String.valueOf(event.getPlacementType()),
                String.valueOf(event.getPlacementId()),
                String.valueOf(event.getPlacementKey()),
                String.valueOf(event.getSectionKey()));
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
