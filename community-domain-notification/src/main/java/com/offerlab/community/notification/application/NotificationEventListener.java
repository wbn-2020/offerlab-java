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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
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
    private static final int TARGET_USER = 3;
    private static final int TYPE_LIKE = 1;
    private static final int TYPE_COMMENT = 2;
    private static final int TYPE_FAVORITE = 3;
    private static final int TYPE_FOLLOWER = 4;
    private static final int TYPE_SYSTEM = 5;
    private static final int TYPE_MENTION = 6;
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\p{L}\\p{N}_\\-\\u4e00-\\u9fa5]{2,32})");

    private final NotificationFacade notificationFacade;
    private final UserFacade userFacade;
    private final NotificationRetryService retryService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostPublished(PostPublishedEvent event) {
        notifyMentions(event.getAuthorId(), event.getPostId(), null,
                textOf(event.getTitle(), event.getContent()), Set.of(event.getAuthorId()));
        notifyTopicFollowers(event);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostLiked(PostLikedEvent event) {
        runQuietly(() -> notificationFacade.notifyLike(
                event.getPostAuthorId(), event.getUid(), TARGET_POST, event.getPostId()),
                "post like", event.getPostAuthorId(), event.getUid(), TYPE_LIKE, TARGET_POST, event.getPostId(),
                Map.of("action", "like", "targetType", TARGET_POST, "targetId", event.getPostId()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentLiked(CommentLikedEvent event) {
        runQuietly(() -> notificationFacade.notifyCommentLike(
                event.getCommentAuthorId(), event.getUid(), event.getPostId(), event.getCommentId()),
                "comment like", event.getCommentAuthorId(), event.getUid(), TYPE_LIKE, TARGET_COMMENT, event.getCommentId(),
                Map.of("action", "like", "targetType", TARGET_COMMENT, "targetId", event.getCommentId(),
                        "postId", event.getPostId(), "commentId", event.getCommentId()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(CommentCreatedEvent event) {
        runQuietly(() -> notificationFacade.notifyComment(
                event.getPostAuthorId(), event.getUid(), event.getPostId(), event.getCommentId()),
                "post comment", event.getPostAuthorId(), event.getUid(), TYPE_COMMENT, TARGET_COMMENT, event.getCommentId(),
                Map.of("action", "comment", "postId", event.getPostId(), "commentId", event.getCommentId()));
        Long replyToUid = event.getReplyToUid();
        if (replyToUid != null && !replyToUid.equals(event.getPostAuthorId())) {
            runQuietly(() -> notificationFacade.notifyComment(
                    replyToUid, event.getUid(), event.getPostId(), event.getCommentId()),
                    "reply comment", replyToUid, event.getUid(), TYPE_COMMENT, TARGET_COMMENT, event.getCommentId(),
                    Map.of("action", "comment", "postId", event.getPostId(), "commentId", event.getCommentId()));
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
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserFollowed(UserFollowedEvent event) {
        runQuietly(() -> notificationFacade.notifyFollower(
                event.getFolloweeId(), event.getFollowerId()),
                "user follow", event.getFolloweeId(), event.getFollowerId(), TYPE_FOLLOWER, TARGET_USER, event.getFollowerId(),
                Map.of("action", "follow", "userId", event.getFollowerId()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostFavorited(PostFavoritedEvent event) {
        runQuietly(() -> notificationFacade.notifyFavorite(
                event.getPostAuthorId(), event.getUid(), event.getPostId()),
                "post favorite", event.getPostAuthorId(), event.getUid(), TYPE_FAVORITE, TARGET_POST, event.getPostId(),
                Map.of("action", "favorite", "postId", event.getPostId()));
    }

    private void runQuietly(Runnable runnable, String scene, Long receiverUid, Long senderUid,
                            Integer notifType, Integer targetType, Long targetId, Map<String, Object> content) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.warn("create notification failed, scene={}: {}", scene, e.getMessage());
            retryService.enqueue(scene, receiverUid, senderUid, notifType, targetType, targetId, content, e);
        }
    }

    private void notifyMentions(Long senderUid, Long postId, Long commentId, String text, Set<Long> excludedUids) {
        Set<String> names = extractMentionNames(text);
        if (names.isEmpty()) {
            return;
        }
        Map<String, Long> matched;
        try {
            matched = userFacade.findUserIdsByNicknames(names);
        } catch (Exception e) {
            log.warn("resolve notification mentions failed: {}", e.getMessage());
            return;
        }
        for (Long receiverUid : matched.values()) {
            if (receiverUid != null && (excludedUids == null || !excludedUids.contains(receiverUid))) {
                Map<String, Object> content = commentId == null
                        ? Map.of("action", "mention", "postId", postId)
                        : Map.of("action", "mention", "postId", postId, "commentId", commentId);
                runQuietly(() -> notificationFacade.notifyMention(receiverUid, senderUid, postId, commentId),
                        "user mention", receiverUid, senderUid, TYPE_MENTION,
                        commentId == null ? TARGET_POST : TARGET_COMMENT,
                        commentId == null ? postId : commentId,
                        content);
            }
        }
    }

    private void notifyTopicFollowers(PostPublishedEvent event) {
        if (event == null || event.getPostId() == null || event.getTopicNotificationTargets() == null
                || event.getTopicNotificationTargets().isEmpty()) {
            return;
        }
        Map<Long, List<PostPublishedEvent.TopicNotificationTarget>> byReceiver = new LinkedHashMap<>();
        for (PostPublishedEvent.TopicNotificationTarget topic : event.getTopicNotificationTargets()) {
            if (topic == null || topic.getFollowerUids() == null || topic.getFollowerUids().isEmpty()) {
                continue;
            }
            for (Long receiverUid : topic.getFollowerUids()) {
                if (receiverUid == null || receiverUid <= 0 || receiverUid.equals(event.getAuthorId())) {
                    continue;
                }
                byReceiver.computeIfAbsent(receiverUid, ignored -> new ArrayList<>()).add(topic);
            }
        }
        byReceiver.forEach((receiverUid, topics) -> {
            Map<String, Object> content = topicNotificationContent(event, topics);
            runQuietly(() -> notificationFacade.notifySystem(receiverUid, (long) TARGET_POST, event.getPostId(), content),
                    "topic post published", receiverUid, 0L, TYPE_SYSTEM, TARGET_POST, event.getPostId(), content);
        });
    }

    private Map<String, Object> topicNotificationContent(PostPublishedEvent event,
                                                         List<PostPublishedEvent.TopicNotificationTarget> topics) {
        List<Map<String, Object>> topicData = topics == null ? List.of() : topics.stream()
                .filter(topic -> topic != null && topic.getTopicId() != null)
                .limit(5)
                .map(topic -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("topicId", topic.getTopicId());
                    item.put("topicSlug", topic.getTopicSlug());
                    item.put("topicName", topic.getTopicName());
                    return item;
                })
                .toList();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("action", "topic_post_published");
        content.put("postId", event.getPostId());
        content.put("postTitle", event.getTitle());
        if (!topicData.isEmpty()) {
            Map<String, Object> first = topicData.get(0);
            content.put("topicId", first.get("topicId"));
            content.put("topicSlug", first.get("topicSlug"));
            content.put("topicName", first.get("topicName"));
            content.put("topics", topicData);
        }
        return content;
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
