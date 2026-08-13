package com.offerlab.community.feed.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(controller.contains("@PostMapping(\"/author-controls\")"));
        assertTrue(controller.contains("@DeleteMapping(\"/author-controls/{authorUid}\")"));
        assertTrue(controller.contains("@GetMapping(\"/controls\")"));
        assertTrue(controller.contains("@DeleteMapping(\"/controls/{controlId}\")"));
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
        assertTrue(store.contains("listActiveBlockedAuthorIds"));
        assertTrue(store.contains("BLOCK_AUTHOR"));
        assertTrue(store.contains("syntheticAuthorPostId"));
        assertTrue(store.contains("listControls"));
        assertTrue(store.contains("deleteControl"));
        assertTrue(store.contains("controlReadFaulted"));
        assertTrue(store.contains("controlReadUnavailable"));
        assertTrue(store.contains("hiddenPostIdsFromRedis"));
        assertTrue(store.contains("lessLikedDomainsFromRedis"));
        assertTrue(store.contains("reverseRangeByScore"));
        assertTrue(store.contains("CONTROL_DURATION.toMillis()"));
        assertTrue(store.contains("persistentTargetDomain"));
        assertTrue(store.contains("redisTargetDomain"));
        assertTrue(store.contains("Redis is a cache only"));

        assertTrue(mapper.contains("uid = #{uid}"));
        assertTrue(mapper.contains("post_id = #{postId}"));
        assertTrue(mapper.contains("target_type = 'AUTHOR'"));
        assertTrue(mapper.contains("deleteAuthorControl"));
        assertTrue(mapper.contains("deleteOwnedControlIfUnchanged"));
        assertTrue(mapper.contains("reason <=> #{control.reason}"));
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

    @Test
    void controlMigrationsMustConstrainValidShapesAndKeepAllMirrorsSynchronized() throws Exception {
        String v29 = readRepo("db/migration/20260802_user_controls_trustworthy_distribution.sql");
        String v29Init = readRepo("db/init/37_user_controls_trustworthy_distribution.sql");
        String v31 = readRepo("db/migration/20260803_creator_quality_signal_query_index.sql");
        String v31Init = readRepo("db/init/38_creator_quality_signal_query_index.sql");
        String syncGuard = readRepo("db/migration/sync-flyway-resources.ps1");

        assertEquals(v29, v29Init);
        assertTrue(v29.contains("chk_feed_feedback_action_target_expiry"));
        assertTrue(v29.contains("action IN ('HIDE', 'LESS_LIKE_THIS')"));
        assertTrue(v29.contains("target_type = 'DOMAIN'"));
        assertTrue(v29.contains("action = 'BLOCK_AUTHOR'"));
        assertTrue(v29.contains("expires_at IS NULL"));

        assertEquals(v31, v31Init);
        assertTrue(v31.contains("(reason, action, post_id, expires_at, update_time, uid)"));
        assertTrue(syncGuard.contains("20260802_user_controls_trustworthy_distribution.sql"));
        assertTrue(syncGuard.contains("37_user_controls_trustworthy_distribution.sql"));
        assertTrue(syncGuard.contains("20260803_creator_quality_signal_query_index.sql"));
        assertTrue(syncGuard.contains("38_creator_quality_signal_query_index.sql"));
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }

    private static String readRepo(String relativePath) throws Exception {
        return Files.readString(Path.of("..").resolve(relativePath).normalize(), StandardCharsets.UTF_8);
    }
}
