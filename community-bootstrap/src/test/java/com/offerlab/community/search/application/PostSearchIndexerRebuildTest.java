package com.offerlab.community.search.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostExtensionMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostCounterPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostExtensionPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.stream.LongStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostSearchIndexerRebuildTest {

    @Mock
    private ElasticsearchHttpClient elasticsearch;
    @Mock
    private PostMapper postMapper;
    @Mock
    private PostExtensionMapper extensionMapper;
    @Mock
    private PostCounterMapper counterMapper;
    @Mock
    private TagMapper tagMapper;
    @Mock
    private MigrationCheckService migrationCheckService;

    private PostSearchIndexer indexer;

    @BeforeEach
    void setUp() {
        indexer = new PostSearchIndexer(elasticsearch, postMapper, extensionMapper, counterMapper, tagMapper, new ObjectMapper(), migrationCheckService);
        lenient().when(migrationCheckService.tagGovernanceReady()).thenReturn(true);
        lenient().when(elasticsearch.enabled()).thenReturn(true);
        lenient().when(elasticsearch.available()).thenReturn(true);
        lenient().when(elasticsearch.postIndex()).thenReturn("post_idx");
        lenient().when(elasticsearch.indexExists("post_idx")).thenReturn(true);
        lenient().when(elasticsearch.updateMapping(eq("post_idx"), any())).thenReturn(true);
    }

    @Test
    void statusRefreshesExistingIndexMappingAndReportsReadyIndex() {
        Map<String, Object> status = indexer.status();

        assertEquals("UP", status.get("status"));
        assertEquals(true, status.get("indexReady"));
        assertEquals(false, status.get("publicSearchDegraded"));
        assertEquals("elasticsearch", status.get("publicSearchSource"));
        assertEquals("搜索索引已就绪，新发布内容会优先进入实时搜索。", status.get("message"));
        assertEquals("Public search is using Elasticsearch index.", status.get("diagnosticMessage"));
        verify(elasticsearch).updateMapping(eq("post_idx"), any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void statusAddsCommunitySearchFieldsToExistingIndexMapping() {
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);

        indexer.status();

        verify(elasticsearch).updateMapping(eq("post_idx"), captor.capture());
        Map<String, Object> props = captor.getValue();
        assertEquals("keyword", ((Map<String, Object>) props.get("difficulty")).get("type"));
        assertEquals("text", ((Map<String, Object>) props.get("scenario")).get("type"));
        assertEquals("keyword", ((Map<String, Object>) props.get("contentType")).get("type"));
        assertEquals("text", ((Map<String, Object>) props.get("techStacks")).get("type"));
        assertEquals("text", ((Map<String, Object>) props.get("tagSynonyms")).get("type"));
        assertEquals("text", ((Map<String, Object>) props.get("tagSearchTerms")).get("type"));
        Map<String, Object> tags = (Map<String, Object>) props.get("tags");
        assertEquals("nested", tags.get("type"));
        assertTrue(((Map<String, Object>) tags.get("properties")).containsKey("synonyms"));
    }

    @Test
    void rebuildAllAdvancesByPagedPostIdBatches() {
        List<PostPO> firstBatch = LongStream.rangeClosed(1L, 500L)
                .mapToObj(PostSearchIndexerRebuildTest::post)
                .toList();
        when(postMapper.selectPublicPostsForIndexAfterId(0L, 500)).thenReturn(firstBatch);
        when(postMapper.selectPublicPostsForIndexAfterId(500L, 500)).thenReturn(List.of(post(501L)));
        when(extensionMapper.selectById(any(Long.class))).thenAnswer(invocation -> extension(invocation.getArgument(0)));
        when(counterMapper.selectById(any(Long.class))).thenAnswer(invocation -> counter(invocation.getArgument(0)));
        when(tagMapper.selectTagsByPostIds(any())).thenReturn(List.of());
        when(elasticsearch.indexDocument(eq("post_idx"), any(String.class), any())).thenReturn(true);

        Map<String, Object> result = indexer.rebuildAll();

        assertTrue((Boolean) result.get("accepted"));
        assertEquals(501, result.get("indexed"));
        assertEquals(0, result.get("failed"));
        assertEquals(501, result.get("total"));
        verify(postMapper).selectPublicPostsForIndexAfterId(0L, 500);
        verify(postMapper).selectPublicPostsForIndexAfterId(500L, 500);
    }

    @Test
    void rebuildAllReportsFailedWhenAnyDocumentCannotBeIndexed() {
        PostPO first = post(1L);
        when(postMapper.selectPublicPostsForIndexAfterId(0L, 500)).thenReturn(List.of(first));
        when(extensionMapper.selectById(1L)).thenReturn(extension(1L));
        when(counterMapper.selectById(1L)).thenReturn(counter(1L));
        when(tagMapper.selectTagsByPostIds(any())).thenReturn(List.of());
        when(elasticsearch.indexDocument(eq("post_idx"), eq("1"), any())).thenReturn(false);

        Map<String, Object> result = indexer.rebuildAll();

        assertFalse((Boolean) result.get("accepted"));
        assertEquals(0, result.get("indexed"));
        assertEquals(1, result.get("failed"));
        assertEquals(1, result.get("total"));
        assertEquals("1 post documents failed to index", result.get("message"));
    }

    @Test
    void statusExposesMysqlFallbackWhenElasticsearchIsUnavailable() {
        when(elasticsearch.available()).thenReturn(false);
        when(migrationCheckService.tagGovernanceReady()).thenReturn(false);

        Map<String, Object> status = indexer.status();

        assertEquals("DEGRADED", status.get("status"));
        assertEquals(false, status.get("available"));
        assertEquals(false, status.get("indexReady"));
        assertEquals(true, status.get("publicSearchAvailable"));
        assertEquals(true, status.get("publicSearchDegraded"));
        assertEquals("mysql", status.get("publicSearchSource"));
        assertEquals(true, status.get("dbFallbackAvailable"));
        assertEquals("compat", status.get("fallbackMode"));
        assertEquals(false, status.get("fallbackSchemaReady"));
        assertEquals("Restore Elasticsearch and apply tag governance migration to enable full tag synonym recall.",
                status.get("action"));
    }

    @Test
    void statusReportsDownWhenBothIndexAndFallbackAreUnavailable() {
        when(elasticsearch.available()).thenReturn(false);
        when(migrationCheckService.tagGovernanceReady()).thenThrow(new IllegalStateException("db down"));

        Map<String, Object> status = indexer.status();

        assertEquals("DOWN", status.get("status"));
        assertEquals(false, status.get("publicSearchAvailable"));
        assertEquals(false, status.get("publicSearchDegraded"));
        assertEquals("unavailable", status.get("publicSearchSource"));
        assertEquals(false, status.get("dbFallbackAvailable"));
        assertEquals("unavailable", status.get("fallbackMode"));
        assertEquals("Check database connectivity and Elasticsearch readiness before signing off search.",
                status.get("action"));
    }

    private static PostPO post(Long id) {
        PostPO po = new PostPO();
        po.setId(id);
        po.setAuthorId(10L);
        po.setPostType(Post.TYPE_INTERVIEW);
        po.setTitle("post " + id);
        po.setContent("content " + id);
        po.setVisibility(Post.VIS_PUBLIC);
        po.setPostStatus(Post.STATUS_PUBLISHED);
        po.setCreateTime(LocalDateTime.now());
        po.setUpdateTime(LocalDateTime.now());
        po.setIsDeleted(0);
        return po;
    }

    private static PostExtensionPO extension(Long postId) {
        PostExtensionPO extension = new PostExtensionPO();
        extension.setPostId(postId);
        extension.setPostType(Post.TYPE_INTERVIEW);
        extension.setExtJson("{\"company\":\"Acme\",\"position\":\"Java\"}");
        return extension;
    }

    private static PostCounterPO counter(Long postId) {
        PostCounterPO counter = new PostCounterPO();
        counter.setPostId(postId);
        counter.setViewCount(0L);
        counter.setLikeCount(0L);
        counter.setCommentCount(0L);
        counter.setFavoriteCount(0L);
        return counter;
    }
}
