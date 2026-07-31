package com.offerlab.community.search.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewQueueGuardTest {

    @Test
    void reviewQueueMustBeBackendModeledPermissionedAuditedAndMigrated() throws Exception {
        String initSql = read("../db/init/11_governance.sql");
        String migrationSql = read("../db/migration/20260608_review_queue.sql");
        String po = read("src/main/java/com/offerlab/community/search/infrastructure/persistence/po/ReviewQueueItemPO.java");
        String mapper = read("src/main/java/com/offerlab/community/search/infrastructure/persistence/mapper/ReviewQueueMapper.java");
        String service = read("src/main/java/com/offerlab/community/search/application/ReviewQueueService.java");
        String controller = read("src/main/java/com/offerlab/community/search/controller/ReviewQueueController.java");
        String createCmd = read("src/main/java/com/offerlab/community/search/api/dto/ReviewQueueCreateCmd.java");
        String actionCmd = read("src/main/java/com/offerlab/community/search/api/dto/ReviewQueueActionCmd.java");

        assertTrue(initSql.contains("CREATE TABLE IF NOT EXISTS t_review_queue"), "fresh DB init must create the unified review queue table");
        assertTrue(migrationSql.contains("CREATE TABLE IF NOT EXISTS t_review_queue"), "migration must create review queue table non-destructively");
        assertTrue(migrationSql.contains("uk_review_queue_source"), "review queue must deduplicate by source");
        assertTrue(migrationSql.contains("idx_review_queue_status_priority"), "review queue must index status and priority");
        assertTrue(migrationSql.contains("idx_review_queue_source_status"), "review queue must support source/status filtering");
        assertTrue(migrationSql.contains("idx_review_queue_assignee_status"), "review queue must support assignee workbench queries");
        assertTrue(migrationSql.contains("idx_review_queue_risk_status"), "review queue must support risk/status triage");

        assertTrue(po.contains("@TableName(\"t_review_queue\")"), "review queue PO must map to the durable table");
        assertTrue(po.contains("private String sourceType"), "review queue items must expose source type");
        assertTrue(po.contains("private String queueStatus"), "review queue items must expose queue status");
        assertTrue(po.contains("private Long assigneeUid"), "review queue items must expose assignee");
        assertTrue(po.contains("private String handleNote"), "review queue items must persist handling notes");

        assertTrue(mapper.contains("tableExists()"), "review queue mapper must tolerate missing migration table");
        assertTrue(mapper.contains("ON DUPLICATE KEY UPDATE"), "source events must upsert idempotently into the queue");
        assertTrue(mapper.contains("queue_status = 'claimed'"), "queue mapper must claim individual items");
        assertTrue(mapper.contains("queue_status = 'pending'"), "queue mapper must release items back to pending");
        assertTrue(mapper.contains("handle_result = #{result}"), "queue mapper must persist handling result");
        assertTrue(mapper.contains("countByStatus"), "queue mapper must expose status counters");

        assertTrue(service.contains("ReviewQueueService"), "review queue service must own queue behavior");
        assertTrue(service.contains("auditService.recordRequired"), "review queue actions must leave required audit logs");
        assertTrue(service.contains("REVIEW_QUEUE_CREATE"), "queue creation must be audited");
        assertTrue(service.contains("REVIEW_QUEUE_CLAIM"), "queue claim must be audited");
        assertTrue(service.contains("REVIEW_QUEUE_RELEASE"), "queue release must be audited");
        assertTrue(service.contains("REVIEW_QUEUE_APPROVE"), "queue approve must be audited");
        assertTrue(service.contains("REVIEW_QUEUE_REJECT"), "queue reject must be audited");
        assertTrue(service.contains("REVIEW_QUEUE_CLOSE"), "queue close must be audited");
        assertTrue(service.contains("RiskConfirmation.requireCritical(note, confirmationPhrase)"),
                "terminal queue actions must require a note and CONFIRM phrase on the backend");
        assertTrue(service.contains("OPEN_STATUSES"), "resolved queue items must not be handled repeatedly");

        assertTrue(controller.contains("@RequestMapping(\"/api/v1/admin/review-queue\")"), "review queue controller must expose the planned backend API");
        for (String path : new String[]{"@GetMapping", "@GetMapping(\"/status\")", "@PostMapping", "@PostMapping(\"/{id}/claim\")",
                "@PostMapping(\"/{id}/release\")", "@PostMapping(\"/{id}/approve\")", "@PostMapping(\"/{id}/reject\")", "@PostMapping(\"/{id}/close\")"}) {
            assertTrue(controller.contains(path), "review queue controller must expose " + path);
        }
        assertTrue(service.contains("AdminPermissionService.ROLE_CONTENT_MODERATOR"), "review queue API must require content-moderator scope");
        assertTrue(controller.contains("UserContext.require()"), "review queue API must require a logged-in operator");

        assertTrue(createCmd.contains("class ReviewQueueCreateCmd"), "queue creation must use a typed DTO");
        assertTrue(createCmd.contains("private String sourceType"), "queue creation DTO must capture source type");
        assertTrue(createCmd.contains("private String title"), "queue creation DTO must capture title");
        assertTrue(actionCmd.contains("class ReviewQueueActionCmd"), "queue action must use a typed DTO");
        assertTrue(actionCmd.contains("private String note"), "queue action DTO must capture operator note");
        assertTrue(actionCmd.contains("private String confirmationPhrase"), "queue action DTO must capture critical confirmation phrase");

        String migrationCheck = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/db/MigrationCheckService.java");
        String schemaScript = read("../scripts/check-schema-readiness.mjs");
        assertTrue(migrationCheck.contains("idx_review_queue_status_priority"), "schema readiness must check review queue priority index");
        assertTrue(migrationCheck.contains("idx_review_queue_source_status"), "schema readiness must check review queue source/status index");
        assertTrue(migrationCheck.contains("idx_review_queue_assignee_status"), "schema readiness must check review queue assignee/status index");
        assertTrue(migrationCheck.contains("idx_review_queue_risk_status"), "schema readiness must check review queue risk/status index");
        assertTrue(schemaScript.contains("idx_review_queue_status_priority"), "CLI schema gate must check review queue priority index");
        assertTrue(schemaScript.contains("idx_review_queue_source_status"), "CLI schema gate must check review queue source/status index");
        assertTrue(schemaScript.contains("idx_review_queue_assignee_status"), "CLI schema gate must check review queue assignee/status index");
        assertTrue(schemaScript.contains("idx_review_queue_risk_status"), "CLI schema gate must check review queue risk/status index");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
