package com.offerlab.community.question.application;

import com.offerlab.community.question.infrastructure.persistence.mapper.QuestionIndexTaskMapper;
import com.offerlab.community.question.infrastructure.persistence.po.QuestionIndexTaskPO;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionIndexTaskService {
    private static final String TYPE_REBUILD = "QUESTION_INDEX_REBUILD";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";

    private final QuestionSearchIndexer indexer;
    private final QuestionIndexTaskMapper taskMapper;
    private Executor rebuildExecutor = ForkJoinPool.commonPool();

    public QuestionIndexTask submitRebuildTask(Long operatorUid) {
        ensureTableReady();
        QuestionIndexTaskPO task = new QuestionIndexTaskPO();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskType(TYPE_REBUILD);
        task.setTaskStatus(STATUS_PENDING);
        task.setOperatorUid(operatorUid);
        task.setAccepted(0);
        task.setIndexed(0);
        task.setFailed(0);
        task.setTotal(0);
        taskMapper.insertTask(task);
        CompletableFuture.runAsync(() -> runRebuild(task.getTaskId()), rebuildExecutor);
        return snapshot(taskMapper.findByTaskId(task.getTaskId()));
    }

    public QuestionIndexTask retryTask(String taskId) {
        ensureTableReady();
        QuestionIndexTaskPO existing = taskMapper.findByTaskId(taskId);
        if (existing == null) {
            return null;
        }
        if (STATUS_FAILED.equals(existing.getTaskStatus()) && taskMapper.markRetry(taskId) > 0) {
            CompletableFuture.runAsync(() -> runRebuild(taskId), rebuildExecutor);
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
        if (!tableReady() || taskMapper.markRunning(taskId) == 0) {
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
        }
    }

    void runRebuildForTest(String taskId) {
        runRebuild(taskId);
    }

    void setRebuildExecutorForTest(Executor rebuildExecutor) {
        this.rebuildExecutor = rebuildExecutor == null ? ForkJoinPool.commonPool() : rebuildExecutor;
    }

    private void ensureTableReady() {
        if (!tableReady()) {
            throw new IllegalStateException("Question index task table is not ready");
        }
    }

    private boolean tableReady() {
        try {
            return taskMapper.tableExists() > 0;
        } catch (RuntimeException e) {
            return false;
        }
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
