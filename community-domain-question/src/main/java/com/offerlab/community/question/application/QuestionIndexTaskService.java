package com.offerlab.community.question.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.question.infrastructure.persistence.mapper.QuestionIndexTaskMapper;
import com.offerlab.community.question.infrastructure.persistence.po.QuestionIndexTaskPO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class QuestionIndexTaskService implements DisposableBean {
    private static final String TYPE_REBUILD = "QUESTION_INDEX_REBUILD";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String REDIS_ACTIVE_REBUILD_KEY = "offerlab:question:index:rebuild:active";
    private static final Duration ACTIVE_REBUILD_TTL = Duration.ofHours(6);

    private final QuestionSearchIndexer indexer;
    private final QuestionIndexTaskMapper taskMapper;
    private final StringRedisTemplate redis;
    private final Object rebuildSubmitLock = new Object();
    private final ExecutorService ownedRebuildExecutor;
    private Executor rebuildExecutor;

    public QuestionIndexTaskService(QuestionSearchIndexer indexer, QuestionIndexTaskMapper taskMapper) {
        this(indexer, taskMapper, null);
    }

    @Autowired
    public QuestionIndexTaskService(QuestionSearchIndexer indexer,
                                    QuestionIndexTaskMapper taskMapper,
                                    @Nullable StringRedisTemplate redis) {
        this.indexer = indexer;
        this.taskMapper = taskMapper;
        this.redis = redis;
        this.ownedRebuildExecutor = defaultRebuildExecutor();
        this.rebuildExecutor = ownedRebuildExecutor;
    }

    public QuestionIndexTask submitRebuildTask(Long operatorUid) {
        ensureTableReady();
        QuestionIndexTaskPO task;
        synchronized (rebuildSubmitLock) {
            QuestionIndexTaskPO activeTask = taskMapper.findActiveRebuildTask(TYPE_REBUILD);
            if (activeTask != null) {
                return snapshot(activeTask);
            }
            String taskId = UUID.randomUUID().toString();
            if (!tryClaimDistributedActiveTask(taskId)) {
                return activeTaskSnapshotOrRemote(operatorUid);
            }
            try {
                QuestionIndexTaskPO distributedActiveTask = taskMapper.findActiveRebuildTask(TYPE_REBUILD);
                if (distributedActiveTask != null) {
                    releaseDistributedActiveTask(taskId);
                    return snapshot(distributedActiveTask);
                }
                task = newPendingTask(taskId, operatorUid);
                taskMapper.insertTask(task);
            } catch (RuntimeException e) {
                releaseDistributedActiveTask(taskId);
                throw e;
            }
        }
        try {
            CompletableFuture.runAsync(() -> runRebuild(task.getTaskId()), rebuildExecutor);
        } catch (RuntimeException e) {
            handleSchedulingFailure(task.getTaskId(), e);
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "Question index rebuild task could not be scheduled");
        }
        return snapshot(taskMapper.findByTaskId(task.getTaskId()));
    }

    public QuestionIndexTask retryTask(String taskId) {
        ensureTableReady();
        QuestionIndexTaskPO existing = taskMapper.findByTaskId(taskId);
        if (existing == null) {
            return null;
        }
        if (STATUS_FAILED.equals(existing.getTaskStatus())) {
            if (!tryClaimDistributedActiveTask(taskId)) {
                QuestionIndexTaskPO activeTask = taskMapper.findActiveRebuildTask(TYPE_REBUILD);
                return activeTask == null ? snapshot(existing) : snapshot(activeTask);
            }
            try {
                QuestionIndexTaskPO activeTask = taskMapper.findActiveRebuildTask(TYPE_REBUILD);
                if (activeTask != null && !taskId.equals(activeTask.getTaskId())) {
                    releaseDistributedActiveTask(taskId);
                    return snapshot(activeTask);
                }
                if (taskMapper.markRetry(taskId) > 0) {
                    try {
                        CompletableFuture.runAsync(() -> runRebuild(taskId), rebuildExecutor);
                    } catch (RuntimeException e) {
                        handleSchedulingFailure(taskId, e);
                        throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "Question index rebuild task could not be scheduled");
                    }
                } else {
                    releaseDistributedActiveTask(taskId);
                }
            } catch (RuntimeException e) {
                releaseDistributedActiveTask(taskId);
                throw e;
            }
        }
        return snapshot(taskMapper.findByTaskId(taskId));
    }

    public QuestionIndexTask getTask(String taskId) {
        if (!tableReady()) {
            return null;
        }
        return snapshot(taskMapper.findByTaskId(taskId));
    }

    public List<QuestionIndexTask> listRecentTasks(int limit) {
        if (!tableReady()) {
            return List.of();
        }
        return taskMapper.listRecent(Math.max(1, Math.min(limit, 20))).stream()
                .map(QuestionIndexTaskService::snapshot)
                .toList();
    }

    private void runRebuild(String taskId) {
        if (!tableReady()) {
            releaseDistributedActiveTask(taskId);
            return;
        }
        if (taskMapper.markRunning(taskId) == 0) {
            releaseDistributedActiveTask(taskId);
            return;
        }
        try {
            Map<String, Object> result = indexer.rebuildAll();
            boolean accepted = Boolean.TRUE.equals(result.get("accepted"));
            int failed = asInt(result.get("failed"));
            String status = accepted && failed == 0 ? STATUS_SUCCEEDED : STATUS_FAILED;
            taskMapper.finish(taskId, status, accepted ? 1 : 0, asInt(result.get("indexed")), failed,
                    asInt(result.get("total")), asString(result.get("indexName")), asString(result.get("message")));
        } catch (Exception e) {
            log.error("question index rebuild task failed: taskId={}", taskId, e);
            taskMapper.finish(taskId, STATUS_FAILED, 0, 0, 0, 0, null, shortMessage(e));
        } finally {
            releaseDistributedActiveTask(taskId);
        }
    }

    void runRebuildForTest(String taskId) {
        runRebuild(taskId);
    }

    void setRebuildExecutorForTest(Executor rebuildExecutor) {
        this.rebuildExecutor = rebuildExecutor == null ? ownedRebuildExecutor : rebuildExecutor;
    }

    private void ensureTableReady() {
        if (!tableReady()) {
            throw new IllegalStateException("Question index task table is not ready");
        }
    }

    private boolean tableReady() {
        try {
            if (taskMapper.tableExists() <= 0) {
                return false;
            }
            taskMapper.listRecent(1);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void destroy() {
        ownedRebuildExecutor.shutdown();
        try {
            if (!ownedRebuildExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ownedRebuildExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ownedRebuildExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static ExecutorService defaultRebuildExecutor() {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "offerlab-question-index-rebuild");
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    private boolean tryClaimDistributedActiveTask(String taskId) {
        if (redis == null) {
            return true;
        }
        try {
            Boolean claimed = redis.opsForValue().setIfAbsent(REDIS_ACTIVE_REBUILD_KEY, taskId, ACTIVE_REBUILD_TTL);
            return Boolean.TRUE.equals(claimed);
        } catch (Exception e) {
            log.error("question index rebuild distributed gate unavailable", e);
            throw new BizException(ErrorCode.CACHE_ERROR.getCode(), "Question index rebuild gate is temporarily unavailable");
        }
    }

    private QuestionIndexTask activeTaskSnapshotOrRemote(Long operatorUid) {
        QuestionIndexTaskPO activeTask = taskMapper.findActiveRebuildTask(TYPE_REBUILD);
        if (activeTask != null) {
            return snapshot(activeTask);
        }
        return remoteActiveSnapshot(currentDistributedActiveTaskId(), operatorUid);
    }

    private String currentDistributedActiveTaskId() {
        if (redis == null) {
            return null;
        }
        try {
            return redis.opsForValue().get(REDIS_ACTIVE_REBUILD_KEY);
        } catch (Exception e) {
            log.warn("question index rebuild distributed gate read failed", e);
            return null;
        }
    }

    private QuestionIndexTask remoteActiveSnapshot(String taskId, Long operatorUid) {
        return QuestionIndexTask.builder()
                .taskId(taskId == null || taskId.isBlank() ? "remote-active" : taskId)
                .type(TYPE_REBUILD)
                .status(STATUS_RUNNING)
                .operatorUid(operatorUid)
                .accepted(false)
                .indexed(0)
                .failed(0)
                .total(0)
                .message("Another rebuild task is already active")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void releaseDistributedActiveTask(String taskId) {
        if (redis == null || taskId == null) {
            return;
        }
        try {
            redis.execute((RedisCallback<Boolean>) connection -> {
                byte[] key = REDIS_ACTIVE_REBUILD_KEY.getBytes(StandardCharsets.UTF_8);
                byte[] current = connection.stringCommands().get(key);
                if (current == null || !taskId.equals(new String(current, StandardCharsets.UTF_8))) {
                    return false;
                }
                Long deleted = connection.keyCommands().del(key);
                return deleted != null && deleted == 1L;
            });
        } catch (Exception e) {
            log.warn("question index rebuild distributed gate release failed: taskId={}", taskId, e);
        }
    }

    private void handleSchedulingFailure(String taskId, RuntimeException cause) {
        releaseDistributedActiveTask(taskId);
        log.error("question index rebuild task scheduling failed: taskId={}", taskId, cause);
        try {
            taskMapper.finish(taskId, STATUS_FAILED, 0, 0, 0, 0, null, schedulingFailureMessage(cause));
        } catch (RuntimeException finishError) {
            log.error("question index rebuild task scheduling failure could not be persisted: taskId={}", taskId, finishError);
        }
    }

    private static QuestionIndexTaskPO newPendingTask(String taskId, Long operatorUid) {
        QuestionIndexTaskPO task = new QuestionIndexTaskPO();
        task.setTaskId(taskId);
        task.setTaskType(TYPE_REBUILD);
        task.setTaskStatus(STATUS_PENDING);
        task.setOperatorUid(operatorUid);
        task.setAccepted(0);
        task.setIndexed(0);
        task.setFailed(0);
        task.setTotal(0);
        return task;
    }

    private static QuestionIndexTask snapshot(QuestionIndexTaskPO task) {
        if (task == null) {
            return null;
        }
        return QuestionIndexTask.builder()
                .taskId(task.getTaskId())
                .type(task.getTaskType())
                .status(task.getTaskStatus())
                .operatorUid(task.getOperatorUid())
                .accepted(asInt(task.getAccepted()) == 1)
                .indexed(asInt(task.getIndexed()))
                .failed(asInt(task.getFailed()))
                .total(asInt(task.getTotal()))
                .indexName(task.getIndexName())
                .message(task.getMessage())
                .retryable(STATUS_FAILED.equals(task.getTaskStatus()))
                .createdAt(task.getCreateTime())
                .updatedAt(task.getUpdateTime())
                .build();
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String shortMessage(Throwable cause) {
        if (cause == null || cause.getMessage() == null) {
            return "Question index rebuild failed";
        }
        String message = cause.getMessage();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private static String schedulingFailureMessage(Throwable cause) {
        String message = shortMessage(cause);
        String prefix = "Question index rebuild task could not be scheduled";
        return message == null || message.isBlank() ? prefix : prefix + ": " + message;
    }

    @Data
    @Builder
    public static class QuestionIndexTask {
        private String taskId;
        private String type;
        private String status;
        private Long operatorUid;
        private boolean accepted;
        private int indexed;
        private int failed;
        private int total;
        private String indexName;
        private String message;
        private boolean retryable;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
