package com.offerlab.community.search.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchContentGapGuardTest {

    @Test
    void searchContentGapDtoMustExposeOnlyAggregateSafeFields() throws Exception {
        String dto = read("src/main/java/com/offerlab/community/search/api/dto/SearchContentGapDTO.java");

        for (String required : List.of(
                "gapId", "keyword", "clusterId", "reasonText", "windowDays", "searchCount",
                "noResultCount", "weakResultCount", "minSampleMet", "riskLevel", "targetStage",
                "status", "source", "sourceRefs", "createdFrom", "lastSeenAt"
        )) {
            assertTrue(dto.contains(required), "SearchContentGap must expose required field: " + required);
        }
        for (String requiredValue : List.of(
                "LOW", "MEDIUM", "HIGH",
                "CANDIDATE", "APPROVED", "IGNORED", "REVIEW_REQUIRED",
                "workspace", "editor", "topic", "knowledge",
                "no_result", "weak_result", "hot_keyword", "manual_review"
        )) {
            assertTrue(dto.contains(requiredValue), "SearchContentGap must expose required enum value: " + requiredValue);
        }
        for (String forbidden : List.of("uid", "userId", "ip", "device", "fingerprint", "singleSearchTime",
                "rawQuery", "privateTraining")) {
            assertFalse(dto.toLowerCase().contains(forbidden.toLowerCase()),
                    "SearchContentGap must not expose personal or raw-event field: " + forbidden);
        }
    }

    @Test
    void searchContentGapServiceMustFilterUnsafeSourcesAndRequireSamplesForDownstream() throws Exception {
        String service = read("src/main/java/com/offerlab/community/search/application/SearchContentGapService.java");
        String analytics = read("src/main/java/com/offerlab/community/search/application/SearchAnalyticsService.java");
        String controller = read("src/main/java/com/offerlab/community/search/controller/OpsController.java");

        assertTrue(service.contains("MIN_SAMPLE_COUNT"), "gap aggregation must define an explicit minimum sample size");
        assertTrue(service.contains("includeTestData") && service.contains("false"),
                "gap aggregation must keep includeTestData default-off");
        assertTrue(service.contains("fallback") && service.contains("demo"),
                "fallback/demo signals must be excluded from real gaps");
        assertTrue(service.contains("minSampleMet") && service.contains("APPROVED") && service.contains("HIGH"),
                "downstream gaps must require APPROVED, minSampleMet=true, and non-HIGH risk");
        assertTrue(service.contains("EMAIL_PATTERN") && service.contains("PHONE_PATTERN")
                        && service.contains("URL_PATTERN") && service.contains("JWT_PATTERN")
                        && service.contains("TOKEN_PATTERN"),
                "gap aggregation must reuse P0 sensitive query filters");
        assertTrue(service.contains("sourceRefs") && service.contains("search"),
                "gap output must preserve aggregate search source refs only");
        assertFalse(service.toLowerCase().contains("usercontext"),
                "gap aggregation must not read user identity");

        assertTrue(analytics.contains("contentGaps(days, limit, false)") || analytics.contains("contentGaps(days, limit, includeTestData)"),
                "analytics service must expose content gap aggregation with default includeTestData=false");
        assertTrue(controller.contains("/search/content-gaps") && controller.contains("includeTestData"),
                "ops controller must expose an explicit content gap endpoint and test-data switch");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
