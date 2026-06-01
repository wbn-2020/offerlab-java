package com.offerlab.community.search.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostExtensionMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
                searchAnalyticsService);
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
        when(postFacade.batchGetPosts(List.of(101L, 102L))).thenReturn(Map.of(101L, currentPublicPost));

        PageResult<PostBriefDTO> page = facade.searchPosts("Java", null, null, null, "relevance", null, 2);

        assertEquals(1, page.getItems().size());
        assertEquals(101L, page.getItems().get(0).getId());
        assertEquals("<em>Java</em>", page.getItems().get(0).getHighlightTitle());
        assertEquals("Current Java", page.getItems().get(0).getTitle());
        verify(postFacade).batchGetPosts(List.of(101L, 102L));
        verify(searchAnalyticsService).recordSearch("Java", null, null, null, "relevance", 1, true);
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
}
