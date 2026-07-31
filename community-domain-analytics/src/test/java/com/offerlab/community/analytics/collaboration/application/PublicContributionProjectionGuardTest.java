package com.offerlab.community.analytics.collaboration.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicContributionProjectionGuardTest {

    @Test
    void publicContributionProjectionMustBePublicFactOnlyAndReadOnly() throws Exception {
        String factDto = read("src/main/java/com/offerlab/community/analytics/collaboration/api/PublicContributionFactDTO.java");
        String profileDto = read("src/main/java/com/offerlab/community/analytics/collaboration/api/PublicContributionProfileDTO.java");
        String controller = read("src/main/java/com/offerlab/community/analytics/collaboration/controller/PublicContributionController.java");
        String service = read("src/main/java/com/offerlab/community/analytics/collaboration/application/CollaborationAnalyticsService.java");
        String mapper = read("src/main/java/com/offerlab/community/analytics/collaboration/infrastructure/persistence/mapper/CollaborationAnalyticsMapper.java");

        assertTrue(controller.contains("@GetMapping(\"/{uid}/public-contributions\")"));
        assertTrue(controller.contains("@PublicApi"));
        assertTrue(controller.contains("HttpServletRequest"));
        assertTrue(controller.contains("@RateLimit"));
        assertFalse(service.contains("viewerUid"));

        for (String acceptedState : new String[]{"ACCEPTED", "COMPLETED", "APPROVED"}) {
            assertTrue(mapper.contains(acceptedState),
                    "projection must consume persisted accepted state: " + acceptedState);
        }
        assertTrue(mapper.contains("t_collab_content_need_event"));
        assertTrue(mapper.contains("ROW_NUMBER()"));
        assertTrue(mapper.contains("PARTITION BY factType, sourceId"));
        assertTrue(mapper.contains("SELECT DISTINCT"));

        String dto = (factDto + profileDto).toLowerCase();
        for (String privateField : new String[]{
                "note", "detail", "review_note", "reviewnote",
                "governance", "moderationnote", "reward", "rank", "score", "badge"
        }) {
            assertFalse(dto.contains(privateField),
                    "public contribution DTO must not expose private or derived field: " + privateField);
        }

        String combined = (service + mapper).toLowerCase();
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
                    "contribution projection must remain a read-side capability: " + forbidden);
        }
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
