package com.offerlab.community.search.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.utils.CursorUtils;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostExtensionMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchPaginationFallbackTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 7, 26, 8, 0);

    private PostMapper postMapper;
    private PostExtensionMapper extensionMapper;
    private TagMapper tagMapper;
    private ElasticsearchHttpClient elasticsearch;
    private PostSearchIndexer postSearchIndexer;
    private PostFacade postFacade;
    private SearchAnalyticsService searchAnalyticsService;
    private MigrationCheckService migrationCheckService;
    private SearchFacadeImpl facade;

    @BeforeEach
    void setUp() {
        postMapper = mock(PostMapper.class);
        extensionMapper = mock(PostExtensionMapper.class);
        tagMapper = mock(TagMapper.class);
        elasticsearch = mock(ElasticsearchHttpClient.class);
        postSearchIndexer = mock(PostSearchIndexer.class);
        postFacade = mock(PostFacade.class);
        searchAnalyticsService = mock(SearchAnalyticsService.class);
        migrationCheckService = mock(MigrationCheckService.class);
        when(migrationCheckService.tagGovernanceReady()).thenReturn(true);
        facade = new SearchFacadeImpl(
                postMapper,
                extensionMapper,
                tagMapper,
                new ObjectMapper(),
                elasticsearch,
                postSearchIndexer,
                postFacade,
                searchAnalyticsService,
                migrationCheckService
        );
    }

    @Test
    void fullMysqlScanWindowWithExactlyOneVisiblePageKeepsContinuation() {
        List<PostPO> firstWindow = List.of(
                post(104L, BASE_TIME),
                post(103L, BASE_TIME.minusMinutes(1)),
                post(102L, BASE_TIME.minusMinutes(2)),
                post(101L, BASE_TIME.minusMinutes(3))
        );
        when(postSearchIndexer.ensurePostIndex()).thenReturn(false);
        when(postMapper.searchPublicPostsFallback(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                nullable(LocalDateTime.class), nullable(Long.class), eq(4)))
                .thenAnswer(invocation -> invocation.getArgument(6) == null ? firstWindow : List.of());
        when(extensionMapper.selectBatchIds(List.of(104L, 103L, 102L, 101L))).thenReturn(List.of());
        when(tagMapper.selectTagsByPostIds(List.of(104L, 103L, 102L, 101L))).thenReturn(List.of());
        when(postFacade.batchGetPosts(List.of(104L, 103L, 102L, 101L), null, false)).thenReturn(Map.of(
                104L, brief(firstWindow.get(0)),
                103L, brief(firstWindow.get(1))
        ));

        PageResult<PostBriefDTO> firstPage = facade.searchPosts(
                null, null, null, null, "relevance", null, 2);

        assertEquals(List.of(104L, 103L), firstPage.getItems().stream().map(PostBriefDTO::getId).toList());
        assertTrue(firstPage.getHasMore());
        assertNotNull(firstPage.getNextCursor());
        assertEquals(2, firstPage.getDiagnostics().get("visibleHits"));
        assertEquals(Boolean.TRUE, firstPage.getDiagnostics().get("scanWindowExhausted"));

        facade.searchPosts(null, null, null, null, "relevance", firstPage.getNextCursor(), 2);

        ArgumentCaptor<LocalDateTime> cursorTime = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Long> cursorId = ArgumentCaptor.forClass(Long.class);
        verify(postMapper, times(2)).searchPublicPostsFallback(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                cursorTime.capture(), cursorId.capture(), eq(4));
        assertNull(cursorTime.getAllValues().get(0));
        assertNull(cursorId.getAllValues().get(0));
        assertEquals(firstWindow.get(3).getCreateTime(), cursorTime.getAllValues().get(1));
        assertEquals(firstWindow.get(3).getId(), cursorId.getAllValues().get(1));
        verify(postSearchIndexer, times(1)).ensurePostIndex();
        verifyNoInteractions(elasticsearch);
    }

    @Test
    void partialMysqlScanWindowWithExactlyOneVisiblePageIsTerminal() {
        List<PostPO> candidates = List.of(
                post(202L, BASE_TIME),
                post(201L, BASE_TIME.minusMinutes(1))
        );
        when(postSearchIndexer.ensurePostIndex()).thenReturn(false);
        when(postMapper.searchPublicPostsFallback(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(4)))
                .thenReturn(candidates);
        when(extensionMapper.selectBatchIds(List.of(202L, 201L))).thenReturn(List.of());
        when(tagMapper.selectTagsByPostIds(List.of(202L, 201L))).thenReturn(List.of());
        when(postFacade.batchGetPosts(List.of(202L, 201L), null, false)).thenReturn(Map.of(
                202L, brief(candidates.get(0)),
                201L, brief(candidates.get(1))
        ));

        PageResult<PostBriefDTO> page = facade.searchPosts(
                null, null, null, null, "relevance", null, 2);

        assertFalse(page.getHasMore());
        assertNull(page.getNextCursor());
        assertEquals(Boolean.FALSE, page.getDiagnostics().get("scanWindowExhausted"));
    }

    @Test
    void mysqlVisibleOverflowAnchorsContinuationAtLastReturnedRow() {
        List<PostPO> firstWindow = List.of(
                post(304L, BASE_TIME),
                post(303L, BASE_TIME.minusMinutes(1)),
                post(302L, BASE_TIME.minusMinutes(2)),
                post(301L, BASE_TIME.minusMinutes(3))
        );
        when(postSearchIndexer.ensurePostIndex()).thenReturn(false);
        when(postMapper.searchPublicPostsFallback(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                nullable(LocalDateTime.class), nullable(Long.class), eq(4)))
                .thenAnswer(invocation -> invocation.getArgument(6) == null ? firstWindow : List.of());
        when(extensionMapper.selectBatchIds(List.of(304L, 303L, 302L, 301L))).thenReturn(List.of());
        when(tagMapper.selectTagsByPostIds(List.of(304L, 303L, 302L, 301L))).thenReturn(List.of());
        when(postFacade.batchGetPosts(List.of(304L, 303L, 302L, 301L), null, false)).thenReturn(Map.of(
                304L, brief(firstWindow.get(0)),
                303L, brief(firstWindow.get(1)),
                302L, brief(firstWindow.get(2)),
                301L, brief(firstWindow.get(3))
        ));

        PageResult<PostBriefDTO> firstPage = facade.searchPosts(
                null, null, null, null, "relevance", null, 2);
        facade.searchPosts(null, null, null, null, "relevance", firstPage.getNextCursor(), 2);

        ArgumentCaptor<LocalDateTime> cursorTime = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Long> cursorId = ArgumentCaptor.forClass(Long.class);
        verify(postMapper, times(2)).searchPublicPostsFallback(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                cursorTime.capture(), cursorId.capture(), eq(4));
        assertEquals(List.of(304L, 303L), firstPage.getItems().stream().map(PostBriefDTO::getId).toList());
        assertTrue(firstPage.getHasMore());
        assertEquals(firstWindow.get(1).getCreateTime(), cursorTime.getAllValues().get(1));
        assertEquals(firstWindow.get(1).getId(), cursorId.getAllValues().get(1));
    }

    @Test
    void mysqlRelevanceContinuationStaysOnMysqlAfterElasticsearchRecovers() {
        PostPO candidate = post(301L, BASE_TIME.minusHours(1));
        String cursor = relevanceCursor(null, BASE_TIME, 302L);
        when(postMapper.searchPublicPostsFallback(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(BASE_TIME), eq(302L), eq(4)))
                .thenReturn(List.of(candidate));
        when(extensionMapper.selectBatchIds(List.of(301L))).thenReturn(List.of());
        when(tagMapper.selectTagsByPostIds(List.of(301L))).thenReturn(List.of());
        when(postFacade.batchGetPosts(List.of(301L), null, false)).thenReturn(Map.of(301L, brief(candidate)));

        PageResult<PostBriefDTO> page = facade.searchPosts(
                null, null, null, null, "relevance", cursor, 2);

        assertEquals(List.of(301L), page.getItems().stream().map(PostBriefDTO::getId).toList());
        assertEquals("mysql", page.getSource());
        assertEquals(Boolean.TRUE, page.getDegraded());
        assertEquals("mysql_fallback_continuation", page.getFallbackReason());
        verify(postSearchIndexer, never()).ensurePostIndex();
        verifyNoInteractions(elasticsearch);
    }

    @Test
    void legacyRelevanceCursorIsRejectedInsteadOfMixingSortOrders() {
        String cursor = CursorUtils.encodeTimeId("sr1", BASE_TIME, 302L);

        BizException error = assertThrows(BizException.class, () -> facade.searchPosts(
                null, null, null, null, "relevance", cursor, 2));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("restart the search"));
        verifyNoInteractions(postSearchIndexer);
        verifyNoInteractions(postMapper);
        verifyNoInteractions(elasticsearch);
    }

    @Test
    void esRelevanceContinuationFailsExplicitlyWhenIndexIsUnavailable() {
        String cursor = relevanceCursor(6.5D, BASE_TIME, 401L);
        when(postSearchIndexer.ensurePostIndex()).thenReturn(false);

        BizException error = assertThrows(BizException.class, () -> facade.searchPosts(
                null, null, null, null, "relevance", cursor, 2));

        assertEquals(ErrorCode.ELASTICSEARCH_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("retry with the same cursor"));
        verifyNoInteractions(postMapper);
        verifyNoInteractions(elasticsearch);
    }

    @Test
    void esRelevanceContinuationDoesNotFallBackWhenSearchRequestFails() {
        String cursor = relevanceCursor(5.25D, BASE_TIME, 501L);
        when(postSearchIndexer.ensurePostIndex()).thenReturn(true);
        when(elasticsearch.postIndex()).thenReturn("post_idx");
        when(elasticsearch.search(eq("post_idx"), any())).thenReturn(Optional.empty());

        BizException error = assertThrows(BizException.class, () -> facade.searchPosts(
                null, null, null, null, "relevance", cursor, 2));

        assertEquals(ErrorCode.ELASTICSEARCH_ERROR.getCode(), error.getCode());
        verify(elasticsearch).search(eq("post_idx"), any());
        verifyNoInteractions(postMapper);
    }

    @Test
    void sparseEsRelevanceContinuationKeepsEsOrderingInsteadOfFallingBack() throws Exception {
        String cursor = relevanceCursor(8.0D, BASE_TIME, 601L);
        when(postSearchIndexer.ensurePostIndex()).thenReturn(true);
        when(elasticsearch.postIndex()).thenReturn("post_idx");
        when(elasticsearch.search(eq("post_idx"), any())).thenReturn(Optional.of(esHits(
                hit(600L, 7.5D, BASE_TIME.minusMinutes(1)),
                hit(599L, 7.0D, BASE_TIME.minusMinutes(2)),
                hit(598L, 6.5D, BASE_TIME.minusMinutes(3)),
                hit(597L, 6.0D, BASE_TIME.minusMinutes(4))
        )));
        when(postFacade.batchGetPosts(List.of(600L, 599L, 598L, 597L), null, false))
                .thenReturn(Map.of(600L, brief(post(600L, BASE_TIME.minusMinutes(1)))));

        PageResult<PostBriefDTO> page = facade.searchPosts(
                null, null, null, null, "relevance", cursor, 2);

        assertEquals(List.of(600L), page.getItems().stream().map(PostBriefDTO::getId).toList());
        assertEquals("elasticsearch", page.getSource());
        assertEquals(Boolean.FALSE, page.getDegraded());
        assertTrue(page.getHasMore());
        assertNotNull(page.getNextCursor());
        verifyNoInteractions(postMapper);
    }

    @Test
    void firstRelevancePageCanStillFallBackToMysql() {
        PostPO candidate = post(701L, BASE_TIME);
        when(postSearchIndexer.ensurePostIndex()).thenReturn(true);
        when(elasticsearch.postIndex()).thenReturn("post_idx");
        when(elasticsearch.search(eq("post_idx"), any())).thenReturn(Optional.empty());
        when(postMapper.searchPublicPostsFallback(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(4)))
                .thenReturn(List.of(candidate));
        when(extensionMapper.selectBatchIds(List.of(701L))).thenReturn(List.of());
        when(tagMapper.selectTagsByPostIds(List.of(701L))).thenReturn(List.of());
        when(postFacade.batchGetPosts(List.of(701L), null, false)).thenReturn(Map.of(701L, brief(candidate)));

        PageResult<PostBriefDTO> page = facade.searchPosts(
                null, null, null, null, "relevance", null, 2);

        assertEquals(List.of(701L), page.getItems().stream().map(PostBriefDTO::getId).toList());
        assertEquals("mysql", page.getSource());
        assertEquals("elasticsearch_unavailable", page.getFallbackReason());
    }

    private static PostPO post(long id, LocalDateTime createTime) {
        PostPO post = new PostPO();
        post.setId(id);
        post.setAuthorId(id + 10_000);
        post.setPostType(1);
        post.setTitle("Search result " + id);
        post.setContent("Visible search content " + id);
        post.setCreateTime(createTime);
        return post;
    }

    private static PostBriefDTO brief(PostPO post) {
        return PostBriefDTO.builder()
                .id(post.getId())
                .authorId(post.getAuthorId())
                .postType(post.getPostType())
                .title(post.getTitle())
                .summary(post.getContent())
                .createTime(post.getCreateTime())
                .build();
    }

    private static String relevanceCursor(Double score, LocalDateTime createTime, long postId) {
        long millis = createTime.toInstant(ZoneOffset.UTC).toEpochMilli();
        String raw = "sr2|relevance|" + (score == null ? "" : score)
                + "|" + millis + "|" + postId + "|";
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> hit(long postId, double score, LocalDateTime createTime) {
        long millis = createTime.toInstant(ZoneOffset.UTC).toEpochMilli();
        return Map.of(
                "_score", score,
                "sort", List.of(score, millis, postId),
                "_source", Map.of(
                        "postId", postId,
                        "authorId", postId + 10_000,
                        "type", 1,
                        "title", "Search result " + postId,
                        "summary", "Visible search content " + postId,
                        "createTime", millis
                )
        );
    }

    @SafeVarargs
    private final JsonNode esHits(Map<String, Object>... hits) {
        return new ObjectMapper().valueToTree(Map.of("hits", Map.of("hits", List.of(hits))));
    }
}
