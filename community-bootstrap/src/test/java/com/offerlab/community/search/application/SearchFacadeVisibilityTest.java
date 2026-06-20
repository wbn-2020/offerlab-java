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
import com.offerlab.community.user.api.UserFacade;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    private UserFacade userFacade;
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
                userFacade,
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
        assertEquals("elasticsearch", page.getSource());
        assertEquals(Boolean.FALSE, page.getDegraded());
        verify(postFacade).batchGetPosts(List.of(101L, 102L), null, false);
        verify(searchAnalyticsService).recordSearch("Java", null, null, null, "relevance", 1, true);
    }

    @Test
    void firstPageEmptyElasticsearchResultFallsBackToMysqlContent() throws Exception {
        when(postSearchIndexer.ensurePostIndex()).thenReturn(true);
        when(elasticsearch.postIndex()).thenReturn("post_idx");
        when(elasticsearch.search(eq("post_idx"), any())).thenReturn(Optional.of(emptyEsHits()));

        PostPO post = new PostPO();
        post.setId(201L);
        post.setAuthorId(21L);
        post.setPostType(1);
        post.setTitle("Kafka \u524A\u5CF0\u548C\u6D88\u8D39\u5E42\u7B49\u590D\u76D8");
        post.setContent("\u6D88\u606F\u5806\u79EF\u6392\u67E5\u3001\u91CD\u8BD5\u6B7B\u4FE1\u548C\u4E1A\u52A1\u552F\u4E00\u952E\u53BB\u91CD\u3002");
        post.setCreateTime(LocalDateTime.now());

        PostExtensionPO extension = new PostExtensionPO();
        extension.setPostId(201L);
        extension.setExtJson("""
                {"company":"\u5B57\u8282\u8DF3\u52A8","position":"Java \u540E\u7AEF"}
                """);

        when(postMapper.searchPublicPostsFallback(eq("Kafka"), isNull(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(post));
        when(extensionMapper.selectBatchIds(any())).thenReturn(List.of(extension));
        when(tagMapper.selectTagsByPostIds(List.of(201L))).thenReturn(List.of());
        when(postFacade.batchGetCounters(List.of(201L))).thenReturn(Map.of());
        when(userFacade.batchGetUserBriefs(Set.of(21L))).thenReturn(Map.of());

        PageResult<PostBriefDTO> page = facade.searchPosts("Kafka", null, null, null, "relevance", null, 5);

        assertEquals(1, page.getItems().size());
        assertEquals(201L, page.getItems().get(0).getId());
        assertEquals(1L, page.getTotal());
        assertEquals("mysql", page.getSource());
        assertEquals(Boolean.TRUE, page.getDegraded());
        assertEquals("elasticsearch_empty", page.getFallbackReason());
        assertEquals(10, page.getScanLimit());
        verify(searchAnalyticsService).recordSearch("Kafka", null, null, null, "relevance", 1, true);
    }

    @Test
    void exactPublicProbeFallsBackToMysqlWhenElasticsearchSearchIsNotFreshYet() throws Exception {
        when(postSearchIndexer.ensurePostIndex()).thenReturn(true);
        when(elasticsearch.postIndex()).thenReturn("post_idx");
        when(elasticsearch.search(eq("post_idx"), any())).thenReturn(Optional.of(emptyEsHits()));

        String probe = "AuroraIndexerProbe00992905";
        PostPO post = new PostPO();
        post.setId(202L);
        post.setAuthorId(22L);
        post.setPostType(1);
        post.setTitle("搜索新鲜度验证 " + probe);
        post.setContent("公开帖子已经写入主库，ES 搜索不可见时 API 也要通过 MySQL fallback 找到。");
        post.setCreateTime(LocalDateTime.now());

        when(postMapper.searchPublicPostsFallback(eq(probe), isNull(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(post));
        when(extensionMapper.selectBatchIds(any())).thenReturn(List.of());
        when(tagMapper.selectTagsByPostIds(List.of(202L))).thenReturn(List.of());
        when(postFacade.batchGetCounters(List.of(202L))).thenReturn(Map.of());
        when(userFacade.batchGetUserBriefs(Set.of(22L))).thenReturn(Map.of());

        PageResult<PostBriefDTO> page = facade.searchPosts(probe, null, null, null, "relevance", null, 10);

        assertEquals(1, page.getItems().size());
        assertEquals(202L, page.getItems().get(0).getId());
        assertEquals("mysql", page.getSource());
        assertEquals(Boolean.TRUE, page.getDegraded());
        assertEquals("elasticsearch_empty", page.getFallbackReason());
        verify(searchAnalyticsService).recordSearch(probe, null, null, null, "relevance", 1, true);
    }

    @Test
    void hyphenatedEngineeringKeywordFallsBackToMysqlForRegularContent() throws Exception {
        when(postSearchIndexer.ensurePostIndex()).thenReturn(true);
        when(elasticsearch.postIndex()).thenReturn("post_idx");
        when(elasticsearch.search(eq("post_idx"), any())).thenReturn(Optional.of(emptyEsHits()));

        String keyword = "order-center-v2";
        PostPO post = post(203L, 23L, "Payment rollout for " + keyword, LocalDateTime.now());
        post.setPostType(10);

        when(postMapper.searchPublicPostsFallback(eq(keyword), isNull(), any(), any(), eq(10), any(), anyInt()))
                .thenReturn(List.of(post));
        when(extensionMapper.selectBatchIds(any())).thenReturn(List.of());
        when(tagMapper.selectTagsByPostIds(List.of(203L))).thenReturn(List.of());
        when(postFacade.batchGetCounters(List.of(203L))).thenReturn(Map.of());
        when(userFacade.batchGetUserBriefs(Set.of(23L))).thenReturn(Map.of());

        PageResult<PostBriefDTO> page = facade.searchPosts(keyword, null, null, 10, "relevance", null, 10);

        assertEquals(1, page.getItems().size());
        assertEquals(203L, page.getItems().get(0).getId());
        assertEquals(10, page.getItems().get(0).getPostType());
        assertEquals("mysql", page.getSource());
        assertEquals("elasticsearch_empty", page.getFallbackReason());
        verify(searchAnalyticsService).recordSearch(keyword, null, null, 10, "relevance", 1, true);
    }

    @Test
    void sparseElasticsearchPageFallsBackToMysqlWhenVisibilityFilterExhaustsScanWindow() throws Exception {
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

        LocalDateTime now = LocalDateTime.now();
        List<PostPO> mysqlPosts = List.of(
                post(401L, 41L, "Visible Java fallback 1", now),
                post(402L, 42L, "Visible Java fallback 2", now.minusMinutes(1)),
                post(403L, 43L, "Visible Java fallback 3", now.minusMinutes(2))
        );
        when(postMapper.searchPublicPostsFallback(eq("Java"), isNull(), any(), any(), any(), any(), anyInt()))
                .thenReturn(mysqlPosts);
        when(extensionMapper.selectBatchIds(any())).thenReturn(List.of());
        when(tagMapper.selectTagsByPostIds(any())).thenReturn(List.of());
        when(postFacade.batchGetCounters(List.of(401L, 402L, 403L))).thenReturn(Map.of());
        when(userFacade.batchGetUserBriefs(Set.of(41L, 42L, 43L))).thenReturn(Map.of());

        PageResult<PostBriefDTO> page = facade.searchPosts("Java", null, null, null, "latest", null, 2);

        assertEquals(2, page.getItems().size());
        assertEquals(401L, page.getItems().get(0).getId());
        assertEquals(402L, page.getItems().get(1).getId());
        assertEquals(Boolean.TRUE, page.getHasMore());
        assertEquals("mysql", page.getSource());
        assertEquals(Boolean.TRUE, page.getDegraded());
        assertEquals("elasticsearch_visibility_filtered", page.getFallbackReason());
        assertEquals(4, page.getScanLimit());
        verify(searchAnalyticsService).recordSearch("Java", null, null, null, "latest", 2, true);
    }

    @Test
    void syntheticMysqlResultIsHiddenUnlessTestDataModeIsEnabled() {
        String keyword = "CODEX-E2E-search-visibility";
        PostPO post = post(501L, 51L, keyword + " Java offer replay", LocalDateTime.now());
        post.setContent("Synthetic CODEX-E2E verification content.");

        when(postMapper.searchPublicPostsFallback(eq(keyword), isNull(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(post));
        when(extensionMapper.selectBatchIds(any())).thenReturn(List.of());
        when(tagMapper.selectTagsByPostIds(List.of(501L))).thenReturn(List.of());
        when(postFacade.batchGetCounters(List.of(501L))).thenReturn(Map.of());
        when(userFacade.batchGetUserBriefs(Set.of(51L))).thenReturn(Map.of());

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

        when(postMapper.searchPublicPostsFallback(eq("909"), eq(909L), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(post));
        when(extensionMapper.selectBatchIds(any())).thenReturn(List.of());
        when(tagMapper.selectTagsByPostIds(List.of(909L))).thenReturn(List.of());
        when(postFacade.batchGetCounters(List.of(909L))).thenReturn(Map.of());
        when(userFacade.batchGetUserBriefs(Set.of(90L))).thenReturn(Map.of());

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
}
