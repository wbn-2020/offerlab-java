package com.offerlab.community.notification.application;

import com.offerlab.community.interaction.api.event.CommentCreatedEvent;
import com.offerlab.community.interaction.api.event.CommentLikedEvent;
import com.offerlab.community.interaction.api.event.PostFavoritedEvent;
import com.offerlab.community.interaction.api.event.PostLikedEvent;
import com.offerlab.community.notification.api.NotificationFacade;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.user.api.event.UserFollowedEvent;
import com.offerlab.community.user.api.UserFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final int TARGET_POST = 1;
    private static final int TARGET_COMMENT = 2;
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\p{L}\\p{N}_\\-\\u4e00-\\u9fa5]{2,32})");

    private final NotificationFacade notificationFacade;
    private final UserFacade userFacade;

    @Async
    @EventListener
    public void onPostPublished(PostPublishedEvent event) {
        notifyMentions(event.getAuthorId(), event.getPostId(), null,
                textOf(event.getTitle(), event.getContent()), Set.of(event.getAuthorId()));
    }

    @Async
    @EventListener
    public void onPostLiked(PostLikedEvent event) {
        runQuietly(() -> notificationFacade.notifyLike(
                event.getPostAuthorId(), event.getUid(), TARGET_POST, event.getPostId()), "post like");
    }

    @Async
    @EventListener
    public void onCommentLiked(CommentLikedEvent event) {
        runQuietly(() -> notificationFacade.notifyCommentLike(
                event.getCommentAuthorId(), event.getUid(), event.getPostId(), event.getCommentId()), "comment like");
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
        Set<Long> excluded = new HashSet<>();
        excluded.add(event.getUid());
        excluded.add(event.getPostAuthorId());
        if (replyToUid != null) {
            excluded.add(replyToUid);
        }
        notifyMentions(event.getUid(), event.getPostId(), event.getCommentId(), event.getContent(), excluded);
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

    private void notifyMentions(Long senderUid, Long postId, Long commentId, String text, Set<Long> excludedUids) {
        Set<String> names = extractMentionNames(text);
        if (names.isEmpty()) {
            return;
        }
        runQuietly(() -> {
            Map<String, Long> matched = userFacade.findUserIdsByNicknames(names);
            for (Long receiverUid : matched.values()) {
                if (receiverUid != null && (excludedUids == null || !excludedUids.contains(receiverUid))) {
                    notificationFacade.notifyMention(receiverUid, senderUid, postId, commentId);
                }
            }
        }, "user mention");
    }

    private Set<String> extractMentionNames(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(text);
        while (matcher.find() && result.size() < 20) {
            result.add(matcher.group(1).trim());
        }
        return result;
    }

    private String textOf(String title, String content) {
        return (title == null ? "" : title) + "\n" + (content == null ? "" : content);
    }
}
