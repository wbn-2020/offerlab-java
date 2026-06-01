package com.offerlab.community.search.application;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private PostSearchIndexer indexer;

    @BeforeEach
    void setUp() {
        indexer = new PostSearchIndexer(elasticsearch, postMapper, extensionMapper, counterMapper, tagMapper, new ObjectMapper());
        when(elasticsearch.enabled()).thenReturn(true);
        when(elasticsearch.available()).thenReturn(true);
        when(elasticsearch.postIndex()).thenReturn("post_idx");
        when(elasticsearch.indexExists("post_idx")).thenReturn(true);
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
