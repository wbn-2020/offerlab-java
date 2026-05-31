package com.offerlab.community.search.application;

import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.search.infrastructure.persistence.mapper.SearchIndexRetryTaskMapper;
import com.offerlab.community.search.infrastructure.persistence.po.SearchIndexRetryTaskPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexRetryService {

    static final String OP_INDEX = "INDEX";
    static final String OP_DELETE = "DELETE";
    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRY = 5;
    private static final int CLAIM_LEASE_SECONDS = 60;

    private final SearchIndexRetryTaskMapper taskMapper;
    private final SnowflakeIdGenerator idGen;
    private final PostSearchIndexer indexer;
    private final String owner = buildOwner();

    public void enqueueIndex(Long postId, Throwable cause) {
        enqueue(OP_INDEX, postId, cause);
    }

    public void enqueueDelete(Long postId, Throwable cause) {
        enqueue(OP_DELETE, postId, cause);
    }

    public List<SearchIndexRetryTaskPO> listRecent(Integer status, int limit) {
        if (!tableReady()) {
            return List.of();
        }
        return taskMapper.listRecent(status, clampLimit(limit));
    }

    public SearchIndexRetryTaskPO findById(Long id) {
        if (!tableReady()) {
            return null;
        }
        return id == null || id <= 0 ? null : taskMapper.findById(id);
    }

    public Map<String, Object> status() {
        if (!tableReady()) {
            return emptyStatus();
        }
        Map<String, Long> byStatus = new LinkedHashMap<>();
        byStatus.put("pending", 0L);
        byStatus.put("done", 0L);
        byStatus.put("failed", 0L);
        byStatus.put("running", 0L);
        for (Map<String, Object> row : taskMapper.countByStatus()) {
            byStatus.put(statusName(row.get("status")), asLong(row.get("count")));
        }
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("byStatus", byStatus);
        status.put("duePending", taskMapper.countDuePending());
        return status;
    }

    public boolean replayFailed(Long id) {
        if (!tableReady()) {
            return false;
        }
        return id != null && id > 0 && taskMapper.markFailedForRetry(id) > 0;
    }

    public int replayFailedBatch(List<Long> ids) {
        if (!tableReady()) {
            return 0;
        }
        List<Long> safeIds = ids == null ? List.of() : ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(100)
                .toList();
        return safeIds.isEmpty() ? 0 : taskMapper.markFailedForRetryBatch(safeIds);
    }

    @Scheduled(fixedDelay = 5000)
    public void retryDueTasks() {
        if (!tableReady()) {
            return;
        }
        LocalDateTime lockUntil = LocalDateTime.now().plusSeconds(CLAIM_LEASE_SECONDS);
        int claimed = taskMapper.claimDue(owner, lockUntil, BATCH_SIZE);
        if (claimed <= 0) {
            return;
        }
        for (SearchIndexRetryTaskPO task : taskMapper.findClaimed(owner, BATCH_SIZE)) {
            retryOne(task);
        }
    }

    void retryOne(SearchIndexRetryTaskPO task) {
        try {
            boolean ok = switch (task.getOperation()) {
                case OP_INDEX -> indexer.indexPost(task.getPostId());
                case OP_DELETE -> indexer.deletePost(task.getPostId());
                default -> throw new IllegalArgumentException("Unsupported search retry operation: " + task.getOperation());
            };
            if (!ok) {
                throw new IllegalStateException("Elasticsearch index operation returned false");
            }
            taskMapper.markDone(task.getId(), owner);
            log.debug("search index retry task completed: id={} operation={} postId={}",
                    task.getId(), task.getOperation(), task.getPostId());
        } catch (Exception e) {
            handleRetryFailure(task, e);
        }
    }

    private void enqueue(String operation, Long postId, Throwable cause) {
        if (postId == null || postId <= 0) {
            return;
        }
        if (!tableReady()) {
            return;
        }
        SearchIndexRetryTaskPO task = new SearchIndexRetryTaskPO();
        task.setId(idGen.nextId());
        task.setDedupKey("post:search:" + postId);
        task.setPostId(postId);
        task.setOperation(operation);
        task.setTaskStatus(SearchIndexRetryTaskMapper.STATUS_PENDING);
        task.setRetryCount(0);
        task.setNextRetryTime(LocalDateTime.now().plusSeconds(30));
        task.setLastError(shortMessage(cause));
        taskMapper.upsertPending(task);
        log.warn("search index retry task enqueued: operation={} postId={}", operation, postId, cause);
    }

    private void handleRetryFailure(SearchIndexRetryTaskPO task, Exception e) {
        int retryCount = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        if (retryCount >= MAX_RETRY) {
            taskMapper.updateRetry(task.getId(), owner, SearchIndexRetryTaskMapper.STATUS_FAILED,
                    retryCount, null, shortMessage(e));
            log.error("search index retry task failed after {} retries: id={} operation={} postId={}",
                    MAX_RETRY, task.getId(), task.getOperation(), task.getPostId(), e);
            return;
        }
        long delaySeconds = (long) Math.pow(2, retryCount) * 30;
        LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(delaySeconds);
        taskMapper.updateRetry(task.getId(), owner, SearchIndexRetryTaskMapper.STATUS_PENDING,
                retryCount, nextRetry, shortMessage(e));
        log.warn("search index retry task rescheduled: id={} operation={} postId={} nextRetry={} delaySeconds={}",
                task.getId(), task.getOperation(), task.getPostId(), nextRetry, delaySeconds, e);
    }

    private String shortMessage(Throwable cause) {
        if (cause == null || cause.getMessage() == null) {
            return null;
        }
        String message = cause.getMessage();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private static int clampLimit(int limit) {
        if (limit < 1) {
            return 20;
        }
        return Math.min(limit, 100);
    }

    private boolean tableReady() {
        try {
            return taskMapper.tableExists() > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Map<String, Object> emptyStatus() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        byStatus.put("pending", 0L);
        byStatus.put("done", 0L);
        byStatus.put("failed", 0L);
        byStatus.put("running", 0L);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("byStatus", byStatus);
        status.put("duePending", 0L);
        return status;
    }

    private static String statusName(Object status) {
        int value = status instanceof Number number ? number.intValue() : -1;
        return switch (value) {
            case SearchIndexRetryTaskMapper.STATUS_PENDING -> "pending";
            case SearchIndexRetryTaskMapper.STATUS_DONE -> "done";
            case SearchIndexRetryTaskMapper.STATUS_FAILED -> "failed";
            case SearchIndexRetryTaskMapper.STATUS_RUNNING -> "running";
            default -> "unknown";
        };
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private static String buildOwner() {
        String host = "unknown";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            // best-effort identifier only
        }
        return host + ":" + ManagementFactory.getRuntimeMXBean().getName() + ":" + UUID.randomUUID();
    }
}
