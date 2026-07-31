package com.offerlab.community.search.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.search.api.SearchFacade;
import com.offerlab.community.search.api.dto.SearchStatusDTO;
import com.offerlab.community.search.application.PostSearchIndexer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchControllerStatusTest {

    @Test
    void statusReturnsTheCompletePublicIndexerContractWithoutRebuildingIt() {
        SearchStatusDTO expected = SearchStatusDTO.builder()
                .status("DEGRADED")
                .enabled(true)
                .available(false)
                .indexName("public-posts")
                .indexExists(false)
                .indexReady(false)
                .publicSearchAvailable(true)
                .publicSearchDegraded(true)
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

        Result<SearchStatusDTO> result = controller.status(null);

        assertSame(expected, result.getData());
    }

    @Test
    void publishStatusCarriesPublicSearchFallbackDiagnostics() {
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
        assertEquals("mysql", search.get("source"));
        assertEquals(true, search.get("degraded"));
        assertEquals("elasticsearch_unavailable", search.get("fallbackReason"));
        assertEquals(diagnostics, search.get("diagnostics"));
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
