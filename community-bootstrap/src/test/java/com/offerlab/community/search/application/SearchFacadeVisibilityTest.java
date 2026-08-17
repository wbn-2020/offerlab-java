package com.offerlab.community.search.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostExtensionMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostExtensionPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchFacadeVisibilityTest {

    @Mock
    private PostMapper postMapper;
    @Mock
    private PostExtensionMapper extensionMapper;
    @Mock
    private TagMapper tagMapper;
    @Mock
    private ElasticsearchHttpClient elasticsearch;
    @Mock
    private PostSearchIndexer postSearchIndexer;
    @Mock
    private PostFacade postFacade;
    @Mock
    private SearchAnalyticsService searchAnalyticsService;
    @Mock
    private MigrationCheckService migrationCheckService;

    private ObjectMapper objectMapper;
    private SearchFacadeImpl facade;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        facade = new SearchFacadeImpl(
                postMapper,
                extensionMapper,
                tagMapper,
                objectMapper,
                elasticsearch,
                postSearchIndexer,
                postFacade,
                searchAnalyticsService,
                migrationCheckService);
        lenient().when(migrationCheckService.tagGovernanceReady()).thenReturn(true);
    }

    @Test
    void elasticsearchResultsAreRecheckedAgainstCurrentPostVisibility() throws Exception {
        when(postSearchIndexer.ensurePostIndex()).thenReturn(true);
        when(elasticsearch.postIndex()).thenReturn("post_idx");
        when(elasticsearch.search(eq("post_idx"), any())).thenReturn(Optional.of(esHits()));

        PostBriefDTO currentPublicPost = PostBriefDTO.builder()
                .id(101L)
                .authorId(11L)
                .title("Current Java")
                .summary("Current summary")
                .createTime(LocalDateTime.now())
                .build();
        when(postFacade.batchGetPosts(List.of(101L, 102L), null, false)).thenReturn(Map.of(101L, currentPublicPost));

        PageResult<PostBriefDTO> page = facade.searchPosts("Java", null, null, null, "relevance", null, 2);

        assertEquals(1, page.getItems().size());
        assertEquals(101L, page.getItems().get(0).getId());
        assertEquals("<em>Java</em>", page.getItems().get(0).getHighlightTitle());
        assertEquals("Current Java", page.getItems().get(0).getTitle());
        assertTrue(page.getItems().get(0).getRecommendationReasons().contains("标题高亮命中"));
        assertTrue(page.getItems().get(0).getRecommendationReasons().contains("标题包含搜索词"));
        assertEquals("elasticsearch", page.getSource());
        assertEquals(Boolean.FALSE, page.getDegraded());
        verify(postFacade).batchGetPosts(List.of(101L, 102L), null, false);
        verify(searchAnalyticsService).recordSearch("Java", null, null, null, "relevance", 1, true);
    }

    @Test
    void staleElasticsearchHighlightIsDiscardedAfterCurrentPostReload() throws Exception {
        when(postSearchIndexer.ensurePostIndex()).thenReturn(true);
        when(elasticsearch.postIndex()).thenReturn("post_idx");
        when(elasticsearch.search(eq("post_idx"), any())).thenReturn(Optional.of(esHits()));

        PostBriefDTO currentPublicPost = PostBriefDTO.builder()
                .id(101L)
                .authorId(11L)
                .title("Current Python")
                .summary("Current summary without the indexed term")
                .createTime(LocalDateTime.now())
                .build();
        when(postFacade.batchGetPosts(List.of(101L, 102L), null, false)).thenReturn(Map.of(101L, currentPublicPost));

        PageResult<PostBriefDTO> page = facade.searchPosts("Java", null, null, null, "relevance", null, 2);

        assertEquals(1, page.getItems().size());
        assertEquals(101L, page.getItems().get(0).getId());
        assertEquals("Current Python", page.getItems().get(0).getTitle());
        assertEquals(null, page.getItems().get(0).getHighlightTitle());
        assertTrue(page.getItems().get(0).getRecommendationReasons() == null
                || !page.getItems().get(0).getRecommendationReasons().contains("鏍囬楂樹寒鍛戒腑"));
    }

    @Test
    void hotKeywordsDoNotExposeStaticFallbackSeedsAsTrend() {
        when(tagMapper.selectHotTags(10)).thenReturn(List.of());
        when(postMapper.countCompanies(any(), anyInt())).thenReturn(List.of());
        when(postMapper.countPositions(any(), anyInt())).thenReturn(List.of());

        List<String> hotKeywords = facade.getHotKeywords(10);

        assertEquals(List.of(), hotKeywords);
    }

    @Test
    void firstPageEmptyElasticsearchResultUsesMysqlWhenPublicDatabaseMatches() throws Exception {
        when(postSearchIndexer.ensurePostIndex()).thenReturn(true);
        when(elasticsearch.postIndex()).thenReturn("post_idx");
        when(elasticsearch.search(eq("post_idx"), any())).thenReturn(Optional.of(emptyEsHits()));
        PostPO post = post(201L, 21L, "Kafka 消费幂等", LocalDateTime.now());
        when(postMapper.searchPublicPostsFallback(
                eq(List.of("Kafka")), eq(1), isNull(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(post));
        when(extensionMapper.selectBatchIds(List.of(201L))).thenReturn(List.of());
        when(tagMapper.selectTagsByPostIds(List.of(201L))).thenReturn(List.of());
        when(postFacade.batchGetPosts(List.of(201L), null, false)).thenReturn(Map.of(201L, brief(post)));

        PageResult<PostBriefDTO> page = facade.searchPosts("Kafka", null, null, null, "relevance", null, 5);

        assertEquals(List.of(201L), page.getItems().stream().map(PostBriefDTO::getId).toList());
        assertEquals(1L, page.getTotal());
        assertEquals("mysql", page.getSource());
        assertEquals(Boolean.TRUE, page.getDegraded());
        assertEquals("search_index_empty_consistency_fallback", page.getFallbackReason());
        assertEquals(10, page.getScanLimit());
        verify(searchAnalyticsService).recordSearch("Kafka", null, null, null, "relevance", 1, true);
    }

    @Test
    void firstPageEmptyElasticsearchResultKeepsTrueZeroWhenMysqlIsAlsoEmpty() throws Exception {
        when(postSearchIndexer.ensurePostIndex()).thenReturn(true);
        when(elasticsearch.postIndex()).thenReturn("post_idx");
        when(elasticsearch.search(eq("post_idx"), any())).thenReturn(Optional.of(emptyEsHits()));
        when(postMapper.searchPublicPostsFallback(
                eq(List.of("AuroraIndexerProbe00992905")), eq(1), isNull(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        PageResult<PostBriefDTO> page = facade.searchPosts("AuroraIndexerProbe00992905", null, null, null, "relevance", null, 10);

        assertEquals(0, page.getItems().size());
        assertEquals("elasticsearch", page.getSource());
        assertEquals(Boolean.FALSE, page.getDegraded());
        assertEquals(null, page.getFallbackReason());
        verify(searchAnalyticsService).recordSearch(
                "AuroraIndexerProbe00992905", null, null, null, "relevance", 0, true);
    }

    @Test
    void hyphenatedEngineeringKeywordKeepsSuccessfulElasticsearchZero() throws Exception {
        when(postSearchIndexer.ensurePostIndex()).thenReturn(true);
        when(elasticsearch.postIndex()).thenReturn("post_idx");
        when(elasticsearch.search(eq("post_idx"), any())).thenReturn(Optional.of(emptyEsHits()));

        String keyword = "order-center-v2";

        PageResult<PostBriefDTO> page = facade.searchPosts(keyword, null, null, 10, "relevance", null, 10);

        assertEquals(0, page.getItems().size());
        assertEquals("elasticsearch", page.getSource());
        assertEquals(Boolean.FALSE, page.getDegraded());
        assertEquals(null, page.getFallbackReason());
        verify(postMapper).searchPublicPostsFallback(
                eq(List.of("order", "center", "v2")), eq(2), isNull(), any(), any(), eq(10), any(), any(), any(), anyInt());
        verify(searchAnalyticsService).recordSearch(keyword, null, null, 10, "relevance", 0, true);
    }

    @Test
    void sparseElasticsearchPageDoesNotSwitchSourcesAfterVisibilityFiltering() throws Exception {
        when(postSearchIndexer.ensurePostIndex()).thenReturn(true);
        when(elasticsearch.postIndex()).thenReturn("post_idx");
        when(elasticsearch.search(eq("post_idx"), any())).thenReturn(Optional.of(sparseEsHits()));

        PostBriefDTO currentPublicPost = PostBriefDTO.builder()
                .id(301L)
                .authorId(31L)
                .title("Visible Java from ES")
                .summary("visible")
                .createTime(LocalDateTime.now())
                .build();
        when(postFacade.batchGetPosts(List.of(301L, 302L, 303L, 304L), null, false)).thenReturn(Map.of(301L, currentPublicPost));

        PageResult<PostBriefDTO> page = facade.searchPosts("Java", null, null, null, "latest", null, 2);

        assertEquals(1, page.getItems().size());
        assertEquals(301L, page.getItems().get(0).getId());
        assertEquals("elasticsearch", page.getSource());
        assertEquals(Boolean.FALSE, page.getDegraded());
        assertEquals(null, page.getFallbackReason());
        verify(postMapper, never()).searchPublicPostsFallback(
                eq(List.of("Java")), eq(1), isNull(), any(), any(), any(), any(), any(), any(), anyInt());
        verify(searchAnalyticsService).recordSearch("Java", null, null, null, "latest", 1, true);
    }

    @Test
    void syntheticMysqlResultIsHiddenUnlessTestDataModeIsEnabled() {
        String keyword = "CODEX-E2E-search-visibility";
        PostPO post = post(501L, 51L, keyword + " Java offer replay", LocalDateTime.now());
        post.setContent("Synthetic CODEX-E2E verification content.");

        when(postMapper.searchPublicPostsFallback(any(), anyInt(), isNull(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(post));
        when(extensionMapper.selectBatchIds(any())).thenReturn(List.of());
        when(tagMapper.selectTagsByPostIds(List.of(501L))).thenReturn(List.of());
        when(postFacade.batchGetPosts(List.of(501L), null, false)).thenReturn(Map.of());
        when(postFacade.batchGetPosts(List.of(501L), null, true)).thenReturn(Map.of(501L, brief(post)));

        PageResult<PostBriefDTO> hidden = facade.searchPosts(keyword, null, null, null, "relevance", null, 10);
        PageResult<PostBriefDTO> visible = facade.searchPosts(keyword, null, null, null, "relevance", null, 10, true);

        assertEquals(0, hidden.getItems().size());
        assertEquals("test_data_filtered_unless_includeTestData", hidden.getDiagnostics().get("emptyReason"));
        assertEquals(1, hidden.getDiagnostics().get("syntheticFiltered"));
        assertEquals(1, visible.getItems().size());
        assertEquals(501L, visible.getItems().get(0).getId());
        assertEquals(Boolean.TRUE, visible.getDiagnostics().get("includeTestData"));
    }

    @Test
    void numericPostIdKeywordCanRecallExactMysqlPost() {
        PostPO post = post(909L, 90L, "Type 10 search diagnostics", LocalDateTime.now());

        when(postMapper.searchPublicPostsFallback(eq(List.<String>of()), eq(0), eq(909L), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(post));
        when(extensionMapper.selectBatchIds(any())).thenReturn(List.of());
        when(tagMapper.selectTagsByPostIds(List.of(909L))).thenReturn(List.of());
        when(postFacade.batchGetPosts(List.of(909L), null, false)).thenReturn(Map.of(909L, brief(post)));

        PageResult<PostBriefDTO> page = facade.searchPosts("909", null, null, null, "relevance", null, 5);

        assertEquals(1, page.getItems().size());
        assertEquals(909L, page.getItems().get(0).getId());
        assertEquals(0, page.getDiagnostics().get("syntheticFiltered"));
    }

    private JsonNode esHits() throws Exception {
        long now = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
        return objectMapper.readTree("""
                {
                  "hits": {
                    "hits": [
                      {
                        "_source": {
                          "id": 101,
                          "authorId": 11,
                          "type": 1,
                          "title": "Stale Java",
                          "summary": "stale",
                          "createTime": %d
                        },
                        "highlight": {
                          "title": ["<em>Java</em>"]
                        }
                      },
                      {
                        "_source": {
                          "id": 102,
                          "authorId": 12,
                          "type": 1,
                          "title": "Hidden Java",
                          "summary": "hidden",
                          "createTime": %d
                        }
                      }
                    ]
                  }
                }
                """.formatted(now, now - 1000));
    }

    private JsonNode sparseEsHits() throws Exception {
        long now = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
        return objectMapper.readTree("""
                {
                  "hits": {
                    "hits": [
                      {
                        "_source": {
                          "id": 301,
                          "authorId": 31,
                          "type": 1,
                          "title": "Visible Java",
                          "summary": "visible",
                          "createTime": %d
                        }
                      },
                      {
                        "_source": {
                          "id": 302,
                          "authorId": 32,
                          "type": 1,
                          "title": "Hidden Java 1",
                          "summary": "hidden",
                          "createTime": %d
                        }
                      },
                      {
                        "_source": {
                          "id": 303,
                          "authorId": 33,
                          "type": 1,
                          "title": "Hidden Java 2",
                          "summary": "hidden",
                          "createTime": %d
                        }
                      },
                      {
                        "_source": {
                          "id": 304,
                          "authorId": 34,
                          "type": 1,
                          "title": "Hidden Java 3",
                          "summary": "hidden",
                          "createTime": %d
                        }
                      }
                    ]
                  }
                }
                """.formatted(now, now - 1000, now - 2000, now - 3000));
    }

    private JsonNode emptyEsHits() throws Exception {
        return objectMapper.readTree("""
                {
                  "hits": {
                    "hits": []
                  }
                }
                """);
    }

    private PostPO post(Long id, Long authorId, String title, LocalDateTime createTime) {
        PostPO post = new PostPO();
        post.setId(id);
        post.setAuthorId(authorId);
        post.setPostType(1);
        post.setTitle(title);
        post.setContent("Search fallback visible content for " + title);
        post.setCreateTime(createTime);
        return post;
    }

    private PostBriefDTO brief(PostPO post) {
        return PostBriefDTO.builder()
                .id(post.getId())
                .authorId(post.getAuthorId())
                .postType(post.getPostType())
                .title(post.getTitle())
                .summary(post.getContent())
                .createTime(post.getCreateTime())
                .build();
    }
}
