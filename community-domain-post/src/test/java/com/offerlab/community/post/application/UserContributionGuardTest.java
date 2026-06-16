package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UserContributionGuardTest {

    private static final Path ROOT = Path.of(System.getProperty("user.dir")).getParent();

    @Test
    void userContributionMustUseBackendAggregatePrivacyAndFrontendFallback() throws Exception {
        String dto = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/api/dto/UserContributionDTO.java"));
        String service = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/application/UserContributionService.java"));
        String controller = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/controller/UserContributionController.java"));
        String mapper = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostMapper.java"));
        String api = read(ROOT.resolve("../offerlab-vue/src/api/user.ts"));
        String userProfile = read(ROOT.resolve("../offerlab-vue/src/views/UserProfileView.vue"));
        String meProfile = read(ROOT.resolve("../offerlab-vue/src/views/MeProfileView.vue"));

        assertTrue(dto.contains("class UserContributionDTO"), "backend must expose a typed contribution DTO");
        assertTrue(dto.contains("private String source"), "contribution DTO must expose data source");
        assertTrue(dto.contains("private Boolean estimated"), "contribution DTO must expose estimated flag");

        assertTrue(controller.contains("@RequestMapping(\"/api/v1/users\")"), "contribution controller must live on user-facing API path");
        assertTrue(controller.contains("@GetMapping(\"/{uid}/contribution\")"), "public author contribution endpoint must exist");
        assertTrue(controller.contains("@GetMapping(\"/me/contribution\")"), "current user contribution endpoint must exist");
        assertTrue(controller.contains("@PublicApi"), "author contribution endpoint must be public with privacy filtering");
        assertTrue(service.contains("isProfileVisible"), "contribution service must respect profile privacy");
        assertTrue(service.contains("profile_restricted"), "restricted profiles must not leak contribution counters");
        assertTrue(service.contains("backend_aggregate"), "contribution service must label backend aggregate data");
        assertTrue(service.contains("postCount * 10 + featuredCount * 30 + likeCount + favoriteCount * 2 + commentCount * 2"), "backend score must match the frontend contribution formula");

        assertTrue(mapper.contains("aggregatePublicContributionByAuthor"), "post mapper must aggregate contribution from existing post tables");
        assertTrue(mapper.contains("t_post_counter"), "contribution aggregate must include interaction counters");
        assertTrue(mapper.contains("$.featured"), "contribution aggregate must count featured posts");
        assertTrue(mapper.contains("NOT LIKE '%CODEX%'"), "contribution aggregate must exclude synthetic test data");

        assertTrue(api.contains("UserContribution"), "frontend user API must type contribution summary");
        assertTrue(api.contains("/api/v1/users/${uid}/contribution"), "frontend user API must load author contribution");
        assertTrue(api.contains("/api/v1/users/me/contribution"), "frontend user API must load my contribution");
        assertTrue(userProfile.contains("backendContribution"), "author profile must prefer backend contribution");
        assertTrue(meProfile.contains("backendContribution"), "me profile must prefer backend contribution");
        assertTrue(userProfile.contains("buildContributionSummary(posts.value)") && meProfile.contains("buildContributionSummary(posts.items)"), "profiles must retain frontend fallback estimation");
        assertTrue(userProfile.contains("contributionSourceText") && meProfile.contains("contributionSourceText"), "profiles must show contribution data source");
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
