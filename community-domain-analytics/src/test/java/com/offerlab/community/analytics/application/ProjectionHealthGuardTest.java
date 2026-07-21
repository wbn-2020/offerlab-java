package com.offerlab.community.analytics.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectionHealthGuardTest {

    @Test
    void projectionHealthEndpointsAreProtectedAndBounded() throws Exception {
        String controller = read(
                "src/main/java/com/offerlab/community/analytics/controller/ProjectionHealthController.java");
        String service = read(
                "src/main/java/com/offerlab/community/analytics/application/ProjectionHealthService.java");
        String mapper = read(
                "src/main/java/com/offerlab/community/analytics/infrastructure/persistence/mapper/ProjectionHealthMapper.java");

        assertTrue(controller.contains("@RequestMapping(\"/api/v1/admin/community-health/projections\")"));
        assertTrue(controller.contains("@RateLimit"));
        assertTrue(controller.contains("UserContext.require()"));
        assertTrue(controller.contains("@Valid @RequestBody ProjectionReconcileCmd"));
        assertTrue(service.contains("AdminPermissionService.ROLE_OPS"));
        assertTrue(service.contains("requireWritable"));
        assertTrue(service.contains("idempotencyKey"));
        assertTrue(service.contains("reserveReconciliationRequest"));
        assertTrue(service.contains("DRY_RUN_COMPLETED"));
        assertTrue(service.contains("diagnosis-only"));
        assertTrue(service.contains("\"REWARD_INBOX_DELIVERY\""));
        assertTrue(service.contains("REWARD_INBOX_SLA_MINUTES"));
        assertTrue(service.contains("previewOverdueRewardInbox"));
        assertTrue(service.contains("reconcileOverdueRewardInbox"));
        assertTrue(service.contains("DIAGNOSTIC_SCAN_AND_DIFFERENCE_RECORDING"));
        assertTrue(service.contains("DIAGNOSTIC_SCAN_COMPLETED"));
        assertTrue(service.contains("\"KNOWLEDGE_LIFECYCLE\""));
        assertTrue(service.contains("SOURCE_ERROR"));
        assertTrue(service.contains(".repairMode(\"DIAGNOSIS_ONLY\")"));
        assertTrue(mapper.contains("LIMIT #{cap}"));
        assertTrue(mapper.contains("ORDER BY"));
        assertTrue(mapper.contains("t_admin_audit_log"));
        assertTrue(mapper.contains("t_incentive_reward_inbox"));
        assertTrue(mapper.contains("inbox_status = 'PENDING'"));
        assertTrue(mapper.contains("listOverdueRewardInboxDeliveryIssues"));
        assertTrue(mapper.contains("selectPendingSuggestionHealth"));
        assertTrue(mapper.contains("resolution = 'PENDING'"));
        assertTrue(mapper.contains("selectBrokenReferenceHealth"));
        assertTrue(mapper.contains("reference_status = 'BROKEN'"));
        assertTrue(mapper.contains("selectPendingKnowledgeRelationHealth"));
        assertTrue(mapper.contains("review_status = 'PENDING'"));
        assertTrue(mapper.contains("selectInvalidPublicRelationTargetHealth"));
        assertTrue(mapper.contains("visibility_status = 'VISIBLE'"));
        assertTrue(mapper.contains("selectDueOutcomeRevisitHealth"));
        assertTrue(mapper.contains("follow_up_at <= CURRENT_TIMESTAMP(3)"));
        assertTrue(mapper.contains("INSERT IGNORE INTO t_projection_reconcile_request"));
        assertTrue(mapper.contains("FOR UPDATE"));
        assertFalse(mapper.contains("UPDATE t_post_reference"));
        assertFalse(mapper.contains("UPDATE t_post_knowledge_relation"));
        assertFalse(mapper.contains("UPDATE t_int_post_outcome"));

        String combined = (controller + service + mapper).toLowerCase();
        assertFalse(combined.contains("searchcontentgapfulfillmentlistener"));
        assertFalse(combined.contains("leaderboard"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
