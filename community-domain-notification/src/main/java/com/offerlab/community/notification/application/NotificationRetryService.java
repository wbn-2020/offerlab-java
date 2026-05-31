package com.offerlab.community.notification.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.notification.infrastructure.persistence.mapper.NotificationRetryTaskMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationRetryTaskPO;
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
public class NotificationRetryService {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRY = 5;
    private static final int CLAIM_LEASE_SECONDS = 60;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final NotificationRetryTaskMapper taskMapper;
    private final SnowflakeIdGenerator idGen;
    private final ObjectMapper objectMapper;
    private final NotificationFacadeImpl notificationFacade;
    private final String owner = buildOwner();

    public void enqueue(String scene, Long receiverUid, Long senderUid, Integer notifType,
                        Integer targetType, Long targetId, Map<String, Object> content, Throwable cause) {
        if (receiverUid == null || senderUid == null || receiverUid.equals(senderUid)) {
            return;
        }
        if (!tableReady()) {
            return;
        }
        NotificationRetryTaskPO task = new NotificationRetryTaskPO();
        task.setId(idGen.nextId());
        task.setDedupKey(NotificationDedupKey.of(receiverUid, senderUid, notifType, targetType, targetId, content));
        task.setScene(scene);
        task.setReceiverUid(receiverUid);
        task.setSenderUid(senderUid);
        task.setNotifType(notifType);
        task.setTargetType(targetType);
        task.setTargetId(targetId);
        task.setContentJson(toJson(content));
        task.setTaskStatus(NotificationRetryTaskMapper.STATUS_PENDING);
        task.setRetryCount(0);
        task.setNextRetryTime(LocalDateTime.now().plusSeconds(30));
        task.setLastError(shortMessage(cause));
        taskMapper.upsertPending(task);
        log.warn("notification retry task enqueued: scene={} dedupKey={} receiverUid={} targetId={}",
                scene, task.getDedupKey(), receiverUid, targetId, cause);
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
        for (NotificationRetryTaskPO task : taskMapper.findClaimed(owner, BATCH_SIZE)) {
            retryOne(task);
        }
    }

    public List<NotificationRetryTaskPO> listRecent(Integer status, int limit) {
        if (!tableReady()) {
            return List.of();
        }
        return taskMapper.listRecent(status, clampLimit(limit));
    }

    public NotificationRetryTaskPO findById(Long id) {
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

    void retryOne(NotificationRetryTaskPO task) {
        try {
            notificationFacade.createFromRetryTask(
                    task.getReceiverUid(),
                    task.getSenderUid(),
                    task.getNotifType(),
                    task.getTargetType(),
                    task.getTargetId(),
                    parseContent(task.getContentJson()));
            taskMapper.markDone(task.getId(), owner);
            log.debug("notification retry task completed: id={} dedupKey={}", task.getId(), task.getDedupKey());
        } catch (Exception e) {
            handleRetryFailure(task, e);
        }
    }

    private void handleRetryFailure(NotificationRetryTaskPO task, Exception e) {
        int retryCount = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        if (retryCount >= MAX_RETRY) {
            taskMapper.updateRetry(task.getId(), owner, NotificationRetryTaskMapper.STATUS_FAILED,
                    retryCount, null, shortMessage(e));
            log.error("notification retry task failed after {} retries: id={} dedupKey={}",
                    MAX_RETRY, task.getId(), task.getDedupKey(), e);
            return;
        }
        long delaySeconds = (long) Math.pow(2, retryCount) * 30;
        LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(delaySeconds);
        taskMapper.updateRetry(task.getId(), owner, NotificationRetryTaskMapper.STATUS_PENDING,
                retryCount, nextRetry, shortMessage(e));
        log.warn("notification retry task rescheduled: id={} dedupKey={} nextRetry={} delaySeconds={}",
                task.getId(), task.getDedupKey(), nextRetry, delaySeconds, e);
    }

    private Map<String, Object> parseContent(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Map<String, Object> content) {
        try {
            return objectMapper.writeValueAsString(content == null ? Map.of() : content);
        } catch (JsonProcessingException e) {
            return "{}";
        }
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
            case NotificationRetryTaskMapper.STATUS_PENDING -> "pending";
            case NotificationRetryTaskMapper.STATUS_DONE -> "done";
            case NotificationRetryTaskMapper.STATUS_FAILED -> "failed";
            case NotificationRetryTaskMapper.STATUS_RUNNING -> "running";
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
