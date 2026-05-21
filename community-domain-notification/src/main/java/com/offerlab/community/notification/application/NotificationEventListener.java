package com.offerlab.community.notification.application;

import com.offerlab.community.interaction.api.event.CommentCreatedEvent;
import com.offerlab.community.interaction.api.event.CommentLikedEvent;
import com.offerlab.community.interaction.api.event.PostFavoritedEvent;
import com.offerlab.community.interaction.api.event.PostLikedEvent;
import com.offerlab.community.notification.api.NotificationFacade;
import com.offerlab.community.user.api.event.UserFollowedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final int TARGET_POST = 1;
    private static final int TARGET_COMMENT = 2;

    private final NotificationFacade notificationFacade;

    @Async
    @EventListener
    public void onPostLiked(PostLikedEvent event) {
        runQuietly(() -> notificationFacade.notifyLike(
                event.getPostAuthorId(), event.getUid(), TARGET_POST, event.getPostId()), "post like");
    }

    @Async
    @EventListener
    public void onCommentLiked(CommentLikedEvent event) {
        runQuietly(() -> notificationFacade.notifyLike(
                event.getCommentAuthorId(), event.getUid(), TARGET_COMMENT, event.getCommentId()), "comment like");
    }

    @Async
    @EventListener
    public void onCommentCreated(CommentCreatedEvent event) {
        runQuietly(() -> notificationFacade.notifyComment(
                event.getPostAuthorId(), event.getUid(), event.getPostId(), event.getCommentId()), "post comment");
        Long replyToUid = event.getReplyToUid();
        if (replyToUid != null && !replyToUid.equals(event.getPostAuthorId())) {
            runQuietly(() -> notificationFacade.notifyComment(
                    replyToUid, event.getUid(), event.getPostId(), event.getCommentId()), "reply comment");
        }
    }

    @Async
    @EventListener
    public void onUserFollowed(UserFollowedEvent event) {
        runQuietly(() -> notificationFacade.notifyFollower(
                event.getFolloweeId(), event.getFollowerId()), "user follow");
    }

    @Async
    @EventListener
    public void onPostFavorited(PostFavoritedEvent event) {
        runQuietly(() -> notificationFacade.notifyFavorite(
                event.getPostAuthorId(), event.getUid(), event.getPostId()), "post favorite");
    }

    private void runQuietly(Runnable runnable, String scene) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.warn("create notification failed, scene={}: {}", scene, e.getMessage());
        }
    }
}
