package com.offerlab.community.analytics.collaboration.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollaborationAnalyticsGuardTest {

    @Test
    void funnelAnalyticsMustBeAuthorizedRateLimitedAndDeduplicated() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/analytics/collaboration/controller/CollaborationAnalyticsController.java");
        String service = read("src/main/java/com/offerlab/community/analytics/collaboration/application/CollaborationAnalyticsService.java");
        String dto = read("src/main/java/com/offerlab/community/analytics/collaboration/api/CollaborationFunnelDTO.java");
        String mapper = read("src/main/java/com/offerlab/community/analytics/collaboration/infrastructure/persistence/mapper/CollaborationAnalyticsMapper.java");

        assertTrue(controller.contains("@RequestMapping(\"/api/v1/collaboration/analytics\")"));
        assertTrue(controller.contains("@GetMapping(\"/need-funnel\")"));
        assertTrue(controller.contains("UserContext.require()"));
        assertTrue(controller.contains("@RateLimit"));

        assertTrue(service.contains("AdminPermissionService.ROLE_OPS"));
        assertTrue(service.contains("AdminPermissionService.ROLE_CONTENT_MODERATOR"));
        assertTrue(service.contains("canModerateDomain"));
        assertTrue(service.contains("emptyDenominatorPolicy"));
        assertTrue(service.contains("deduplicationPolicy"));
        assertTrue(service.contains("dataFreshness"));

        assertTrue(mapper.contains("COUNT(DISTINCT"));
        assertTrue(mapper.contains("t_collab_content_need_event"));
        assertTrue(mapper.contains("ROW_NUMBER()"));
        assertTrue(mapper.contains("stalledBefore"));
        assertTrue(mapper.contains("t_collab_content_need_follow"));
        assertTrue(mapper.contains("maintenance_tasks"));
        assertTrue(dto.contains("maintenanceCompletionRate"));

        String combined = (service + mapper + dto).toLowerCase();
        for (String forbidden : new String[]{
                "collaborationcontributionacceptedevent",
                "eventpublisher",
                "rewardledger",
                "leaderboard",
                "insert into",
                "update ",
                "delete "
        }) {
            assertFalse(combined.contains(forbidden),
                    "funnel analytics must remain read-only and independent: " + forbidden);
        }
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
