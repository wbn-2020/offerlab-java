package com.offerlab.community.search.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.search.api.SearchFacade;
import com.offerlab.community.search.api.dto.PublicSearchStatusDTO;
import com.offerlab.community.search.api.dto.SearchStatusDTO;
import com.offerlab.community.search.application.PostSearchIndexer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchControllerStatusTest {

    @Test
    void statusMapsIndexerStateToTheSafePublicContract() {
        SearchStatusDTO expected = SearchStatusDTO.builder()
                .status("DEGRADED")
                .enabled(true)
                .available(false)
                .indexName("public-posts")
                .indexExists(false)
                .indexReady(false)
                .publicSearchAvailable(true)
                .publicSearchDegraded(false)
                .publicSearchSource("mysql")
                .dbFallbackAvailable(true)
                .fallbackSource("mysql")
                .fallbackMode("tag_governance")
                .fallbackScanLimit(200)
                .fallbackSchemaReady(true)
                .message("Database fallback is active")
                .diagnosticMessage("public_search_using_database_fallback")
                .action("Restore Elasticsearch")
                .build();
        PostSearchIndexer indexer = new PostSearchIndexer(null, null, null, null, null, null, null) {
            @Override
            public SearchStatusDTO publicStatus() {
                return expected;
            }
        };
        SearchController controller = new SearchController(null, null, null, indexer);

        Result<PublicSearchStatusDTO> result = controller.status(null);

        assertTrue(result.getData().isAvailable());
        assertFalse(result.getData().isDegraded());
        assertEquals("搜索状态正常，只返回公开且符合当前条件的内容。", result.getData().getMessage());
        assertEquals(null, result.getData().getAction());
        for (String forbidden : List.of(
                "status", "enabled", "indexName", "indexExists", "indexReady",
                "publicSearchSource", "dbFallbackAvailable", "fallbackSource",
                "fallbackMode", "fallbackScanLimit", "fallbackSchemaReady", "diagnosticMessage"
        )) {
            assertFalse(Arrays.stream(PublicSearchStatusDTO.class.getDeclaredFields())
                    .map(Field::getName)
                    .anyMatch(forbidden::equals),
                    "public status must not expose " + forbidden);
        }
    }

    @Test
    void publishStatusExposesOnlyPublicVisibilitySignals() {
        PostBriefDTO post = PostBriefDTO.builder().id(42L).title("Searchable post").build();
        Map<String, Object> diagnostics = Map.of(
                "hitExplanation", "mysql_fallback_no_hit_explanation",
                "visibleHits", 1
        );
        PageResult<PostBriefDTO> recall = PageResult.of(List.of(post), null, false)
                .withMetadata("mysql", true, "elasticsearch_unavailable", 200)
                .withDiagnostics(diagnostics);
        SearchFacade searchFacade = proxy(SearchFacade.class, Map.of("searchPosts", recall));
        PostFacade postFacade = proxy(PostFacade.class, Map.of("batchGetPosts", Map.of(42L, post)));
        SearchController controller = new SearchController(searchFacade, null, postFacade, null);

        Map<String, Object> data = controller.publishStatus(42L, null).getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> search = (Map<String, Object>) data.get("search");

        assertEquals(42L, data.get("postId"));
        assertTrue(Boolean.TRUE.equals(data.get("ready")));
        assertEquals(true, search.get("visible"));
        assertTrue(!search.containsKey("source"));
        assertTrue(!search.containsKey("degraded"));
        assertTrue(!search.containsKey("fallbackReason"));
        assertTrue(!search.containsKey("diagnostics"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Map<String, Object> results) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> results.get(method.getName())
        );
    }
}
