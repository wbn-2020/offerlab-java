package com.offerlab.community.search.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SearchIndexTaskService implements DisposableBean {
    private static final int MAX_RETAINED_TASKS = 100;
    private static final String TYPE_REBUILD = "POST_INDEX_REBUILD";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String REDIS_ACTIVE_REBUILD_KEY = "offerlab:search:index:rebuild:active";
    private static final Duration ACTIVE_REBUILD_TTL = Duration.ofHours(6);

    private final PostSearchIndexer indexer;
    private final StringRedisTemplate redis;
    private final Map<String, SearchIndexTask> tasks = new ConcurrentHashMap<>();
    private final Object rebuildSubmitLock = new Object();
    private final ExecutorService ownedRebuildExecutor;
    private Executor rebuildExecutor;

    public SearchIndexTaskService(PostSearchIndexer indexer) {
        this(indexer, null);
    }

    @Autowired
    public SearchIndexTaskService(PostSearchIndexer indexer, @Nullable StringRedisTemplate redis) {
        this.indexer = indexer;
        this.redis = redis;
        this.ownedRebuildExecutor = defaultRebuildExecutor();
        this.rebuildExecutor = ownedRebuildExecutor;
    }

    public SearchIndexTask submitRebuildTask(Long operatorUid) {
        SearchIndexTask task;
        synchronized (rebuildSubmitLock) {
            SearchIndexTask activeTask = findActiveRebuildTask();
            if (activeTask != null) {
                return snapshot(activeTask);
            }
            String taskId = UUID.randomUUID().toString();
            if (!tryClaimDistributedActiveTask(taskId)) {
                return remoteActiveSnapshot(currentDistributedActiveTaskId(), operatorUid);
            }
            task = SearchIndexTask.builder()
                    .taskId(taskId)
                    .type(TYPE_REBUILD)
                    .status(STATUS_PENDING)
                    .operatorUid(operatorUid)
                    .indexed(0)
                    .failed(0)
                    .total(0)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            tasks.put(taskId, task);
            pruneOldTasks();
        }

        try {
            CompletableFuture.runAsync(() -> runRebuild(task.getTaskId()), rebuildExecutor);
        } catch (RuntimeException e) {
            tasks.remove(task.getTaskId());
            releaseDistributedActiveTask(task.getTaskId());
            log.error("search index rebuild task scheduling failed: taskId={}", task.getTaskId(), e);
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "Search index rebuild task could not be scheduled");
        }
        return snapshot(task);
    }

    public SearchIndexTask getTask(String taskId) {
        SearchIndexTask task = tasks.get(taskId);
        return task == null ? null : snapshot(task);
    }

    public List<SearchIndexTask> listRecentTasks(int limit) {
        return tasks.values().stream()
                .sorted(Comparator.comparing(SearchIndexTask::getCreatedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 20)))
                .map(SearchIndexTaskService::snapshot)
                .toList();
    }

    private void runRebuild(String taskId) {
        SearchIndexTask task = tasks.get(taskId);
        if (task == null) {
            return;
        }
        task.setStatus(STATUS_RUNNING);
        task.setUpdatedAt(LocalDateTime.now());
        try {
            Map<String, Object> result = indexer.rebuildAll();
            task.setAccepted(Boolean.TRUE.equals(result.get("accepted")));
            task.setIndexed(asInt(result.get("indexed")));
            task.setFailed(asInt(result.get("failed")));
            task.setTotal(asInt(result.get("total")));
            task.setIndexName(asString(result.get("indexName")));
            task.setMessage(asString(result.get("message")));
            task.setStatus(task.isAccepted() ? STATUS_SUCCEEDED : STATUS_FAILED);
        } catch (Exception e) {
            log.error("search index rebuild task failed: taskId={}", taskId, e);
            task.setAccepted(false);
            task.setStatus(STATUS_FAILED);
            task.setMessage(e.getMessage());
        } finally {
            task.setUpdatedAt(LocalDateTime.now());
            releaseDistributedActiveTask(taskId);
        }
    }

    void setRebuildExecutorForTest(Executor rebuildExecutor) {
        this.rebuildExecutor = rebuildExecutor == null ? ownedRebuildExecutor : rebuildExecutor;
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
            Thread thread = new Thread(runnable, "offerlab-search-index-rebuild");
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

    private SearchIndexTask findActiveRebuildTask() {
        return tasks.values().stream()
                .filter(task -> TYPE_REBUILD.equals(task.getType()))
                .filter(task -> isActiveStatus(task.getStatus()))
                .max(Comparator.comparing(SearchIndexTask::getCreatedAt))
                .orElse(null);
    }

    private static boolean isActiveStatus(String status) {
        return STATUS_PENDING.equals(status) || STATUS_RUNNING.equals(status);
    }

    private boolean tryClaimDistributedActiveTask(String taskId) {
        if (redis == null) {
            return true;
        }
        try {
            Boolean claimed = redis.opsForValue().setIfAbsent(REDIS_ACTIVE_REBUILD_KEY, taskId, ACTIVE_REBUILD_TTL);
            return Boolean.TRUE.equals(claimed);
        } catch (Exception e) {
            log.error("search index rebuild distributed gate unavailable", e);
            throw new BizException(ErrorCode.CACHE_ERROR.getCode(), "Search index rebuild gate is temporarily unavailable");
        }
    }

    private String currentDistributedActiveTaskId() {
        if (redis == null) {
            return null;
        }
        try {
            return redis.opsForValue().get(REDIS_ACTIVE_REBUILD_KEY);
        } catch (Exception e) {
            log.warn("search index rebuild distributed gate read failed", e);
            return null;
        }
    }

    private SearchIndexTask remoteActiveSnapshot(String taskId, Long operatorUid) {
        SearchIndexTask localTask = taskId == null ? null : tasks.get(taskId);
        if (localTask != null) {
            return snapshot(localTask);
        }
        return SearchIndexTask.builder()
                .taskId(taskId == null || taskId.isBlank() ? "remote-active" : taskId)
                .type(TYPE_REBUILD)
                .status(STATUS_RUNNING)
                .operatorUid(operatorUid)
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
            log.warn("search index rebuild distributed gate release failed: taskId={}", taskId, e);
        }
    }

    private void pruneOldTasks() {
        int overflow = tasks.size() - MAX_RETAINED_TASKS;
        if (overflow <= 0) {
            return;
        }
        tasks.values().stream()
                .sorted(Comparator.comparing(SearchIndexTask::getCreatedAt))
                .limit(overflow)
                .map(SearchIndexTask::getTaskId)
                .forEach(tasks::remove);
    }

    private static SearchIndexTask snapshot(SearchIndexTask task) {
        return SearchIndexTask.builder()
                .taskId(task.getTaskId())
                .type(task.getType())
                .status(task.getStatus())
                .operatorUid(task.getOperatorUid())
                .accepted(task.isAccepted())
                .indexed(task.getIndexed())
                .failed(task.getFailed())
                .total(task.getTotal())
                .indexName(task.getIndexName())
                .message(task.getMessage())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @Data
    @Builder
    public static class SearchIndexTask {
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
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
