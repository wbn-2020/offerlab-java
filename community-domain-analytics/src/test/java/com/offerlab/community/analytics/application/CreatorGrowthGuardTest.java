package com.offerlab.community.analytics.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatorGrowthGuardTest {

    @Test
    void creatorGrowthP0DoesNotIntroducePaymentBadgeChallengeOrJobTrainingLanguage() throws IOException {
        String source = readAll(
                "src/main/java/com/offerlab/community/analytics/api/dto/CreatorGrowthWorkspaceDTO.java",
                "src/main/java/com/offerlab/community/analytics/application/CreatorGrowthService.java",
                "src/main/java/com/offerlab/community/analytics/application/GrowthInsightService.java",
                "src/main/java/com/offerlab/community/analytics/controller/CreatorGrowthController.java")
                .toLowerCase(Locale.ROOT);

        List<String> forbidden = List.of(
                "revenue", "withdraw", "subscription", "membership", "tip jar", "paid column",
                "badge", "challenge", "certified", "authority", "influence", "recommendation", "recommended",
                "system_recommended", "expertcertificationservice",
                "usertaskapplicationservice", "mock interview", "interview training", "job training",
                "收益", "提现", "会员", "订阅", "打赏", "付费专栏", "徽章", "挑战",
                "求职训练", "模拟面试", "刷题", "认证专家");
        for (String term : forbidden) {
            assertFalse(source.contains(term.toLowerCase(Locale.ROOT)), "Forbidden Phase 10 P0 term leaked: " + term);
        }
    }

    @Test
    void representativePostsKeepPublicVisibilityAndOwnershipBoundary() throws IOException {
        String mapper = Files.readString(resolve(
                "src/main/java/com/offerlab/community/analytics/infrastructure/persistence/mapper/GrowthInsightMapper.java"));
        String service = Files.readString(resolve(
                "src/main/java/com/offerlab/community/analytics/application/CreatorGrowthService.java"));

        assertTrue(mapper.contains("p.is_deleted = 0"), "representative posts must ignore deleted posts");
        assertTrue(mapper.contains("p.post_status = 1"), "representative posts must require published posts");
        assertTrue(mapper.contains("p.visibility = 1"), "representative posts must require public posts");
        assertTrue(mapper.contains("p.author_id = #{authorId}"), "representative posts must be owned by the creator");
        assertTrue(service.contains("Representative posts must be your public visible posts"),
                "manual representative updates must reject hidden, deleted, restricted, or foreign posts");
    }

    @Test
    void creatorFeedbackDoesNotExposeAnonymousOrCommenterIdentity() throws IOException {
        String mapper = Files.readString(resolve(
                "src/main/java/com/offerlab/community/analytics/infrastructure/persistence/mapper/GrowthInsightMapper.java"));
        String service = Files.readString(resolve(
                "src/main/java/com/offerlab/community/analytics/application/CreatorGrowthService.java"));

        assertFalse(mapper.contains("AS commenterUid"), "reply opportunities must not expose commenter uid");
        assertFalse(mapper.contains("AS commenterName"), "reply opportunities must not expose commenter name");
        assertFalse(mapper.contains("AS authorId"), "reply opportunities must not expose comment author id");
        assertFalse(service.contains(".commenterUid("), "creator feedback must not set commenter uid");
    }

    @Test
    void topicIdeasAndDigestKeepNoPromiseAndPreferenceBoundary() throws IOException {
        String source = readAll(
                "src/main/java/com/offerlab/community/analytics/api/dto/CreatorGrowthWorkspaceDTO.java",
                "src/main/java/com/offerlab/community/analytics/application/CreatorGrowthService.java")
                .toLowerCase(Locale.ROOT);

        assertTrue(source.contains("weekly_digest_only"), "creator digest must stay low-frequency");
        assertTrue(source.contains("existing preferences"), "creator digest must defer to notification preferences");
        assertTrue(source.contains("low-frequency"), "creator digest must keep anti-disturbance copy");
        for (String term : List.of("guarantee", "guaranteed", "go viral", "gain followers", "promised outcome")) {
            assertFalse(source.contains(term), "topic ideas and digest must not promise outcomes: " + term);
        }
    }

    private static String readAll(String... files) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (String file : files) {
            builder.append(Files.readString(resolve(file))).append('\n');
        }
        return builder.toString();
    }

    private static Path resolve(String file) {
        Path modulePath = Path.of(file);
        if (Files.exists(modulePath)) {
            return modulePath;
        }
        return Path.of("community-domain-analytics").resolve(file);
    }
}
