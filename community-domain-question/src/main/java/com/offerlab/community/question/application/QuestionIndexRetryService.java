package com.offerlab.community.question.application;

import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.question.infrastructure.persistence.mapper.QuestionIndexRetryTaskMapper;
import com.offerlab.community.question.infrastructure.persistence.po.QuestionIndexRetryTaskPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionIndexRetryService {
    static final String OP_INDEX = "INDEX";
    static final String OP_DELETE = "DELETE";
    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRY = 5;
    private static final int CLAIM_LEASE_SECONDS = 60;

    private final QuestionIndexRetryTaskMapper taskMapper;
    private final SnowflakeIdGenerator idGen;
    private final QuestionSearchIndexer indexer;
    private final String owner = buildOwner();

    @EventListener
    public void onRetryEvent(QuestionIndexRetryEvent event) {
        if (event == null) {
            return;
        }
        enqueue(event.operation(), event.questionId(), event.cause());
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

    @Scheduled(fixedDelay = 5000)
    public void retryDueTasks() {
        if (!tableReady()) {
            return;
        }
        int claimed = taskMapper.claimDue(owner, LocalDateTime.now().plusSeconds(CLAIM_LEASE_SECONDS), BATCH_SIZE);
        if (claimed <= 0) {
            return;
        }
        for (QuestionIndexRetryTaskPO task : taskMapper.findClaimed(owner, BATCH_SIZE)) {
            retryOne(task);
        }
    }

    private void retryOne(QuestionIndexRetryTaskPO task) {
        try {
            boolean ok = switch (task.getOperation()) {
                case OP_INDEX -> indexer.indexQuestionForRetry(task.getQuestionId());
                case OP_DELETE -> indexer.deleteQuestionForRetry(task.getQuestionId());
                default -> throw new IllegalArgumentException("Unsupported question index retry operation: " + task.getOperation());
            };
            if (!ok) {
                throw new IllegalStateException("Elasticsearch question index operation returned false");
            }
            taskMapper.markDone(task.getId(), owner);
        } catch (Exception e) {
            handleRetryFailure(task, e);
        }
    }

    private void enqueue(String operation, Long questionId, Throwable cause) {
        if (questionId == null || questionId <= 0 || !tableReady()) {
            return;
        }
        QuestionIndexRetryTaskPO task = new QuestionIndexRetryTaskPO();
        task.setId(idGen.nextId());
        task.setDedupKey("question:search:" + questionId);
        task.setQuestionId(questionId);
        task.setOperation(operation);
        task.setTaskStatus(QuestionIndexRetryTaskMapper.STATUS_PENDING);
        task.setRetryCount(0);
        task.setNextRetryTime(LocalDateTime.now().plusSeconds(30));
        task.setLastError(shortMessage(cause));
        taskMapper.upsertPending(task);
        log.warn("question index retry task enqueued: operation={} questionId={}", operation, questionId, cause);
    }

    private void handleRetryFailure(QuestionIndexRetryTaskPO task, Exception e) {
        int retryCount = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        if (retryCount >= MAX_RETRY) {
            taskMapper.updateRetry(task.getId(), owner, QuestionIndexRetryTaskMapper.STATUS_FAILED,
                    retryCount, null, shortMessage(e));
            return;
        }
        LocalDateTime nextRetry = LocalDateTime.now().plusSeconds((long) Math.pow(2, retryCount) * 30);
        taskMapper.updateRetry(task.getId(), owner, QuestionIndexRetryTaskMapper.STATUS_PENDING,
                retryCount, nextRetry, shortMessage(e));
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
        return Map.of("byStatus", byStatus, "duePending", 0L);
    }

    private static String statusName(Object status) {
        int value = status instanceof Number number ? number.intValue() : -1;
        return switch (value) {
            case QuestionIndexRetryTaskMapper.STATUS_PENDING -> "pending";
            case QuestionIndexRetryTaskMapper.STATUS_DONE -> "done";
            case QuestionIndexRetryTaskMapper.STATUS_FAILED -> "failed";
            case QuestionIndexRetryTaskMapper.STATUS_RUNNING -> "running";
            default -> "unknown";
        };
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static String shortMessage(Throwable cause) {
        if (cause == null || cause.getMessage() == null) {
            return null;
        }
        String message = cause.getMessage();
        return message.length() <= 500 ? message : message.substring(0, 500);
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
