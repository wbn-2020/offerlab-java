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
                indexer,
                searchAnalyticsService,
                postFacade,
                elasticsearch,
                searchIndexRetryService,
                outboxMessageMapper
        ), jwtService);
    }

    @Test
    void publishStatusKeepsInternalRetryErrorsOutOfPublicContract() throws Exception {
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
        when(postFacade.batchGetPosts(List.of(42L), true)).thenReturn(Map.of(42L, post));
        when(searchFacade.searchPosts(eq("42"), isNull(), isNull(), isNull(), eq("relevance"), isNull(), eq(5), eq(true)))
                .thenReturn(PageResult.of(List.of(post), null, false));
        when(elasticsearch.enabled()).thenReturn(true);
        when(elasticsearch.available()).thenReturn(true);
        when(elasticsearch.postIndex()).thenReturn("post_idx");
        when(elasticsearch.indexExists("post_idx")).thenReturn(false);
        when(elasticsearch.getDocument(eq("post_idx"), eq("42"))).thenReturn(Optional.<JsonNode>empty());
        when(searchIndexRetryService.findLatestByPostId(42L)).thenReturn(retryTask);
        when(outboxMessageMapper.findLatestByAggregate("post", 42L)).thenReturn(OutboxMessage.builder()
                .id(3001L)
                .topic("post.published")
                .msgStatus(OutboxMessageMapper.STATUS_FAILED)
                .retryCount(1)
                .build());

        mvc.perform(get("/api/v1/search/posts/42/publish-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.index.retryTask.id").value(9001))
                .andExpect(jsonPath("$.data.index.retryTask.statusText").value("failed"))
                .andExpect(jsonPath("$.data.index.retryTask.lastError").doesNotExist())
                .andExpect(jsonPath("$.data.ready").value(true));

        verifyNoInteractions(indexer);
    }
}
