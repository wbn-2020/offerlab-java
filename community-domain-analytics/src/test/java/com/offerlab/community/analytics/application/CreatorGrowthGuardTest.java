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
        String dto = Files.readString(resolve(
                "src/main/java/com/offerlab/community/analytics/api/dto/CreatorGrowthWorkspaceDTO.java"));

        assertTrue(mapper.contains("p.is_deleted = 0"), "representative posts must ignore deleted posts");
        assertTrue(mapper.contains("p.post_status = 1"), "representative posts must require published posts");
        assertTrue(mapper.contains("p.visibility = 1"), "representative posts must require public posts");
        assertTrue(mapper.contains("p.author_id = #{authorId}"), "representative posts must be owned by the creator");
        assertTrue(mapper.contains("JSON_EXTRACT(e.ext_json, '$.anonymous')"),
                "representative posts and workspace feedback must exclude anonymous posts");
        assertTrue(mapper.contains("e.domain IS NOT NULL"),
                "domain-specific growth views must exclude unclassified posts");
        assertFalse(mapper.contains("COALESCE(e.domain, 1)"),
                "unclassified posts must not be reassigned to the technology domain");
        assertTrue(service.contains("Representative posts must be your public visible posts"),
                "manual representative updates must reject hidden, deleted, restricted, or foreign posts");
        for (String field : List.of(
                "trustedContent", "pendingSuggestions", "freshnessAwaitingConfirmation",
                "unresolvedQuestions", "usefulFeedback7Days", "usefulFeedback30Days",
                "effectiveReads7Days", "effectiveReads30Days")) {
            assertTrue(dto.contains(field), "creator workspace must expose trusted-content field: " + field);
        }
        assertTrue(mapper.contains("t_int_content_suggestion"), "creator workspace must aggregate pending private suggestions");
        assertTrue(mapper.contains("t_int_post_trust_state"), "creator workspace must aggregate question and freshness tasks");
        assertTrue(mapper.contains("t_int_post_useful_feedback"), "creator workspace must aggregate useful feedback");
        assertTrue(mapper.contains("EFFECTIVE_READ"), "creator workspace must aggregate trusted effective reads");
        assertTrue(service.contains("trustedContent"), "creator workspace service must map trusted-content metrics");
    }

    @Test
    void creatorFeedbackDoesNotExposeAnonymousOrCommenterIdentity() throws IOException {
        String mapper = Files.readString(resolve(
                "src/main/java/com/offerlab/community/analytics/infrastructure/persistence/mapper/GrowthInsightMapper.java"));
        String service = Files.readString(resolve(
                "src/main/java/com/offerlab/community/analytics/application/CreatorGrowthService.java"));
        String dto = Files.readString(resolve(
                "src/main/java/com/offerlab/community/analytics/api/dto/CreatorGrowthWorkspaceDTO.java"));
        String replyQuery = mapper.substring(
                mapper.indexOf("SELECT c.id AS commentId"),
                mapper.indexOf("List<Map<String, Object>> selectCreatorReplyOpportunities"));

        assertFalse(replyQuery.contains("AS commenterUid"), "reply opportunities must not expose commenter uid");
        assertFalse(replyQuery.contains("AS commenterName"), "reply opportunities must not expose commenter name");
        assertFalse(replyQuery.contains("AS authorId"), "reply opportunities must not expose comment author id");
        assertFalse(service.contains(".commenterUid("), "creator feedback must not set commenter uid");
        assertFalse(dto.contains("commenterUid"), "creator feedback DTO must not retain a commenter uid field");
    }

    @Test
    void trustedContentTasksStayBoundedPrivateAndUseLifecycleStatuses() throws IOException {
        String mapper = Files.readString(resolve(
                "src/main/java/com/offerlab/community/analytics/infrastructure/persistence/mapper/GrowthInsightMapper.java"));
        String service = Files.readString(resolve(
                "src/main/java/com/offerlab/community/analytics/application/CreatorGrowthService.java"));
        String dto = Files.readString(resolve(
                "src/main/java/com/offerlab/community/analytics/api/dto/CreatorGrowthWorkspaceDTO.java"));

        assertTrue(service.contains(".limit(5)"),
                "each trusted-content task list must stay bounded to five items");
        assertTrue(mapper.contains("'PENDING' AS status"),
                "pending suggestions must expose the pending lifecycle status");
        assertTrue(mapper.contains("s.freshness_status AS status")
                        && mapper.contains("s.freshness_status = 'AWAITING_AUTHOR_CONFIRMATION'"),
                "freshness tasks must expose the persisted awaiting-confirmation lifecycle status");
        assertTrue(mapper.contains("COALESCE(s.question_status, 'OPEN') AS status"),
                "question tasks must expose the persisted question lifecycle status");
        assertFalse(mapper.contains("s.suggestion_type AS status"),
                "suggestion type must never be mislabeled as a task lifecycle status");

        for (String privateField : List.of("detail", "submitterUid", "decision")) {
            assertFalse(dto.contains(privateField),
                    "creator workspace task DTO must not expose private suggestion field: " + privateField);
            assertFalse(service.contains("row.get(\"" + privateField + "\")"),
                    "creator workspace task mapper must not read private suggestion field: " + privateField);
        }
    }

    @Test
    void pendingAuthorSuggestionsHaveAnAuthorStatusTimeCoveringIndexAcrossSchemaContracts() throws IOException {
        String mapper = Files.readString(resolve(
                "src/main/java/com/offerlab/community/analytics/infrastructure/persistence/mapper/GrowthInsightMapper.java"));
        String interactionInit = Files.readString(resolve("../db/init/03_interaction.sql"));
        String trustedMigration = Files.readString(resolve("../db/migration/20260713_trusted_content_stage1.sql"));
        String flywayResource = Files.readString(resolve(
                "../community-bootstrap/src/main/resources/db/flyway/core/V20260713.01__trusted_content_stage1.sql"));
        String schemaReadiness = Files.readString(resolve("../scripts/check-schema-readiness.mjs"));
        String runtimeReadiness = Files.readString(resolve(
                "../community-infrastructure/src/main/java/com/offerlab/community/infra/db/MigrationCheckService.java"));

        assertTrue(mapper.contains("WHERE s.post_author_id = #{authorId}"), "pending author query must filter by post author");
        assertTrue(mapper.contains("AND s.decision IS NULL"), "pending author query must filter unresolved suggestions");
        assertTrue(mapper.contains("ORDER BY s.update_time DESC, s.id DESC"),
                "pending author query must sort by update time and id");
        for (String schema : List.of(interactionInit, trustedMigration, flywayResource)) {
            assertTrue(schema.contains("idx_content_suggestion_author_pending"),
                    "schema asset must declare author pending suggestion index");
            assertTrue(schema.contains("post_author_id, decision, update_time, id")
                            || schema.contains("post_author_id,decision,update_time,id"),
                    "author pending suggestion index must cover author, status, time and id");
        }
        assertTrue(schemaReadiness.contains("idx_content_suggestion_author_pending"),
                "schema readiness script must check author pending suggestion index");
        assertTrue(runtimeReadiness.contains("idx_content_suggestion_author_pending"),
                "runtime readiness must check author pending suggestion index");
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
