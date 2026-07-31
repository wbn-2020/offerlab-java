package com.offerlab.community.search.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.search.infrastructure.persistence.mapper.SearchIndexRebuildTaskMapper;
import com.offerlab.community.search.infrastructure.persistence.po.SearchIndexRebuildTaskPO;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SearchIndexTaskService implements DisposableBean {
    private static final String TYPE_REBUILD = "POST_INDEX_REBUILD";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";
    private static final Duration WORKER_LEASE = Duration.ofMinutes(5);

    private final PostSearchIndexer indexer;
    private final SearchIndexRebuildTaskMapper taskMapper;
    private final Set<String> scheduledTaskIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final String owner = buildOwner();
    private final ExecutorService ownedRebuildExecutor;
    private Executor rebuildExecutor;

    public SearchIndexTaskService(PostSearchIndexer indexer) {
        this(indexer, null);
    }

    @Autowired
    public SearchIndexTaskService(PostSearchIndexer indexer,
                                  @Nullable SearchIndexRebuildTaskMapper taskMapper) {
        this.indexer = indexer;
        this.taskMapper = taskMapper;
        this.ownedRebuildExecutor = defaultRebuildExecutor();
        this.rebuildExecutor = ownedRebuildExecutor;
    }

    public SearchIndexTask submitRebuildTask(Long operatorUid) {
        requireTableReady();
        failExpiredLeases();
        SearchIndexRebuildTaskPO active;
        try {
            active = taskMapper.findActive();
        } catch (RuntimeException e) {
            throw databaseFailure("Search index rebuild active task could not be read", e);
        }
        if (active != null) {
            return toTask(active);
        }

        SearchIndexRebuildTaskPO pending = new SearchIndexRebuildTaskPO();
        pending.setTaskId(UUID.randomUUID().toString());
        pending.setTaskType(TYPE_REBUILD);
        pending.setOperatorUid(operatorUid);
        try {
            if (taskMapper.insertPending(pending) <= 0) {
                throw new IllegalStateException("Search index rebuild insert affected no rows");
            }
        } catch (DataIntegrityViolationException duplicateActive) {
            SearchIndexRebuildTaskPO concurrent;
            try {
                concurrent = taskMapper.findActive();
            } catch (RuntimeException readFailure) {
                duplicateActive.addSuppressed(readFailure);
                throw databaseFailure("Concurrent search index rebuild task could not be read", duplicateActive);
            }
            if (concurrent != null) {
                return toTask(concurrent);
            }
            throw databaseFailure("Concurrent search index rebuild task could not be resolved", duplicateActive);
        } catch (RuntimeException e) {
            throw databaseFailure("Search index rebuild task could not be persisted", e);
        }

        schedulePendingTask(pending.getTaskId());
        SearchIndexRebuildTaskPO stored = taskMapper.findByTaskId(pending.getTaskId());
        return toTask(stored == null ? pendingSnapshot(pending) : stored);
    }

    public SearchIndexTask getTask(String taskId) {
        if (!tableReady() || taskId == null || taskId.isBlank()) {
            return null;
        }
        failExpiredLeases();
        SearchIndexRebuildTaskPO task = taskMapper.findByTaskId(taskId);
        return task == null ? null : toTask(task);
    }

    public List<SearchIndexTask> listRecentTasks(int limit) {
        if (!tableReady()) {
            return List.of();
        }
        failExpiredLeases();
        int safeLimit = Math.max(1, Math.min(limit, 20));
        List<SearchIndexRebuildTaskPO> tasks = taskMapper.listRecent(safeLimit);
        return tasks == null ? List.of() : tasks.stream().map(SearchIndexTaskService::toTask).toList();
    }

    @Scheduled(fixedDelayString = "${offerlab.search.rebuild.poll-delay-ms:5000}")
    public void dispatchPendingTasks() {
        if (!tableReady()) {
            return;
        }
        failExpiredLeases();
        SearchIndexRebuildTaskPO pending = taskMapper.findPending();
        if (pending != null) {
            schedulePendingTask(pending.getTaskId());
        }
    }

    private void schedulePendingTask(String taskId) {
        if (taskId == null || !scheduledTaskIds.add(taskId)) {
            return;
        }
        try {
            rebuildExecutor.execute(() -> {
                try {
                    runRebuild(taskId);
                } finally {
                    scheduledTaskIds.remove(taskId);
                }
            });
        } catch (RuntimeException e) {
            scheduledTaskIds.remove(taskId);
            log.warn("search index rebuild task scheduling deferred: taskId={}", taskId, e);
        }
    }

    private void runRebuild(String taskId) {
        LocalDateTime lockUntil = LocalDateTime.now().plus(WORKER_LEASE);
        if (taskMapper.markRunning(taskId, owner, lockUntil) <= 0) {
            return;
        }

        ProgressState progress = new ProgressState();
        try {
            Map<String, Object> result = indexer.rebuildAll(snapshot -> {
                progress.update(snapshot);
                return taskMapper.heartbeat(
                        taskId,
                        owner,
                        snapshot.checkpointId(),
                        snapshot.indexed(),
                        snapshot.failed(),
                        snapshot.total(),
                        LocalDateTime.now().plus(WORKER_LEASE)) > 0;
            });
            progress.update(result);
            boolean accepted = Boolean.TRUE.equals(result.get("accepted"));
            String status = accepted ? STATUS_SUCCEEDED : STATUS_FAILED;
            String message = accepted ? null : shortMessage(asString(result.get("message")));
            int updated = taskMapper.finish(
                    taskId,
                    owner,
                    status,
                    progress.indexed,
                    progress.failed,
                    progress.total,
                    asString(result.get("indexName")),
                    message,
                    progress.checkpointId);
            if (updated <= 0) {
                log.warn("search index rebuild completion ignored after lease loss: taskId={} owner={}", taskId, owner);
            }
        } catch (Exception e) {
            log.error("search index rebuild task failed: taskId={}", taskId, e);
            int updated = taskMapper.finish(
                    taskId,
                    owner,
                    STATUS_FAILED,
                    progress.indexed,
                    progress.failed,
                    progress.total,
                    null,
                    shortMessage(e.getMessage()),
                    progress.checkpointId);
            if (updated <= 0) {
                log.warn("search index rebuild failure could not update task after lease loss: taskId={} owner={}",
                        taskId, owner);
            }
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

    private void requireTableReady() {
        if (!tableReady()) {
            throw new BizException(ErrorCode.DATABASE_ERROR.getCode(),
                    "Search index rebuild table is unavailable");
        }
    }

    private boolean tableReady() {
        if (taskMapper == null) {
            return false;
        }
        try {
            return taskMapper.tableExists() > 0;
        } catch (RuntimeException e) {
            log.warn("search index rebuild table readiness check failed: {}", e.getMessage());
            return false;
        }
    }

    private void failExpiredLeases() {
        try {
            int failed = taskMapper.failExpiredLease();
            if (failed > 0) {
                log.error("marked {} search index rebuild task(s) failed after worker lease expiry", failed);
            }
        } catch (RuntimeException e) {
            log.warn("search index rebuild lease cleanup failed: {}", e.getMessage());
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

    private static SearchIndexRebuildTaskPO pendingSnapshot(SearchIndexRebuildTaskPO task) {
        task.setTaskStatus(STATUS_PENDING);
        task.setCheckpointId(0L);
        task.setIndexedCount(0);
        task.setFailedCount(0);
        task.setTotalCount(0);
        LocalDateTime now = LocalDateTime.now();
        task.setCreateTime(now);
        task.setUpdateTime(now);
        return task;
    }

    private static SearchIndexTask toTask(SearchIndexRebuildTaskPO task) {
        String status = task.getTaskStatus() == null ? STATUS_PENDING : task.getTaskStatus();
        return SearchIndexTask.builder()
                .taskId(task.getTaskId())
                .type(task.getTaskType() == null ? TYPE_REBUILD : task.getTaskType())
                .status(status)
                .operatorUid(task.getOperatorUid())
                .accepted(STATUS_SUCCEEDED.equals(status))
                .indexed(valueOrZero(task.getIndexedCount()))
                .failed(valueOrZero(task.getFailedCount()))
                .total(valueOrZero(task.getTotalCount()))
                .indexName(task.getIndexName())
                .message(task.getLastError())
                .checkpointId(task.getCheckpointId() == null ? 0L : task.getCheckpointId())
                .heartbeatTime(task.getHeartbeatTime())
                .lockUntil(task.getLockUntil())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .createdAt(task.getCreateTime())
                .updatedAt(task.getUpdateTime())
                .build();
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String shortMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private static BizException databaseFailure(String message, RuntimeException cause) {
        log.error(message, cause);
        return new BizException(ErrorCode.DATABASE_ERROR.getCode(), message);
    }

    private static String buildOwner() {
        String host = "unknown";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            // Best-effort worker identity only.
        }
        return host + ":" + ManagementFactory.getRuntimeMXBean().getName() + ":" + UUID.randomUUID();
    }

    private static final class ProgressState {
        private long checkpointId;
        private int indexed;
        private int failed;
        private int total;

        private void update(PostSearchIndexer.RebuildProgress progress) {
            if (progress == null) {
                return;
            }
            checkpointId = progress.checkpointId();
            indexed = progress.indexed();
            failed = progress.failed();
            total = progress.total();
        }

        private void update(Map<String, Object> result) {
            if (result == null) {
                return;
            }
            checkpointId = Math.max(checkpointId, asLong(result.get("checkpointId")));
            indexed = Math.max(indexed, asInt(result.get("indexed")));
            failed = Math.max(failed, asInt(result.get("failed")));
            total = Math.max(total, asInt(result.get("total")));
        }
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
        private long checkpointId;
        private LocalDateTime heartbeatTime;
        private LocalDateTime lockUntil;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
