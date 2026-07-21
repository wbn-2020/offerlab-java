package com.offerlab.community.analytics.collaboration.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicContributionVisibilityGuardTest {

    @Test
    void maintenanceFactsMustJoinOnlyPublicDeliveredResources() throws Exception {
        String mapper = Files.readString(Path.of(
                "src/main/java/com/offerlab/community/analytics/collaboration/infrastructure/persistence/"
                        + "mapper/CollaborationAnalyticsMapper.java"), StandardCharsets.UTF_8);
        int start = mapper.indexOf("'CONTENT_MAINTENANCE_COMPLETED'");
        int end = mapper.indexOf("UNION ALL", start);
        String maintenance = start >= 0 && end > start ? mapper.substring(start, end) : "";

        assertTrue(maintenance.contains("task.delivery_type IN ('POST', 'QUESTION')"));
        assertTrue(maintenance.contains("COALESCE(task.delivery_post_id, task.delivery_ref_id)"));
        assertTrue(maintenance.contains("delivery_post.is_deleted = 0"));
        assertTrue(maintenance.contains("delivery_post.post_status = 1"));
        assertTrue(maintenance.contains("delivery_post.visibility = 1"));
        assertTrue(maintenance.contains("delivery_post.post_type = 13"));
        assertTrue(maintenance.contains("delivery_series.moderation_hidden = 0"));
        assertTrue(maintenance.contains("delivery_series.series_status IN ('OPEN', 'CLOSED')"));
        assertTrue(maintenance.contains("delivery_submission.review_status = 'APPROVED'"));
        assertTrue(maintenance.contains("series_post.is_deleted = 0"));
        assertTrue(maintenance.contains("series_post.post_status = 1"));
        assertTrue(maintenance.contains("series_post.visibility = 1"));
        assertFalse(maintenance.contains("task.delivery_note"));
        assertFalse(maintenance.contains("task.review_note"));
    }
}
