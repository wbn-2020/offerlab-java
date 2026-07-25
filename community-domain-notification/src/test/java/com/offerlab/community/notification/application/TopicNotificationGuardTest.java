package com.offerlab.community.notification.application;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicNotificationGuardTest {

    private static final Path ROOT = Path.of(System.getProperty("user.dir")).getParent();

    @Test
    void topicFollowersReceiveSystemNotificationAfterMatchedPostPublished() throws Exception {
        String event = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/api/event/PostPublishedEvent.java"));
        assertContains(event, "private List<Long> tagIds");
        assertContains(event, "private List<TopicNotificationTarget> topicNotificationTargets");
        assertContains(event, "class TopicNotificationTarget");
        assertContains(event, "private List<Long> followerUids");

        String postService = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/application/PostApplicationService.java"));
        assertContains(postService, "CommunityTopicNotificationTargetService communityTopicNotificationTargetService");
        assertContains(postService, "publishPostPublishedEvent(post, resolvedTagIds)");
        assertContains(postService, ".topicNotificationTargets(topicNotificationTargets(post, tagIds))");
        assertContains(postService, "Objects.equals(post.getVisibility(), Post.VIS_PUBLIC)");
        assertContains(postService, "Objects.equals(post.getPostStatus(), Post.STATUS_PUBLISHED)");
        assertContains(postService, "communityTopicNotificationTargetService.targetsForPost(tagIds, post.getExtJson(), post.getAuthorId())");

        String targetService = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/application/CommunityTopicNotificationTargetService.java"));
        assertContains(targetService, "class CommunityTopicNotificationTargetService");
        assertContains(targetService, "targetsForPost");
        assertContains(targetService, "selectOnlineTopicsByTagIds");
        assertContains(targetService, "selectFollowerUidsForNotification");
        assertContains(targetService, "MAX_NOTIFICATION_TOPICS");
        assertContains(targetService, "MAX_TOPIC_FOLLOWER_FANOUT");
        assertContains(targetService, "int remainingFanout = MAX_TOPIC_FOLLOWER_FANOUT");
        assertContains(targetService, "remainingFanout -= followerUids.size()");

        String topicMapper = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/CommunityTopicMapper.java"));
        assertContains(topicMapper, "selectOnlineTopicsByTagIds");
        assertContains(topicMapper, "JOIN t_community_topic_tag");
        assertContains(topicMapper, "AND t.topic_status = 1");

        String followMapper = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/CommunityTopicFollowMapper.java"));
        assertContains(followMapper, "selectFollowerUidsForNotification");
        assertContains(followMapper, "uid != #{authorId}");

        String listener = read(ROOT.resolve("community-domain-notification/src/main/java/com/offerlab/community/notification/application/NotificationEventListener.java"));
        assertContains(listener, "notifyTopicFollowers(event)");
        assertContains(listener, "topic_post_published");
        assertContains(listener, "notificationFacade.notifySystem");
        assertContains(listener, "topicNotificationContent");
        assertContains(listener, "\"topics\"");
        assertContains(listener, "retryService.enqueue");
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), () -> "Expected source to contain: " + expected);
    }
}
