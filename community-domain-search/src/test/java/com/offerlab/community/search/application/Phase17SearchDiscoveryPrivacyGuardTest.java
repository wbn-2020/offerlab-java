package com.offerlab.community.search.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase17SearchDiscoveryPrivacyGuardTest {

    @Test
    void publicSearchAndSuggestionsMustStayCurrentStateVisible() throws Exception {
        String facade = read("src/main/java/com/offerlab/community/search/application/SearchFacadeImpl.java");

        assertTrue(facade.contains("filterVisibleSearchResults(items, includeTestData)"),
                "Elasticsearch hits must be reloaded through the public post facade before display");
        assertTrue(facade.contains("postFacade.batchGetPosts(esItems.stream()"),
                "Search results must be filtered against current post visibility instead of trusting stale index data");
        assertTrue(facade.contains("Map.of(\"term\", Map.of(\"status\", \"published\"))"),
                "Elasticsearch search and suggestion queries must only ask for published documents");
        assertTrue(facade.contains("Map.of(\"term\", Map.of(\"visibility\", 1))"),
                "Elasticsearch search and suggestion queries must only ask for public documents");
        assertTrue(facade.contains("visibleSuggestionSources(hits)"),
                "Search suggestions must validate candidate post visibility before extracting terms");
        assertTrue(facade.contains("postFacade.batchGetPosts(sourcesById.keySet(), null, false)"),
                "Suggestions must never include private, deleted, restricted, reviewing, or violation posts");
        assertTrue(facade.contains("filterVisibleSearchResults(items, includeTestData);"),
                "MySQL fallback search must also pass through current visibility filtering");
    }

    @Test
    void hotTermsAndSuggestionsMustExcludeSyntheticUnsafeAndCommercialCopy() throws Exception {
        String facade = read("src/main/java/com/offerlab/community/search/application/SearchFacadeImpl.java");
        String publicFilter = read("../community-domain-post/src/main/java/com/offerlab/community/post/api/PublicContentFilter.java");

        assertTrue(facade.contains("PublicContentFilter.isSyntheticText(name)")
                        && facade.contains("PublicContentFilter.isUnsafeSuggestionText(name)"),
                "Hot keywords must remove synthetic and unsafe terms before display");
        assertTrue(facade.contains("PublicContentFilter.isSyntheticText(source.path(\"title\")")
                        && facade.contains("PublicContentFilter.isUnsafeSuggestionText(source.path(\"title\")"),
                "Suggestions must remove synthetic and unsafe source text before display");
        assertFalse(facade.matches("(?s).*List\\.of\\([^)]*(sponsor|paid|member|ad|vip)[^)]*\\).*"),
                "Search hot keywords must not inject commercial-looking fallback trends");
        for (String marker : List.of(
                "official endorsement",
                "platform guarantee",
                "authority certification",
                "limited-time purchase",
                "member exclusive",
                "sponsored recommendation",
                "paid pin"
        )) {
            assertTrue(publicFilter.contains(marker),
                    "PublicContentFilter must keep blocking forbidden copy marker: " + marker);
        }
    }

    @Test
    void publicSearchAnalyticsMustExposeAggregatesOnly() throws Exception {
        String dto = read("src/main/java/com/offerlab/community/search/api/dto/SearchAnalyticsDTO.java");
        String itemDto = read("src/main/java/com/offerlab/community/search/api/dto/SearchAnalyticsItemDTO.java");
        String mapper = read("src/main/java/com/offerlab/community/search/infrastructure/persistence/mapper/SearchAnalyticsMapper.java");
        String service = read("src/main/java/com/offerlab/community/search/application/SearchAnalyticsService.java");

        String publicDtoSurface = dto + "\n" + itemDto;
        for (String forbidden : List.of("uid", "userId", "session", "ip", "device", "fingerprint", "rawQuery")) {
            assertFalse(publicDtoSurface.toLowerCase().contains(forbidden.toLowerCase()),
                    "Search analytics DTOs must not expose personal search history field: " + forbidden);
        }
        for (String method : List.of("topSearchKeywords", "topNoResultKeywords", "topPrepClicks", "topRecommendationClicks")) {
            String block = selectBlockFor(mapper, method);
            assertTrue(block.contains("COUNT(*) AS count") || block.contains("COUNT(*) AS noResultCount"),
                    method + " must return aggregate counters, not raw events");
            assertTrue(block.contains("GROUP BY keyword") || block.contains("GROUP BY company"),
                    method + " must group analytics before exposing them");
            for (String forbidden : List.of(" uid", " user_id", "session", " ip", "device", "fingerprint")) {
                assertFalse(block.toLowerCase().contains(forbidden),
                        method + " must not select personal analytics field: " + forbidden);
            }
        }
        assertTrue(service.contains("SearchAnalyticsDTO.builder()"),
                "SearchAnalyticsService.summary must build the aggregate DTO surface");
        assertTrue(service.contains("event.setUid(null)"),
                "Search analytics events must not persist public user identifiers for search history");
    }

    private static String selectBlockFor(String source, String methodName) {
        int signature = source.indexOf(methodName + "(");
        assertTrue(signature > 0, "expected mapper method not found: " + methodName);
        int selectStart = source.lastIndexOf("@Select", signature);
        int nextMethod = source.indexOf("List<Map<String, Object>>", signature + methodName.length());
        if (nextMethod < 0) {
            nextMethod = source.length();
        }
        return source.substring(selectStart, nextMethod);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
