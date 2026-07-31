package com.offerlab.community.archtest;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase11GovernancePrivacyGuardTest {

    @Test
    void userReportEndpointsReturnOnlyOpaqueReportIds() throws Exception {
        String postController = read("community-domain-post/src/main/java/com/offerlab/community/post/controller/PostController.java");
        String interactionController = read("community-domain-interaction/src/main/java/com/offerlab/community/interaction/controller/InteractionController.java");

        assertContains(postController, "public Result<Map<String, Long>> report(", "post report endpoint must not return admin report DTOs");
        assertContains(postController, "Map.of(\"reportId\", reportId)", "post report endpoint must return only an opaque report id");
        assertNotContains(endpointBody(postController, "public Result<Map<String, Long>> report("), "PostReportDTO", "post report endpoint must not expose reporter/reviewer details");

        assertContains(interactionController, "public Result<Map<String, Long>> reportComment(", "comment report endpoint must not return admin report DTOs");
        assertContains(interactionController, "Map.of(\"reportId\", reportId)", "comment report endpoint must return only an opaque report id");
        assertNotContains(endpointBody(interactionController, "public Result<Map<String, Long>> reportComment("), "CommentReportDTO", "comment report endpoint must not expose reporter/reviewer details");
    }

    @Test
    void reportAdminEndpointsAndReviewServicesRemainPermissionScoped() throws Exception {
        String postController = read("community-domain-post/src/main/java/com/offerlab/community/post/controller/PostController.java");
        String interactionController = read("community-domain-interaction/src/main/java/com/offerlab/community/interaction/controller/InteractionController.java");
        String postReportService = read("community-domain-post/src/main/java/com/offerlab/community/post/application/PostReportService.java");
        String commentReportService = read("community-domain-interaction/src/main/java/com/offerlab/community/interaction/application/CommentReportService.java");

        assertContains(postController, "@GetMapping(\"/admin/reports\")", "post report list must stay admin-routed");
        assertContains(postController, "domainModeratorService.requireModerateDomain(UserContext.require(), domain)", "post report list must require domain moderation scope");
        assertContains(postReportService, "domainModeratorService.requireModerateDomain(reviewerUid, post.getDomain())", "post report review must require domain moderation scope");
        assertContains(postReportService, "adminAuditService.recordRequired(reviewerUid", "post report review must write an admin audit log");

        assertContains(interactionController, "@GetMapping(\"/comments/admin/reports\")", "comment report list must stay admin-routed");
        assertContains(interactionController, "domainModeratorService.requireModerateDomain(UserContext.require(), domain)", "comment report list must require domain moderation scope");
        assertContains(commentReportService, "domainModeratorService.requireModerateDomain(reviewerUid, post.getDomain())", "comment report review must require domain moderation scope");
        assertContains(commentReportService, "adminAuditService.recordRequired(reviewerUid", "comment report review must write an admin audit log");
    }

    @Test
    void reviewQueueAndAuditDataAreAdminOnlyAndHighRiskActionsNeedConfirmation() throws Exception {
        String reviewQueueController = read("community-domain-search/src/main/java/com/offerlab/community/search/controller/ReviewQueueController.java");
        String reviewQueueService = read("community-domain-search/src/main/java/com/offerlab/community/search/application/ReviewQueueService.java");
        String opsController = read("community-domain-search/src/main/java/com/offerlab/community/search/controller/OpsController.java");
        String feedController = read("community-domain-feed/src/main/java/com/offerlab/community/feed/controller/FeedController.java");
        String userController = read("community-domain-user/src/main/java/com/offerlab/community/user/controller/UserController.java");

        assertContains(reviewQueueController, "@RequestMapping(\"/api/v1/admin/review-queue\")", "review queue must stay under admin routing");
        assertContains(reviewQueueService, "AdminPermissionService.ROLE_CONTENT_MODERATOR",
                "each review queue endpoint must require content moderator scope");
        assertContains(reviewQueueService, "public ReviewQueueItemPO approve(Long id, Long operatorUid, String note, String confirmationPhrase)", "approve action must accept confirmation phrase");
        assertContains(reviewQueueService, "public ReviewQueueItemPO reject(Long id, Long operatorUid, String note, String confirmationPhrase)", "reject action must accept confirmation phrase");
        assertContains(reviewQueueService, "public ReviewQueueItemPO close(Long id, Long operatorUid, String note, String confirmationPhrase)", "close action must accept confirmation phrase");
        assertTrue(count(reviewQueueService, "RiskConfirmation.requireCritical(note, confirmationPhrase)") >= 3,
                "approve/reject/close review queue actions must require critical confirmation");
        assertContains(reviewQueueService, "auditService.recordRequired(operatorUid, action, \"REVIEW_QUEUE\"", "review queue decisions must write required audit logs");

        assertContains(opsController, "@GetMapping(\"/audit-logs\")", "audit logs must be available only through ops controller");
        assertContains(opsController, "adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS)", "audit log reads must require ops scope");
        assertNoPublicGovernanceLeaks(feedController, "FeedController");
        assertNoPublicGovernanceLeaks(userController, "UserController");
    }

    @Test
    void governanceDtosDoNotExposeRealAuthorsOrPrivateBlockRelations() throws Exception {
        String postReportDto = read("community-domain-post/src/main/java/com/offerlab/community/post/api/dto/PostReportDTO.java");
        String commentReportDto = read("community-domain-interaction/src/main/java/com/offerlab/community/interaction/api/dto/CommentReportDTO.java");
        String userController = read("community-domain-user/src/main/java/com/offerlab/community/user/controller/UserController.java");

        assertContains(postReportDto, "private Long reporterUid;", "admin post report DTO may retain reporter id for permissioned review");
        assertNotContains(postReportDto, "realAuthor", "admin post report DTO must not reveal anonymous real author identity");
        assertNotContains(postReportDto, "originalAuthor", "admin post report DTO must not reveal anonymous original author identity");
        assertContains(commentReportDto, "private Long reporterUid;", "admin comment report DTO may retain reporter id for permissioned review");
        assertNotContains(commentReportDto, "realAuthor", "admin comment report DTO must not reveal anonymous real author identity");
        assertNotContains(commentReportDto, "originalAuthor", "admin comment report DTO must not reveal anonymous original author identity");

        for (String privateRelationField : List.of("blockedUsers", "blockedBy", "mutedBy", "blockRelation", "muteRelation")) {
            assertNotContains(userController, privateRelationField, "public user controller must not expose private block or mute relations");
        }
    }

    @Test
    void phase11GovernanceFilesDoNotDriftIntoCommercialOrPrivateTrainingGovernance() throws Exception {
        String combined = String.join("\n",
                read("community-domain-search/src/main/java/com/offerlab/community/search/controller/ReviewQueueController.java"),
                read("community-domain-search/src/main/java/com/offerlab/community/search/application/ReviewQueueService.java"),
                read("community-domain-post/src/main/java/com/offerlab/community/post/application/PostReportService.java"),
                read("community-domain-interaction/src/main/java/com/offerlab/community/interaction/application/CommentReportService.java"),
                read("community-infrastructure/src/main/java/com/offerlab/community/infra/moderation/ContentModerationService.java"),
                read("community-infrastructure/src/main/java/com/offerlab/community/infra/moderation/ModerationAdminService.java"),
                read("db/init/09_moderation.sql")
        );

        for (String forbidden : List.of(
                "payment risk",
                "revenue penalty",
                "ad review",
                "expert endorsement",
                "CodeCoachAI private training",
                "paywall",
                "withdrawal",
                "settlement",
                "monetization governance",
                "广告审核",
                "支付风控",
                "收益处罚",
                "提现",
                "结算",
                "付费专栏",
                "专家背书",
                "私人训练治理"
        )) {
            assertFalse(combined.toLowerCase().contains(forbidden.toLowerCase()),
                    "Phase 11 governance files must not include commercial/private-training governance term: " + forbidden);
        }
    }

    private static void assertNoPublicGovernanceLeaks(String source, String name) {
        for (String sensitive : List.of("PostReportDTO", "CommentReportDTO", "AdminAuditLog", "reporterUid",
                "reviewerUid", "reviewNote", "auditRemark", "riskScore", "internalRule",
                "realAuthor", "originalAuthor", "blockedUsers", "blockedBy", "mutedBy")) {
            assertNotContains(source, sensitive, name + " must not expose governance internals through ordinary public APIs");
        }
    }

    private static String endpointBody(String source, String methodSignature) {
        int start = source.indexOf(methodSignature);
        assertTrue(start >= 0, "method not found: " + methodSignature);
        int nextMapping = source.indexOf("\n    @", start + methodSignature.length());
        return nextMapping > start ? source.substring(start, nextMapping) : source.substring(start);
    }

    private static int count(String source, String needle) {
        int total = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            total++;
            index += needle.length();
        }
        return total;
    }

    private static String read(String path) throws Exception {
        return Files.readString(RepositoryTestPaths.resolve(path), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String needle, String message) {
        assertTrue(source.contains(needle), message);
    }

    private static void assertNotContains(String source, String needle, String message) {
        assertFalse(source.contains(needle), message);
    }
}
