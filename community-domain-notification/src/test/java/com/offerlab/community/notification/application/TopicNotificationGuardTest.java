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
        assertContains(postService, "CommunityTopicService communityTopicService");
        assertContains(postService, "topicNotificationTargets(communityTopicService.notificationTargetsForPost(resolvedTagIds, cmd.getAuthorId()))");

        String topicService = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/application/CommunityTopicService.java"));
        assertContains(topicService, "notificationTargetsForPost");
        assertContains(topicService, "selectOnlineTopicsByTagIds");
        assertContains(topicService, "selectFollowerUidsForNotification");
        assertContains(topicService, "MAX_TOPIC_FOLLOWER_FANOUT");

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
