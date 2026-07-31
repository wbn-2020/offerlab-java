package com.offerlab.community.post.collaboration.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollaborationV7ReadGuardTest {

    @Test
    void v7ReadControllerMustKeepPublicAndAuthenticatedBoundaries() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/post/collaboration/controller/CollaborationV7ReadController.java");

        assertTrue(controller.contains("class CollaborationV7ReadController"));
        assertTrue(controller.contains("@GetMapping(\"/actions/summary\")"));
        assertTrue(controller.contains("@GetMapping(\"/actions\")"));
        assertTrue(controller.contains("@GetMapping(\"/needs/{needId}/delivery-candidates\")"));
        assertTrue(controller.contains("@GetMapping(\"/needs/discovery\")"));
        assertTrue(controller.contains("@PublicApi"));
        assertTrue(controller.contains("actionQueryService.summary(UserContext.require())"));
        assertTrue(controller.contains("UserContext.require(), actionType, cursor, size"));
        assertTrue(controller.contains("needId, UserContext.require(), resolutionType"));
        assertTrue(controller.contains("UserContext.get()"));
        assertTrue(controller.contains("@RateLimit"));
    }

    @Test
    void actionReadMustBeServerFilteredAndStable() throws Exception {
        String mapper = read("src/main/java/com/offerlab/community/post/collaboration/infrastructure/persistence/CollaborationActionQueryMapper.java");
        String service = read("src/main/java/com/offerlab/community/post/collaboration/application/CollaborationActionQueryService.java");
        String actionTypeSource = read("src/main/java/com/offerlab/community/post/collaboration/api/CollaborationActionType.java");

        for (String type : new String[]{
                "NEED_SUBMIT", "NEED_REVISE", "NEED_REVIEW", "NEED_STALLED", "OFFICE_HOUR_REVIEW"
        }) {
            assertTrue(mapper.contains("'" + type + "'"), "missing action type: " + type);
            assertTrue(actionTypeSource.contains(type), "missing action model type: " + type);
        }
        assertTrue(mapper.contains("n.moderation_hidden = 0"));
        assertTrue(mapper.contains("r.moderation_hidden = 0"));
        assertTrue(mapper.contains("h.moderation_hidden = 0"));
        assertTrue(mapper.contains("ORDER BY updatedAt DESC, sourceId DESC, actionType ASC"));
        assertTrue(mapper.contains("n.claimed_by_uid = #{uid}"));
        assertTrue(mapper.contains("n.creator_uid = #{uid}"));
        assertTrue(mapper.contains("h.host_uid = #{uid}"));
        assertTrue(service.contains("mapper.countActions(uid)"));
        assertTrue(service.contains("ActionCursor"));
    }

    @Test
    void discoveryMustUsePublicFiltersStableSortsAndDeterministicReasons() throws Exception {
        String mapper = read("src/main/java/com/offerlab/community/post/collaboration/infrastructure/persistence/NeedDiscoveryMapper.java");
        String service = read("src/main/java/com/offerlab/community/post/collaboration/application/NeedDiscoveryService.java");

        assertTrue(mapper.contains("n.moderation_hidden = 0"));
        assertTrue(mapper.contains("n.domain = #{domain}"));
        assertTrue(mapper.contains("n.need_status = #{status}"));
        assertTrue(mapper.contains("n.content_format = #{contentFormat}"));
        assertTrue(mapper.contains("n.source_type = #{sourceType}"));
        assertTrue(mapper.contains("LOWER(CONCAT_WS(' ', n.title, n.description))"));
        assertTrue(mapper.contains("ORDER BY stalled DESC, n.update_time DESC, n.id DESC"));
        assertTrue(mapper.contains("ORDER BY n.update_time DESC, n.id DESC"));
        assertTrue(mapper.contains("ORDER BY n.create_time DESC, n.id DESC"));
        for (String reason : new String[]{
                "FILTER_DOMAIN_MATCH",
                "FILTER_CONTENT_FORMAT_MATCH",
                "FILTER_SOURCE_TYPE_MATCH",
                "PUBLIC_DOMAIN_CONTRIBUTION_MATCH",
                "PUBLIC_CONTENT_FORMAT_CONTRIBUTION_MATCH"
        }) {
            assertTrue(service.contains(reason), "missing deterministic reason: " + reason);
        }
        assertFalse(service.contains("SearchContentGapFulfillmentListener"));
        assertFalse(mapper.contains("ORDER BY RAND"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
