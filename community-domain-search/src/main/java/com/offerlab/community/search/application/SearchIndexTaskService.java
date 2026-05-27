package com.offerlab.community.search.application;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexTaskService {
    private static final int MAX_RETAINED_TASKS = 100;

    private final PostSearchIndexer indexer;
    private final Map<String, SearchIndexTask> tasks = new ConcurrentHashMap<>();

    public SearchIndexTask submitRebuildTask(Long operatorUid) {
        String taskId = UUID.randomUUID().toString();
        SearchIndexTask task = SearchIndexTask.builder()
                .taskId(taskId)
                .type("POST_INDEX_REBUILD")
                .status("PENDING")
                .operatorUid(operatorUid)
                .indexed(0)
                .failed(0)
                .total(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        tasks.put(taskId, task);
        pruneOldTasks();

        CompletableFuture.runAsync(() -> runRebuild(taskId));
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
        task.setStatus("RUNNING");
        task.setUpdatedAt(LocalDateTime.now());
        try {
            Map<String, Object> result = indexer.rebuildAll();
            task.setAccepted(Boolean.TRUE.equals(result.get("accepted")));
            task.setIndexed(asInt(result.get("indexed")));
            task.setFailed(asInt(result.get("failed")));
            task.setTotal(asInt(result.get("total")));
            task.setIndexName(asString(result.get("indexName")));
            task.setMessage(asString(result.get("message")));
            task.setStatus(task.isAccepted() ? "SUCCEEDED" : "FAILED");
        } catch (Exception e) {
            log.error("search index rebuild task failed: taskId={}", taskId, e);
            task.setAccepted(false);
            task.setStatus("FAILED");
            task.setMessage(e.getMessage());
        } finally {
            task.setUpdatedAt(LocalDateTime.now());
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
