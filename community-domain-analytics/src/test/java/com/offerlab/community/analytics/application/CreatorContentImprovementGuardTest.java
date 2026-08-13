package com.offerlab.community.analytics.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatorContentImprovementGuardTest {

    @Test
    void creatorContentImprovementMustUseReadContractsAndExposeOpaqueDtos() throws Exception {
        String analyticsPom = read("pom.xml");
        String service = read("src/main/java/com/offerlab/community/analytics/application/CreatorContentImprovementService.java");
        String coordinator = read("src/main/java/com/offerlab/community/analytics/application/RevisionAwareQualitySignalCoordinator.java");
        String dto = read("src/main/java/com/offerlab/community/analytics/api/dto/CreatorContentImprovementSignalsDTO.java");
        String maintenanceFacade = read("../community-domain-post/src/main/java/com/offerlab/community/post/api/ContentMaintenanceTaskReadFacade.java");
        String maintenanceMapper = read("../community-domain-post/src/main/java/com/offerlab/community/post/collaboration/infrastructure/persistence/ContentMaintenanceTaskMapper.java");

        assertTrue(analyticsPom.contains("<artifactId>community-domain-feed-api</artifactId>"),
                "analytics must depend on the Feed API contract rather than Feed implementation details");
        assertFalse(service.contains("FeedFeedbackStore") || service.contains("FeedFeedbackPreferenceMapper"),
                "analytics must not read Feed persistence implementation details directly");
        assertTrue(service.contains("RevisionAwareQualitySignalCoordinator"),
                "creator signals must use the shared revision-aware coordinator");
        assertTrue(service.contains("creatorContentRevisionBoundaryReady"),
                "creator signals must degrade before V32 reads when the revision-boundary schema is unavailable");
        assertTrue(coordinator.contains("findRevisionAwareActiveQualitySignals")
                        && coordinator.contains("PostContentRevisionQueryFacade")
                        && coordinator.contains("Instant"),
                "revision-aware signals must coordinate Post windows and Feed aggregates with Instant");
        assertFalse(coordinator.contains("latestUpdatedAt") || coordinator.contains("update_time")
                        || coordinator.contains("PostVersionHistoryMapper"),
                "Analytics must not infer revision windows from persistence timestamps");
        assertTrue(service.contains("ContentMaintenanceTaskReadFacade"),
                "analytics must use the stable post maintenance read contract");
        assertTrue(maintenanceFacade.contains("findActivePublicSourcePostIds"),
                "post maintenance read contract must expose only matched source post ids");
        assertTrue(maintenanceMapper.contains("source_post.author_id = #{authorUid}"),
                "maintenance existence query must be scoped to the author");
        assertTrue(maintenanceMapper.contains("source_post.visibility = 1")
                        && maintenanceMapper.contains("task.task_status IN ('OPEN', 'CLAIMED', 'SUBMITTED')"),
                "maintenance existence query must require public source content and an active task");
        for (String forbidden : new String[]{
                "distinctReaderCount",
                "priorRevisionDistinctReaderCount",
                "currentRevisionDistinctReaderCount",
                "effectiveRevisionAt",
                "revisionToken",
                "readerUid",
                "feedbackReason",
                "action",
                "taskId",
                "assigneeUid"
        }) {
            assertFalse(dto.contains(forbidden),
                    "creator quality DTO must not disclose " + forbidden);
        }
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }
}
