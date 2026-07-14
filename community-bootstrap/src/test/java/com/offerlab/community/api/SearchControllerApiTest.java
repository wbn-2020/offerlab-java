package com.offerlab.community.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.infra.mq.outbox.OutboxMessage;
import com.offerlab.community.infra.mq.outbox.OutboxMessageMapper;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.search.api.SearchFacade;
import com.offerlab.community.search.api.dto.SearchStatusDTO;
import com.offerlab.community.search.application.PostSearchIndexer;
import com.offerlab.community.search.application.SearchAnalyticsService;
import com.offerlab.community.search.application.SearchIndexRetryService;
import com.offerlab.community.search.controller.SearchController;
import com.offerlab.community.search.infrastructure.persistence.po.SearchIndexRetryTaskPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SearchControllerApiTest {
    @Mock
    private SearchFacade searchFacade;
    @Mock
    private PostSearchIndexer indexer;
    @Mock
    private SearchAnalyticsService searchAnalyticsService;
    @Mock
    private PostFacade postFacade;
    @Mock
    private ElasticsearchHttpClient elasticsearch;
    @Mock
    private SearchIndexRetryService searchIndexRetryService;
    @Mock
    private OutboxMessageMapper outboxMessageMapper;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new SearchController(
                searchFacade,
                searchAnalyticsService,
                postFacade,
                indexer
        ), jwtService);
    }

    @Test
    void publishStatusKeepsInternalRetryErrorsOutOfPublicContract() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        PostBriefDTO post = PostBriefDTO.builder()
                .id(42L)
                .postType(10)
                .title("Public publish status")
                .summary("regular post")
                .createTime(LocalDateTime.now())
                .build();
        SearchIndexRetryTaskPO retryTask = new SearchIndexRetryTaskPO();
        retryTask.setId(9001L);
        retryTask.setPostId(42L);
        retryTask.setOperation("UPSERT");
        retryTask.setTaskStatus(2);
        retryTask.setRetryCount(3);
        retryTask.setLastError("http://internal-es:9200/_bulk token=secret");
        retryTask.setUpdateTime(LocalDateTime.parse("2026-06-14T10:15:00"));

        when(postFacade.batchGetPosts(List.of(42L))).thenReturn(Map.of(42L, post));
        when(searchFacade.searchPosts(eq("42"), isNull(), isNull(), isNull(), eq("relevance"), isNull(), eq(5), eq(false)))
                .thenReturn(PageResult.of(List.of(post), null, false)
                        .withMetadata("mysql", true, "elasticsearch_unavailable", 200)
                        .withDiagnostics(Map.of(
                                "hitExplanation", "mysql_fallback_no_hit_explanation",
                                "visibleHits", 1
                        )));

        mvc.perform(get("/api/v1/search/posts/42/publish-status")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.database.publiclyVisible").value(true))
                .andExpect(jsonPath("$.data.search.visible").value(true))
                .andExpect(jsonPath("$.data.search.source").value("mysql"))
                .andExpect(jsonPath("$.data.search.degraded").value(true))
                .andExpect(jsonPath("$.data.search.fallbackReason").value("elasticsearch_unavailable"))
                .andExpect(jsonPath("$.data.search.diagnostics.hitExplanation").value("mysql_fallback_no_hit_explanation"))
                .andExpect(jsonPath("$.data.search.diagnostics.visibleHits").value(1))
                .andExpect(jsonPath("$.data.ready").value(true));

        verifyNoInteractions(indexer);
    }

    @Test
    void publicStatusPreservesTheCompleteIndexerContract() throws Exception {
        when(indexer.publicStatus()).thenReturn(SearchStatusDTO.builder()
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
                .build());

        mvc.perform(get("/api/v1/search/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DEGRADED"))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.indexName").value("public-posts"))
                .andExpect(jsonPath("$.data.indexExists").value(false))
                .andExpect(jsonPath("$.data.indexReady").value(false))
                .andExpect(jsonPath("$.data.publicSearchAvailable").value(true))
                .andExpect(jsonPath("$.data.publicSearchDegraded").value(true))
                .andExpect(jsonPath("$.data.publicSearchSource").value("mysql"))
                .andExpect(jsonPath("$.data.dbFallbackAvailable").value(true))
                .andExpect(jsonPath("$.data.fallbackSource").value("mysql"))
                .andExpect(jsonPath("$.data.fallbackMode").value("tag_governance"))
                .andExpect(jsonPath("$.data.fallbackScanLimit").value(200))
                .andExpect(jsonPath("$.data.fallbackSchemaReady").value(true))
                .andExpect(jsonPath("$.data.diagnosticMessage").value("public_search_using_database_fallback"))
                .andExpect(jsonPath("$.data.action").value("Restore Elasticsearch"));
    }

    @Test
    void postSearchPassesDomainFilterToFacade() throws Exception {
        when(searchFacade.searchPosts(
                eq("租房"),
                isNull(),
                isNull(),
                isNull(),
                eq(4),
                eq("latest"),
                isNull(),
                eq(20),
                eq(false)))
                .thenReturn(PageResult.empty());

        mvc.perform(get("/api/v1/search/posts")
                        .param("q", "租房")
                        .param("domain", "4")
                        .param("sort", "latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(searchFacade).searchPosts(
                "租房", null, null, null, 4, "latest", null, 20, false);
    }

    @Test
    void postSearchRejectsUnknownDomainBeforeFacade() throws Exception {
        mvc.perform(get("/api/v1/search/posts")
                        .param("domain", "999"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchFacade);
    }
}
