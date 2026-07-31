package com.offerlab.community.search.application;

import com.offerlab.community.search.infrastructure.persistence.mapper.SearchIndexRebuildTaskMapper;
import com.offerlab.community.search.infrastructure.persistence.po.SearchIndexRebuildTaskPO;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchIndexTaskServicePersistenceTest {

    @Test
    void rebuildPersistsRunningHeartbeatCheckpointAndSucceededState() {
        PostSearchIndexer indexer = mock(PostSearchIndexer.class);
        SearchIndexRebuildTaskMapper mapper = mock(SearchIndexRebuildTaskMapper.class);
        AtomicReference<SearchIndexRebuildTaskPO> stored = new AtomicReference<>();
        when(mapper.tableExists()).thenReturn(1);
        when(mapper.findActive()).thenReturn(null);
        when(mapper.insertPending(any(SearchIndexRebuildTaskPO.class))).thenAnswer(invocation -> {
            SearchIndexRebuildTaskPO task = invocation.getArgument(0);
            initializePending(task);
            stored.set(task);
            return 1;
        });
        when(mapper.findByTaskId(anyString())).thenAnswer(invocation -> stored.get());
        when(mapper.markRunning(anyString(), anyString(), any(LocalDateTime.class))).thenAnswer(invocation -> {
            stored.get().setTaskStatus("RUNNING");
            return 1;
        });
        when(mapper.heartbeat(anyString(), anyString(), eq(42L), eq(42), eq(0), eq(42),
                any(LocalDateTime.class))).thenReturn(1);
        when(indexer.rebuildAll(any(PostSearchIndexer.RebuildProgressListener.class))).thenAnswer(invocation -> {
            PostSearchIndexer.RebuildProgressListener listener = invocation.getArgument(0);
            listener.onProgress(new PostSearchIndexer.RebuildProgress(42L, 42, 0, 42));
            return Map.of(
                    "accepted", true,
                    "indexed", 42,
                    "failed", 0,
                    "total", 42,
                    "checkpointId", 42L,
                    "indexName", "public-posts");
        });
        when(mapper.finish(anyString(), anyString(), eq("SUCCEEDED"), eq(42), eq(0), eq(42),
                eq("public-posts"), isNull(), eq(42L))).thenAnswer(invocation -> {
            SearchIndexRebuildTaskPO task = stored.get();
            task.setTaskStatus("SUCCEEDED");
            task.setCheckpointId(42L);
            task.setIndexedCount(42);
            task.setFailedCount(0);
            task.setTotalCount(42);
            task.setIndexName("public-posts");
            task.setFinishedAt(LocalDateTime.now());
            return 1;
        });
        SearchIndexTaskService service = new SearchIndexTaskService(indexer, mapper);
        service.setRebuildExecutorForTest(Runnable::run);

        try {
            SearchIndexTaskService.SearchIndexTask task = service.submitRebuildTask(7L);

            assertEquals("SUCCEEDED", task.getStatus());
            assertEquals(42L, task.getCheckpointId());
            assertEquals(42, task.getIndexed());
            assertEquals(0, task.getFailed());
            assertNotNull(task.getFinishedAt());
            verify(mapper).heartbeat(anyString(), anyString(), eq(42L), eq(42), eq(0), eq(42),
                    any(LocalDateTime.class));
            verify(mapper).finish(anyString(), anyString(), eq("SUCCEEDED"), eq(42), eq(0), eq(42),
                    eq("public-posts"), isNull(), eq(42L));
        } finally {
            service.destroy();
        }
    }

    @Test
    void databaseUniqueActiveConflictReturnsConcurrentTask() {
        PostSearchIndexer indexer = mock(PostSearchIndexer.class);
        SearchIndexRebuildTaskMapper mapper = mock(SearchIndexRebuildTaskMapper.class);
        SearchIndexRebuildTaskPO concurrent = pendingTask("concurrent-task", 9L);
        when(mapper.tableExists()).thenReturn(1);
        when(mapper.findActive()).thenReturn(null, concurrent);
        when(mapper.insertPending(any(SearchIndexRebuildTaskPO.class)))
                .thenThrow(new DuplicateKeyException("uk_search_index_rebuild_active"));
        SearchIndexTaskService service = new SearchIndexTaskService(indexer, mapper);

        try {
            SearchIndexTaskService.SearchIndexTask task = service.submitRebuildTask(7L);

            assertEquals("concurrent-task", task.getTaskId());
            assertEquals("PENDING", task.getStatus());
            assertEquals(9L, task.getOperatorUid());
            verify(indexer, never()).rebuildAll(any(PostSearchIndexer.RebuildProgressListener.class));
        } finally {
            service.destroy();
        }
    }

    @Test
    void maintenanceFailsExpiredRunningLeaseWithoutReclaimingIt() {
        PostSearchIndexer indexer = mock(PostSearchIndexer.class);
        SearchIndexRebuildTaskMapper mapper = mock(SearchIndexRebuildTaskMapper.class);
        when(mapper.tableExists()).thenReturn(1);
        when(mapper.failExpiredLease()).thenReturn(1);
        when(mapper.findPending()).thenReturn(null);
        SearchIndexTaskService service = new SearchIndexTaskService(indexer, mapper);

        try {
            service.dispatchPendingTasks();

            verify(mapper).failExpiredLease();
            verify(mapper).findPending();
            verify(mapper, never()).markRunning(anyString(), anyString(), any(LocalDateTime.class));
        } finally {
            service.destroy();
        }
    }

    private static SearchIndexRebuildTaskPO pendingTask(String taskId, Long operatorUid) {
        SearchIndexRebuildTaskPO task = new SearchIndexRebuildTaskPO();
        task.setTaskId(taskId);
        task.setTaskType("POST_INDEX_REBUILD");
        task.setOperatorUid(operatorUid);
        initializePending(task);
        return task;
    }

    private static void initializePending(SearchIndexRebuildTaskPO task) {
        task.setTaskStatus("PENDING");
        task.setCheckpointId(0L);
        task.setIndexedCount(0);
        task.setFailedCount(0);
        task.setTotalCount(0);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(task.getCreateTime());
    }
}
