package com.offerlab.community.analytics.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatorCurationFeedbackGuardTest {

    @Test
    void creatorCurationFeedbackMustExposeOnlyPublicGovernedInclusionFeedback() throws Exception {
        String dto = read("src/main/java/com/offerlab/community/analytics/api/dto/CreatorCurationFeedbackDTO.java");
        String service = read("src/main/java/com/offerlab/community/analytics/application/CreatorCurationFeedbackService.java");
        String controller = read("src/main/java/com/offerlab/community/analytics/controller/CreatorGrowthController.java");
        String postService = read("../community-domain-post/src/main/java/com/offerlab/community/post/application/OperationCurationService.java");
        String facade = read("../community-domain-post/src/main/java/com/offerlab/community/post/api/CreatorCurationFeedbackFacade.java");
        String growthEventService = read("src/main/java/com/offerlab/community/analytics/application/GrowthEventService.java");
        String growthEventListener = read("src/main/java/com/offerlab/community/analytics/application/GrowthEventListener.java");

        for (String field : new String[] {
                "contentId",
                "contentTitle",
                "placementLabel",
                "reasonText",
                "href",
                "triggeredAt",
                "status",
                "source",
                "publicMetrics"
        }) {
            assertTrue(dto.contains(field), "curation feedback DTO must expose " + field);
        }
        assertTrue(dto.contains("CreatorCurationFeedbackSummaryDTO"),
                "summary DTO must be nested with the item DTO");
        assertTrue(controller.contains("@GetMapping(\"/curation-feedback\")"),
                "creator growth must expose /api/v1/creator-growth/curation-feedback");
        assertTrue(controller.contains("creatorCurationFeedbackService.summary(UserContext.require())"),
                "curation feedback route must be authenticated and creator-scoped");

        assertTrue(service.contains("CreatorCurationFeedbackFacade"), "service must reuse the post-domain curation feedback facade");
        assertTrue(facade.contains("listCreatorCurationFeedback"),
                "post domain must provide a creator-visible feedback read model");
        assertTrue(postService.contains("PublicContentFilter.isDistributablePost"),
                "creator feedback must inherit public/governed content filtering");
        assertTrue(postService.contains("post.getAuthorId()") && postService.contains("authorUid"),
                "creator feedback must only expose the current creator's own public content");
        assertTrue(postService.contains("isRealRemotePlacement")
                        && postService.contains("fallback")
                        && postService.contains("demo")
                        && postService.contains("fixture"),
                "fallback/demo/fixture sources must be excluded from real creator feedback");
        assertTrue(postService.contains("post.getAnonymous()")
                        && postService.contains("!Boolean.TRUE.equals(post.getAnonymous())"),
                "anonymous content must not create author-visible inclusion feedback");
        assertTrue(service.contains("NO_FEEDBACK"),
                "empty creator feedback reads must be a stable no-feedback state instead of an offline/demo fallback");
        assertTrue(growthEventService.contains("OPERATION_CURATION_SELECTED"),
                "creator inclusion events must be part of the trusted growth event taxonomy");
        assertTrue(growthEventListener.contains("onOperationCurationSelected(OperationCurationSelectedEvent event)")
                        && growthEventListener.contains("GrowthEventService.OPERATION_CURATION_SELECTED"),
                "creator inclusion events must feed creator growth event analytics");

        String combined = (dto + service + controller + postService + facade + growthEventService + growthEventListener).toLowerCase();
        for (String forbidden : new String[] {
                "codecoachai",
                "mockinterview",
                "resumematch",
                "applicationtask",
                "private training",
                "points",
                "shop",
                "settlement",
                "revenue share"
        }) {
            assertFalse(combined.contains(forbidden),
                    "OfferLab curation feedback must not introduce private training or commercial capability: " + forbidden);
        }
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
