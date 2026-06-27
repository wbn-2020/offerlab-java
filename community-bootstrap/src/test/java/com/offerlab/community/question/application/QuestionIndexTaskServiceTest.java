package com.offerlab.community.question.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.question.infrastructure.persistence.mapper.QuestionIndexTaskMapper;
import com.offerlab.community.question.infrastructure.persistence.po.QuestionIndexTaskPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionIndexTaskServiceTest {
    private static final String REDIS_ACTIVE_REBUILD_KEY = "offerlab:question:index:rebuild:active";

    @Mock
    private QuestionSearchIndexer indexer;
    @Mock
    private QuestionIndexTaskMapper taskMapper;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private QuestionIndexTaskService service;

    @BeforeEach
    void setUp() {
        service = new QuestionIndexTaskService(indexer, taskMapper);
        service.setRebuildExecutorForTest(command -> {
        });
    }

    @Test
    void defaultExecutorMustNotUseForkJoinCommonPool() throws Exception {
        Field field = QuestionIndexTaskService.class.getDeclaredField("rebuildExecutor");
        field.setAccessible(true);

        Object executor = field.get(service);

        assertNotSame(ForkJoinPool.commonPool(), executor);
    }

    @Test
    void submitRebuildTaskPersistsPendingTask() {
        AtomicReference<QuestionIndexTaskPO> saved = new AtomicReference<>();
        when(taskMapper.tableExists()).thenReturn(1);
        when(taskMapper.findActiveRebuildTask("QUESTION_INDEX_REBUILD")).thenReturn(null);
        doAnswer(invocation -> {
            QuestionIndexTaskPO task = invocation.getArgument(0);
            saved.set(task);
            return 1;
        }).when(taskMapper).insertTask(any(QuestionIndexTaskPO.class));
        when(taskMapper.findByTaskId(anyString())).thenAnswer(invocation -> saved.get());

        QuestionIndexTaskService.QuestionIndexTask task = service.submitRebuildTask(99L);

        assertNotNull(task.getTaskId());
        assertEquals("QUESTION_INDEX_REBUILD", task.getType());
        assertEquals("PENDING", task.getStatus());
        assertEquals(99L, task.getOperatorUid());
        assertFalse(task.isRetryable());
    }

    @Test
    void submitRebuildTaskReturnsActiveTaskWithoutInsert() {
        QuestionIndexTaskPO active = task("task-active", "RUNNING");
        active.setOperatorUid(100L);
        when(taskMapper.tableExists()).thenReturn(1);
        when(taskMapper.findActiveRebuildTask("QUESTION_INDEX_REBUILD")).thenReturn(active);

        QuestionIndexTaskService.QuestionIndexTask task = service.submitRebuildTask(101L);

        assertEquals("task-active", task.getTaskId());
        assertEquals("RUNNING", task.getStatus());
        assertEquals(100L, task.getOperatorUid());
        verify(taskMapper, never()).insertTask(any(QuestionIndexTaskPO.class));
    }

    @Test
    void getTaskReturnsNullWhenTableIsMissing() {
        when(taskMapper.tableExists()).thenReturn(0);

        assertNull(service.getTask("task-1"));
    }

    @Test
    void failedTaskSnapshotIsRetryable() {
        when(taskMapper.tableExists()).thenReturn(1);
        when(taskMapper.findByTaskId("task-1")).thenReturn(task("task-1", "FAILED"));

        QuestionIndexTaskService.QuestionIndexTask task = service.getTask("task-1");

        assertEquals("FAILED", task.getStatus());
        assertTrue(task.isRetryable());
    }

    @Test
    void retryTaskMovesFailedTaskBackToPending() {
        QuestionIndexTaskPO failed = task("task-1", "FAILED");
        QuestionIndexTaskPO pending = task("task-1", "PENDING");
        when(taskMapper.tableExists()).thenReturn(1);
        when(taskMapper.findByTaskId("task-1")).thenReturn(failed, pending);
        when(taskMapper.markRetry("task-1")).thenReturn(1);

        QuestionIndexTaskService.QuestionIndexTask task = service.retryTask("task-1");

        assertEquals("PENDING", task.getStatus());
        assertFalse(task.isRetryable());
        verify(taskMapper).markRetry("task-1");
    }

    @Test
    void submitRebuildTaskReturnsRemoteActiveTaskWhenRedisGateIsClaimedElsewhere() {
        service = newServiceWithRedis();
        service.setRebuildExecutorForTest(command -> {
        });
        when(taskMapper.tableExists()).thenReturn(1);
        when(taskMapper.findActiveRebuildTask("QUESTION_INDEX_REBUILD")).thenReturn(null);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(REDIS_ACTIVE_REBUILD_KEY), anyString(), any(Duration.class))).thenReturn(false);
        when(valueOps.get(REDIS_ACTIVE_REBUILD_KEY)).thenReturn("remote-task-1");

        QuestionIndexTaskService.QuestionIndexTask task = service.submitRebuildTask(7L);

        assertEquals("remote-task-1", task.getTaskId());
        assertEquals("QUESTION_INDEX_REBUILD", task.getType());
        assertEquals("RUNNING", task.getStatus());
        verify(taskMapper, never()).insertTask(any(QuestionIndexTaskPO.class));
        verify(indexer, never()).rebuildAll();
    }

    @Test
    void submitRebuildTaskFailsClosedWhenRedisGateIsUnavailable() {
        service = newServiceWithRedis();
        service.setRebuildExecutorForTest(command -> {
        });
        when(taskMapper.tableExists()).thenReturn(1);
        when(taskMapper.findActiveRebuildTask("QUESTION_INDEX_REBUILD")).thenReturn(null);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(REDIS_ACTIVE_REBUILD_KEY), anyString(), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis down"));

        BizException error = assertThrows(BizException.class, () -> service.submitRebuildTask(7L));

        assertEquals(ErrorCode.CACHE_ERROR.getCode(), error.getCode());
        verify(taskMapper, never()).insertTask(any(QuestionIndexTaskPO.class));
        verifyNoInteractions(indexer);
    }

    @Test
    void submitRebuildTaskReleasesGateAndMarksTaskFailedWhenExecutorRejects() {
        service = newServiceWithRedis();
        service.setRebuildExecutorForTest(command -> {
            throw new RejectedExecutionException("executor closed");
        });
        AtomicReference<QuestionIndexTaskPO> saved = new AtomicReference<>();
        when(taskMapper.tableExists()).thenReturn(1);
        when(taskMapper.findActiveRebuildTask("QUESTION_INDEX_REBUILD")).thenReturn(null);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(REDIS_ACTIVE_REBUILD_KEY), anyString(), any(Duration.class))).thenReturn(true);
        doAnswer(invocation -> {
            QuestionIndexTaskPO task = invocation.getArgument(0);
            saved.set(task);
            return 1;
        }).when(taskMapper).insertTask(any(QuestionIndexTaskPO.class));

        BizException error = assertThrows(BizException.class, () -> service.submitRebuildTask(7L));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), error.getCode());
        verify(taskMapper).finish(anyString(), eq("FAILED"), eq(0), eq(0), eq(0), eq(0), isNull(), contains("schedule"));
        verify(redis).execute(any(RedisCallback.class));
        verify(indexer, never()).rebuildAll();
        assertNotNull(saved.get());
    }

    @Test
    void runRebuildMarksSucceededWhenIndexerAcceptsAllRows() {
        when(taskMapper.tableExists()).thenReturn(1);
        when(taskMapper.markRunning("task-1")).thenReturn(1);
        when(indexer.rebuildAll()).thenReturn(Map.of(
                "accepted", true,
                "indexed", 3,
                "failed", 0,
                "total", 3,
                "indexName", "question-index"));

        service.runRebuildForTest("task-1");

        verify(taskMapper).finish("task-1", "SUCCEEDED", 1, 3, 0, 3, "question-index", null);
    }

    @Test
    void runRebuildMarksFailedWhenIndexerReportsFailures() {
        when(taskMapper.tableExists()).thenReturn(1);
        when(taskMapper.markRunning("task-1")).thenReturn(1);
        when(indexer.rebuildAll()).thenReturn(Map.of(
                "accepted", true,
                "indexed", 2,
                "failed", 1,
                "total", 3,
                "indexName", "question-index",
                "message", "one row failed"));

        service.runRebuildForTest("task-1");

        verify(taskMapper).finish("task-1", "FAILED", 1, 2, 1, 3, "question-index", "one row failed");
    }

    @Test
    void runRebuildMarksFailedWhenIndexerThrows() {
        when(taskMapper.tableExists()).thenReturn(1);
        when(taskMapper.markRunning("task-1")).thenReturn(1);
        when(indexer.rebuildAll()).thenThrow(new IllegalStateException("boom"));

        service.runRebuildForTest("task-1");

        verify(taskMapper).finish(eq("task-1"), eq("FAILED"), eq(0), eq(0), eq(0), eq(0), isNull(), contains("boom"));
    }

    private static QuestionIndexTaskPO task(String taskId, String status) {
        QuestionIndexTaskPO task = new QuestionIndexTaskPO();
        task.setTaskId(taskId);
        task.setTaskType("QUESTION_INDEX_REBUILD");
        task.setTaskStatus(status);
        task.setOperatorUid(99L);
        task.setAccepted(0);
        task.setIndexed(0);
        task.setFailed(0);
        task.setTotal(0);
        return task;
    }

    private QuestionIndexTaskService newServiceWithRedis() {
        try {
            Constructor<QuestionIndexTaskService> constructor = QuestionIndexTaskService.class
                    .getDeclaredConstructor(QuestionSearchIndexer.class, QuestionIndexTaskMapper.class, StringRedisTemplate.class);
            constructor.setAccessible(true);
            return constructor.newInstance(indexer, taskMapper, redis);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Expected QuestionIndexTaskService to expose a redis-aware constructor", e);
        }
    }
}
