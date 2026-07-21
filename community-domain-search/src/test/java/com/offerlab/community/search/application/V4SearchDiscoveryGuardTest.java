package com.offerlab.community.search.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V4SearchDiscoveryGuardTest {

    @Test
    void publicSearchEndpointMustStayPublicAndNonDemo() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/search/controller/SearchController.java");
        String facade = read("src/main/java/com/offerlab/community/search/application/SearchFacadeImpl.java");
        String indexer = read("src/main/java/com/offerlab/community/search/application/PostSearchIndexer.java");
        String postMapper = read("../community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostMapper.java");

        assertTrue(controller.contains("facade.searchPosts(keyword, company, position, type, domain, sort, cursor, size, false,")
                        && controller.contains("trustFilter).publicView()"),
                "public search endpoint must force includeTestData=false, retain trusted filters, and return publicView");
        assertTrue(controller.contains("@RequestParam(required = false) @Min(1) @Max(5) Integer domain"),
                "public search endpoint must validate the domain filter");
        assertTrue(facade.contains("Map.of(\"term\", Map.of(\"domain\", domain))"),
                "Elasticsearch search must filter by domain");
        assertTrue(indexer.contains("doc.put(\"domain\"")
                        && indexer.contains("props.put(\"domain\", Map.of(\"type\", \"integer\"))"),
                "Elasticsearch documents and mappings must include domain");
        assertTrue(postMapper.contains("<if test=\"domain != null\">")
                        && postMapper.contains("AND e.domain = #{domain}"),
                "MySQL fallback search must filter by the indexed domain without classifying null as TECH");
        assertTrue(facade.contains("filterVisibleSearchResults(items, includeTestData, domain)"),
                "search results must pass public visibility, test-data, and current domain filtering before output");
        assertTrue(facade.contains("visibleSuggestionSources(hits)"),
                "suggestions must validate current visible posts before output");
        assertTrue(facade.contains("emptyHints(degraded, fallbackReason, includeTestData)"),
                "zero-result metadata must expose repair hints without leaking raw event data");
        assertFalse(facade.contains("FALLBACK_HOT"),
                "backend hot searches must not inject static fallback/demo seeds as public trends");
    }

    @Test
    void analyticsAndContentGapContractsMustExposeAggregateSafeFieldsOnly() throws Exception {
        String trackCmd = read("src/main/java/com/offerlab/community/search/api/dto/SearchAnalyticsTrackCmd.java");
        String analyticsDto = read("src/main/java/com/offerlab/community/search/api/dto/SearchAnalyticsDTO.java");
        String analyticsItemDto = read("src/main/java/com/offerlab/community/search/api/dto/SearchAnalyticsItemDTO.java");
        String gapDto = read("src/main/java/com/offerlab/community/search/api/dto/SearchContentGapDTO.java");
        String mapper = read("src/main/java/com/offerlab/community/search/infrastructure/persistence/mapper/SearchAnalyticsMapper.java");
        String analyticsService = read("src/main/java/com/offerlab/community/search/application/SearchAnalyticsService.java");
        String gapService = read("src/main/java/com/offerlab/community/search/application/SearchContentGapService.java");

        String publicDtoSurface = trackCmd + "\n" + analyticsDto + "\n" + analyticsItemDto + "\n" + gapDto;
        for (String allowed : List.of("eventType", "keyword", "company", "target",
                "hotKeywords", "noResultKeywords", "prepClicks", "recommendClicks",
                "count", "noResultCount", "weakResultCount", "minSampleMet", "riskLevel", "sourceRefs")) {
            assertTrue(publicDtoSurface.contains(allowed), "aggregate search contract must retain field: " + allowed);
        }
        for (String forbidden : List.of("uid", "userId", "ipAddress", "clientIp", "deviceId",
                "fingerprint", "rawSession", "rawQuery", "singleSearchTime", "searchDuration")) {
            assertFalse(publicDtoSurface.toLowerCase().contains(forbidden.toLowerCase()),
                    "public analytics/content-gap DTOs must not expose privacy field: " + forbidden);
        }

        for (String method : List.of("topSearchKeywords", "topNoResultKeywords", "topPrepClicks", "topRecommendationClicks")) {
            String block = selectMapperBlock(mapper, method);
            assertTrue(block.contains("COUNT(*) AS count") || block.contains("COUNT(*) AS noResultCount"),
                    method + " must expose aggregate counts only");
            assertTrue(block.contains("GROUP BY keyword") || block.contains("GROUP BY company"),
                    method + " must group raw events before exposure");
            for (String forbiddenSql : List.of(" uid", "user_id", " ip", "device", "fingerprint", "raw_query", "single_search")) {
                assertFalse(block.toLowerCase().contains(forbiddenSql),
                        method + " must not select personal/raw-event field: " + forbiddenSql);
            }
        }

        assertTrue(analyticsService.contains("event.setUid(null)"),
                "analytics persistence must not store public user identifiers");
        assertTrue(analyticsService.contains("looksSensitive(text)") && analyticsService.contains("return null"),
                "analytics persistence must drop sensitive raw terms before insert");
        assertTrue(gapService.contains("fallback") && gapService.contains("demo"),
                "content-gap aggregation must exclude fallback/demo signals from downstream candidates");
        assertTrue(gapService.contains("minSampleMet") && gapService.contains("APPROVED") && gapService.contains("HIGH"),
                "content-gap downstream eligibility must require sample threshold, approval, and non-high risk");
    }

    @Test
    void forbiddenCommercialAndEndorsementCopyMustStayBlocked() throws Exception {
        String publicFilter = read("../community-domain-post/src/main/java/com/offerlab/community/post/api/PublicContentFilter.java");
        String facade = read("src/main/java/com/offerlab/community/search/application/SearchFacadeImpl.java");
        String gapService = read("src/main/java/com/offerlab/community/search/application/SearchContentGapService.java");

        for (String marker : List.of(
                "广告",
                "广告投放",
                "付费曝光",
                "付费置顶",
                "收益",
                "官方背书",
                "保证有效"
        )) {
            assertTrue(publicFilter.contains(marker),
                    "PublicContentFilter must keep blocking V4 red-line copy marker: " + marker);
        }
        for (String source : List.of(facade, gapService)) {
            for (String forbidden : List.of("广告", "竞价", "付费置顶", "保证曝光", "保证精选", "收益", "官方背书", "私人 AI 教练", "私人AI教练")) {
                assertFalse(source.contains(forbidden),
                        "search discovery backend surfaces must not contain red-line copy: " + forbidden);
            }
        }
    }

    private static String selectMapperBlock(String source, String methodName) {
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
