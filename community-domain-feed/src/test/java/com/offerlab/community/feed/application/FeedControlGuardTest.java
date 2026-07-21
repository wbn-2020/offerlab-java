package com.offerlab.community.feed.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedControlGuardTest {

    @Test
    void feedControlsMustBeExplainablePersistentAccountScopedAndRateLimited() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/feed/controller/FeedController.java");
        String facade = read("src/main/java/com/offerlab/community/feed/application/FeedFacadeImpl.java");
        String store = read("src/main/java/com/offerlab/community/feed/infrastructure/FeedFeedbackStore.java");
        String mapper = read("src/main/java/com/offerlab/community/feed/infrastructure/persistence/mapper/FeedFeedbackPreferenceMapper.java");
        String item = read("src/main/java/com/offerlab/community/feed/api/dto/FeedItemVO.java");

        assertTrue(controller.contains("@GetMapping(\"/feedback/preferences\")"));
        assertTrue(controller.contains("@GetMapping(\"/feedback/preferences/{postId}\")"));
        assertTrue(controller.contains("@DeleteMapping(\"/feedback/preferences/{postId}\")"));
        assertTrue(controller.contains("@RateLimit"));
        assertTrue(controller.contains("UserContext.require()"));

        assertTrue(item.contains("sourceType"));
        assertTrue(item.contains("sourceLabel"));
        assertTrue(item.contains("reasonCode"));
        assertTrue(item.contains("reasonText"));
        assertTrue(facade.contains("PublicContentFilter.isDistributablePost"));
        assertTrue(facade.contains("lessLikedDomains"));
        assertTrue(facade.contains("FeedSource"));

        assertTrue(store.contains("mapper.upsert"));
        assertTrue(store.contains("mapper.deleteByPost"));
        assertTrue(store.contains("listActiveHiddenPostIds"));
        assertTrue(store.contains("listActiveReducedDomainIds"));
        assertTrue(store.contains("hiddenPostIdsFromRedis"));
        assertTrue(store.contains("lessLikedDomainsFromRedis"));
        assertTrue(store.contains("reverseRangeByScore"));
        assertTrue(store.contains("CONTROL_DURATION.toMillis()"));
        assertTrue(store.contains("persistentTargetDomain"));
        assertTrue(store.contains("redisTargetDomain"));
        assertTrue(store.contains("Redis is a cache only"));

        assertTrue(mapper.contains("uid = #{uid}"));
        assertTrue(mapper.contains("post_id = #{postId}"));
        assertTrue(mapper.contains("ORDER BY update_time DESC, id DESC"));
        assertTrue(mapper.contains("update_time &lt; #{cursorTime}"));
    }

    @Test
    void feedMustNotIntroduceOpaqueRecommendationModelOrPublicFeedbackIdentity() throws Exception {
        String facade = read("src/main/java/com/offerlab/community/feed/application/FeedFacadeImpl.java");
        String preference = read("src/main/java/com/offerlab/community/feed/api/dto/FeedFeedbackPreferenceVO.java");

        assertTrue(!facade.contains("modelScore") && !facade.contains("embedding"));
        assertTrue(!preference.contains("uid"));
        assertTrue(!facade.contains("setUid"));
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }
}
