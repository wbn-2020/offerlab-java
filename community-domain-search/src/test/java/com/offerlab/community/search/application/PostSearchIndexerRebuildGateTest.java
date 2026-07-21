package com.offerlab.community.search.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostExtensionMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import com.offerlab.community.search.infrastructure.persistence.mapper.SearchIndexRebuildTaskMapper;
import com.offerlab.community.search.infrastructure.persistence.po.SearchIndexRebuildTaskPO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PostSearchIndexerRebuildGateTest {

    private final ElasticsearchHttpClient elasticsearch = mock(ElasticsearchHttpClient.class);
    private final PostMapper postMapper = mock(PostMapper.class);
    private final PostExtensionMapper extensionMapper = mock(PostExtensionMapper.class);
    private final PostCounterMapper counterMapper = mock(PostCounterMapper.class);
    private final TagMapper tagMapper = mock(TagMapper.class);
    private final MigrationCheckService migrationCheckService = mock(MigrationCheckService.class);
    private final SearchIndexRebuildTaskMapper rebuildTaskMapper = mock(SearchIndexRebuildTaskMapper.class);

    @Test
    void activeAndFailedLatestRebuildBothKeepElasticsearchNotReady() {
        SearchIndexRebuildTaskPO pending = rebuildTask("task-pending", "PENDING");
        SearchIndexRebuildTaskPO failed = rebuildTask("task-failed", "FAILED");
        when(rebuildTaskMapper.tableExists()).thenReturn(1);
        when(rebuildTaskMapper.findLatest()).thenReturn(pending, failed);
        PostSearchIndexer indexer = indexer();

        assertFalse(indexer.ensurePostIndex());
        assertFalse(indexer.ensurePostIndex());

        verifyNoInteractions(elasticsearch);
    }

    @Test
    void unavailableRebuildTableFailsClosedToMysqlFallback() {
        when(rebuildTaskMapper.tableExists()).thenReturn(0);
        PostSearchIndexer indexer = indexer();

        assertFalse(indexer.ensurePostIndex());

        verifyNoInteractions(elasticsearch);
    }

    private PostSearchIndexer indexer() {
        return new PostSearchIndexer(
                elasticsearch,
                postMapper,
                extensionMapper,
                counterMapper,
                tagMapper,
                new ObjectMapper(),
                migrationCheckService,
                rebuildTaskMapper);
    }

    private static SearchIndexRebuildTaskPO rebuildTask(String taskId, String status) {
        SearchIndexRebuildTaskPO task = new SearchIndexRebuildTaskPO();
        task.setTaskId(taskId);
        task.setTaskStatus(status);
        return task;
    }
}
