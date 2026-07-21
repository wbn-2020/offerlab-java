package com.offerlab.community.search.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.search.infrastructure.persistence.mapper.SearchIndexRebuildTaskMapper;
import com.offerlab.community.search.infrastructure.persistence.po.SearchIndexRebuildTaskPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchIndexTaskServiceTest {

    @Mock
    private PostSearchIndexer indexer;
    @Mock
    private SearchIndexRebuildTaskMapper taskMapper;

    private SearchIndexTaskService service;
    private AtomicReference<SearchIndexRebuildTaskPO> stored;

    @BeforeEach
    void setUp() {
        stored = new AtomicReference<>();
        lenient().when(taskMapper.tableExists()).thenReturn(1);
        lenient().when(taskMapper.findActive()).thenAnswer(invocation -> {
            SearchIndexRebuildTaskPO task = stored.get();
            return task != null && List.of("PENDING", "RUNNING").contains(task.getTaskStatus()) ? task : null;
        });
        lenient().when(taskMapper.insertPending(any())).thenAnswer(invocation -> {
            SearchIndexRebuildTaskPO task = invocation.getArgument(0);
            initializePending(task);
            stored.set(task);
            return 1;
        });
        lenient().when(taskMapper.findByTaskId(anyString())).thenAnswer(invocation -> {
            SearchIndexRebuildTaskPO task = stored.get();
            return task != null && task.getTaskId().equals(invocation.getArgument(0)) ? task : null;
        });
        lenient().when(taskMapper.listRecent(anyInt())).thenAnswer(invocation ->
                stored.get() == null ? List.of() : List.of(stored.get()));
        service = new SearchIndexTaskService(indexer, taskMapper);
        service.setRebuildExecutorForTest(command -> {
        });
    }

    @Test
    void submitRebuildTaskPersistsPendingBeforeAsyncWorkStarts() {
        SearchIndexTaskService.SearchIndexTask task = service.submitRebuildTask(7L);

        assertNotNull(task.getTaskId());
        assertEquals("POST_INDEX_REBUILD", task.getType());
        assertEquals("PENDING", task.getStatus());
        assertEquals(7L, task.getOperatorUid());
        assertEquals(1, service.listRecentTasks(10).size());
        verify(taskMapper).insertPending(any(SearchIndexRebuildTaskPO.class));
        verify(indexer, never()).rebuildAll(any(PostSearchIndexer.RebuildProgressListener.class));
    }

    @Test
    void submitRebuildTaskReturnsDatabaseActiveTaskInsteadOfCreatingDuplicate() {
        SearchIndexTaskService.SearchIndexTask first = service.submitRebuildTask(7L);
        SearchIndexTaskService.SearchIndexTask second = service.submitRebuildTask(8L);

        assertEquals(first.getTaskId(), second.getTaskId());
        assertEquals("PENDING", second.getStatus());
        assertEquals(7L, second.getOperatorUid());
        verify(taskMapper, times(1)).insertPending(any(SearchIndexRebuildTaskPO.class));
        verify(indexer, never()).rebuildAll(any(PostSearchIndexer.RebuildProgressListener.class));
    }

    @Test
    void completedTaskAllowsNextPersistentRebuild() {
        when(taskMapper.markRunning(anyString(), anyString(), any(LocalDateTime.class))).thenAnswer(invocation -> {
            SearchIndexRebuildTaskPO task = stored.get();
            task.setTaskStatus("RUNNING");
            return 1;
        });
        when(taskMapper.heartbeat(anyString(), anyString(), eq(2L), eq(2), eq(0), eq(2),
                any(LocalDateTime.class))).thenReturn(1);
        when(indexer.rebuildAll(any(PostSearchIndexer.RebuildProgressListener.class))).thenAnswer(invocation -> {
            PostSearchIndexer.RebuildProgressListener listener = invocation.getArgument(0);
            listener.onProgress(new PostSearchIndexer.RebuildProgress(2L, 2, 0, 2));
            return Map.of(
                    "accepted", true,
                    "indexed", 2,
                    "failed", 0,
                    "total", 2,
                    "checkpointId", 2L,
                    "indexName", "post-index");
        });
        when(taskMapper.finish(anyString(), anyString(), eq("SUCCEEDED"), eq(2), eq(0), eq(2),
                eq("post-index"), isNull(), eq(2L))).thenAnswer(invocation -> {
            SearchIndexRebuildTaskPO task = stored.get();
            task.setTaskStatus("SUCCEEDED");
            task.setCheckpointId(2L);
            task.setIndexedCount(2);
            task.setTotalCount(2);
            task.setIndexName("post-index");
            task.setFinishedAt(LocalDateTime.now());
            return 1;
        });
        service.setRebuildExecutorForTest(Runnable::run);

        SearchIndexTaskService.SearchIndexTask first = service.submitRebuildTask(7L);
        String firstTaskId = first.getTaskId();
        SearchIndexTaskService.SearchIndexTask second = service.submitRebuildTask(8L);

        assertEquals("SUCCEEDED", first.getStatus());
        assertEquals("SUCCEEDED", second.getStatus());
        assertNotEquals(firstTaskId, second.getTaskId());
        verify(indexer, times(2)).rebuildAll(any(PostSearchIndexer.RebuildProgressListener.class));
    }

    @Test
    void submitRebuildTaskFailsClosedWhenPersistentTableIsUnavailable() {
        when(taskMapper.tableExists()).thenReturn(0);

        BizException error = assertThrows(BizException.class, () -> service.submitRebuildTask(7L));

        assertEquals(ErrorCode.DATABASE_ERROR.getCode(), error.getCode());
        verify(taskMapper, never()).insertPending(any(SearchIndexRebuildTaskPO.class));
        verify(indexer, never()).rebuildAll(any(PostSearchIndexer.RebuildProgressListener.class));
    }

    @Test
    void executorRejectionLeavesPersistentPendingTaskForLaterRecovery() {
        service.setRebuildExecutorForTest(command -> {
            throw new RejectedExecutionException("executor closed");
        });

        SearchIndexTaskService.SearchIndexTask task = service.submitRebuildTask(7L);

        assertEquals("PENDING", task.getStatus());
        assertEquals(task.getTaskId(), stored.get().getTaskId());
        verify(taskMapper, never()).markRunning(anyString(), anyString(), any(LocalDateTime.class));
        verify(indexer, never()).rebuildAll(any(PostSearchIndexer.RebuildProgressListener.class));
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
