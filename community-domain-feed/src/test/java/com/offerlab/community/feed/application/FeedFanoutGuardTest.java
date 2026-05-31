package com.offerlab.community.feed.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedFanoutGuardTest {

    @Test
    void feedFanoutMustPageThroughAllFollowersByRelationCursor() throws Exception {
        String fanoutService = read("src/main/java/com/offerlab/community/feed/application/FeedFanoutService.java");
        String feedRedis = read("src/main/java/com/offerlab/community/feed/infrastructure/FeedInboxRedis.java");
        String kafkaConsumer = read("src/main/java/com/offerlab/community/feed/application/PostPublishedFeedConsumer.java");
        String followRepository = read("../community-domain-user/src/main/java/com/offerlab/community/user/infrastructure/persistence/FollowRepositoryImpl.java");

        assertTrue(fanoutService.contains("FANOUT_BATCH_SIZE = 1000"), "fanout must use bounded batches instead of a total cap");
        assertTrue(fanoutService.contains("while (true)"), "fanout must keep reading follower pages until exhausted");
        assertTrue(fanoutService.contains("userFacade.getFollowerPage(authorId, cursor, FANOUT_BATCH_SIZE)"), "fanout must use relation cursor follower pages");
        assertTrue(fanoutService.contains("last.getRelationId()"), "next fanout cursor must come from relation id, not user id");
        assertTrue(fanoutService.contains("followers.size() < FANOUT_BATCH_SIZE"), "fanout must stop only when the last page is shorter than the batch");
        assertTrue(fanoutService.contains("batches"), "fanout logs must expose batch count for large-author troubleshooting");
        assertTrue(feedRedis.contains("throw new IllegalStateException"), "Redis fanout write failures must bubble up to Kafka retry/DLT");
        assertTrue(fanoutService.contains("idempotentChecker.release"), "failed fanout must release idempotency so retries can run");
        assertTrue(kafkaConsumer.contains("throw e"), "Kafka consumer must rethrow fanout failures so the container can retry");

        assertFalse(fanoutService.contains("getFollowerIds(authorId, 0"), "fanout must not fetch one capped follower list from offset zero");
        assertFalse(fanoutService.contains("MAX_FANOUT"), "fanout must not silently drop followers after a hard total cap");

        assertTrue(followRepository.contains("MAX_PAGE_LIMIT = 1000"), "follow repository must permit feed-sized internal batches");
        assertTrue(followRepository.contains("Math.min(size, MAX_PAGE_LIMIT)"), "follow repository must still clamp SQL LIMIT values");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
