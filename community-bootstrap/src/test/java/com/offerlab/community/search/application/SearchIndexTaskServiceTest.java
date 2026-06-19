package com.offerlab.community.search.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchIndexTaskServiceTest {
    private static final String REDIS_ACTIVE_REBUILD_KEY = "offerlab:search:index:rebuild:active";

    @Mock
    private PostSearchIndexer indexer;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private SearchIndexTaskService service;

    @BeforeEach
    void setUp() {
        service = new SearchIndexTaskService(indexer);
        service.setRebuildExecutorForTest(command -> {
        });
    }

    @Test
    void submitRebuildTaskCreatesPendingTaskBeforeAsyncWorkStarts() {
        SearchIndexTaskService.SearchIndexTask task = service.submitRebuildTask(7L);

        assertNotNull(task.getTaskId());
        assertEquals("POST_INDEX_REBUILD", task.getType());
        assertEquals("PENDING", task.getStatus());
        assertEquals(7L, task.getOperatorUid());
        assertEquals(1, service.listRecentTasks(10).size());
        verify(indexer, never()).rebuildAll();
    }

    @Test
    void submitRebuildTaskReturnsActiveTaskInsteadOfCreatingDuplicate() {
        SearchIndexTaskService.SearchIndexTask first = service.submitRebuildTask(7L);
        SearchIndexTaskService.SearchIndexTask second = service.submitRebuildTask(8L);

        assertEquals(first.getTaskId(), second.getTaskId());
        assertEquals("PENDING", second.getStatus());
        assertEquals(7L, second.getOperatorUid());
        assertEquals(1, service.listRecentTasks(10).size());
        verify(indexer, never()).rebuildAll();
    }

    @Test
    void submitRebuildTaskCreatesNewTaskAfterPreviousTaskCompletes() {
        when(indexer.rebuildAll()).thenReturn(Map.of(
                "accepted", true,
                "indexed", 2,
                "failed", 0,
                "total", 2,
                "indexName", "post-index"));
        service.setRebuildExecutorForTest(Runnable::run);

        SearchIndexTaskService.SearchIndexTask first = service.submitRebuildTask(7L);
        SearchIndexTaskService.SearchIndexTask second = service.submitRebuildTask(8L);

        assertEquals("SUCCEEDED", first.getStatus());
        assertEquals("SUCCEEDED", second.getStatus());
        assertNotEquals(first.getTaskId(), second.getTaskId());
        assertEquals(2, service.listRecentTasks(10).size());
        verify(indexer, times(2)).rebuildAll();
    }

    @Test
    void submitRebuildTaskReturnsRemoteActiveTaskWhenRedisGateIsClaimedElsewhere() {
        service = new SearchIndexTaskService(indexer, redis);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(REDIS_ACTIVE_REBUILD_KEY), anyString(), any(Duration.class))).thenReturn(false);
        when(valueOps.get(REDIS_ACTIVE_REBUILD_KEY)).thenReturn("remote-task-1");

        SearchIndexTaskService.SearchIndexTask task = service.submitRebuildTask(7L);

        assertEquals("remote-task-1", task.getTaskId());
        assertEquals("POST_INDEX_REBUILD", task.getType());
        assertEquals("RUNNING", task.getStatus());
        assertEquals(0, service.listRecentTasks(10).size());
        verify(indexer, never()).rebuildAll();
    }

    @Test
    void submitRebuildTaskFailsClosedWhenRedisGateIsUnavailable() {
        service = new SearchIndexTaskService(indexer, redis);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(REDIS_ACTIVE_REBUILD_KEY), anyString(), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis down"));

        BizException error = assertThrows(BizException.class, () -> service.submitRebuildTask(7L));

        assertEquals(ErrorCode.CACHE_ERROR.getCode(), error.getCode());
        verifyNoInteractions(indexer);
    }

    @Test
    void submitRebuildTaskReleasesGateAndClearsLocalTaskWhenExecutorRejects() {
        service = new SearchIndexTaskService(indexer, redis);
        service.setRebuildExecutorForTest(command -> {
            throw new RejectedExecutionException("executor closed");
        });
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(REDIS_ACTIVE_REBUILD_KEY), anyString(), any(Duration.class))).thenReturn(true);

        BizException error = assertThrows(BizException.class, () -> service.submitRebuildTask(7L));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), error.getCode());
        assertEquals(0, service.listRecentTasks(10).size());
        verify(redis).execute(any(RedisCallback.class));
        verify(indexer, never()).rebuildAll();
    }
}
