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
        String localFanoutListener = read("src/main/java/com/offerlab/community/feed/application/FeedFanoutListener.java");
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
        assertTrue(kafkaConsumer.contains("IdempotentEventConsumer"),
                "Kafka fanout must use the transactional consumer inbox");
        assertTrue(kafkaConsumer.contains("FeedFanoutService.idempotencyKey(event)"),
                "feed idempotency must distinguish separate publications of the same post");
        assertTrue(fanoutService.contains("nextCursor >= cursor"),
                "fanout must fail when a follower cursor does not advance");
        assertTrue(kafkaConsumer.contains("throw e"), "Kafka consumer must rethrow fanout failures so the container can retry");
        assertTrue(localFanoutListener.contains("local-event-fanout-enabled"),
                "local Redis fanout must remain an explicit opt-in mode");
        assertTrue(read("src/main/java/com/offerlab/community/feed/application/FeedFacadeImpl.java")
                        .contains("!kafkaEnabled || !feedKafkaConsumerEnabled"),
                "Kafka-disabled deployments must use the database following-feed fallback");
        assertTrue(read("../community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostMapper.java")
                        .contains("JOIN t_user_follow f"),
                "database fallback must join the follow relation before applying LIMIT");

        assertFalse(fanoutService.contains("getFollowerIds(authorId, 0"), "fanout must not fetch one capped follower list from offset zero");
        assertFalse(fanoutService.contains("MAX_FANOUT"), "fanout must not silently drop followers after a hard total cap");

        assertTrue(followRepository.contains("MAX_PAGE_LIMIT = 1000"), "follow repository must permit feed-sized internal batches");
        assertTrue(followRepository.contains("Math.min(size, MAX_PAGE_LIMIT)"), "follow repository must still clamp SQL LIMIT values");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
