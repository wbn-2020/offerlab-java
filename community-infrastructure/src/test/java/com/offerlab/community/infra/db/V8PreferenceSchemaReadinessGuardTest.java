package com.offerlab.community.infra.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class V8PreferenceSchemaReadinessGuardTest {

    @Test
    void userSubscriptionAndFeedFeedbackTablesMustBeInOverallReadiness() throws Exception {
        String service = read(
                "src/main/java/com/offerlab/community/infra/db/MigrationCheckService.java");
        String script = read("../scripts/check-schema-readiness.mjs");

        assertTrue(service.contains("\"t_user_subscription_preference\""));
        assertTrue(service.contains("userSubscriptionPreferenceReady()"));
        assertTrue(service.contains("schema:user-subscription-preference-definitions"));
        assertTrue(service.contains("20260719_user_subscription_preference.sql"));

        assertTrue(service.contains("\"t_feed_feedback_preference\""));
        assertTrue(service.contains("feedFeedbackControlReady()"));
        assertTrue(service.contains("chk_feed_feedback_action"));
        assertTrue(service.contains("chk_feed_feedback_target_type"));
        assertTrue(service.contains("schema:feed-feedback-control-definitions"));
        assertTrue(service.contains("20260719_feed_feedback_control.sql"));

        assertTrue(script.contains("checkConstraintDefinition('t_feed_feedback_preference'"));
        assertTrue(script.contains("uk_feed_feedback_uid_post"));
        assertTrue(script.contains("idx_feed_feedback_uid_action_active"));
        assertTrue(script.contains("idx_feed_feedback_uid_target_active"));
        assertTrue(script.contains("idx_feed_feedback_uid_cursor"));
        assertTrue(script.contains("20260719_feed_feedback_control.sql"));
        assertTrue(script.contains("20260719_user_subscription_preference.sql"));

        assertTrue(service.contains("\"t_projection_reconcile_request\""));
        assertTrue(service.contains("projectionReconcileRequestReady()"));
        assertTrue(service.contains("projectionReconcileRequestDefinitionsReady"));
        assertTrue(service.contains("schema:projection-reconcile-request-definitions"));
        assertTrue(service.contains("20260719_projection_reconcile_request.sql"));

        assertTrue(script.contains("t_projection_reconcile_request"));
        assertTrue(script.contains("uk_projection_reconcile_resource"));
        assertTrue(script.contains("idx_projection_reconcile_operator_time"));
        assertTrue(script.contains("idx_projection_reconcile_type_time"));
        assertTrue(script.contains("chk_projection_reconcile_status"));
        assertTrue(script.contains("20260719_projection_reconcile_request.sql"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
