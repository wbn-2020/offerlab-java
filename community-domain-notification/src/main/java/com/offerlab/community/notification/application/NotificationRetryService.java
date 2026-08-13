package com.offerlab.community.notification.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.utils.LogMask;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.notification.infrastructure.persistence.mapper.NotificationRetryTaskMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationRetryTaskPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Instant;
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
    private static final int RETENTION_BATCH_SIZE = 1000;
    private static final int MAX_RETENTION_BATCHES = 20;
    private static final int DONE_RETENTION_DAYS = 30;
    private static final int FAILED_RETENTION_DAYS = 180;
    private static final String SUBSCRIPTION_UPDATE_DELIVERY_SCENE = "subscription_update_delivery";
    private static final int SUBSCRIPTION_UPDATE_RETRY_VERSION = 1;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final NotificationRetryTaskMapper taskMapper;
    private final SnowflakeIdGenerator idGen;
    private final ObjectMapper objectMapper;
    private final NotificationFacadeImpl notificationFacade;
    private final String owner = buildOwner();
    @Autowired(required = false)
    private ObjectProvider<SubscriptionUpdateDeliveryService> subscriptionUpdateDeliveryServiceProvider;

    public boolean enqueue(String scene, Long receiverUid, Long senderUid, Integer notifType,
                           Integer targetType, Long targetId, Map<String, Object> content, Throwable cause) {
        if (receiverUid == null || senderUid == null || receiverUid.equals(senderUid)) {
            return false;
        }
        if (!tableReady()) {
            return false;
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
                scene, LogMask.key(task.getDedupKey()), LogMask.id(receiverUid), LogMask.id(targetId), cause);
        return true;
    }

    boolean enqueueSubscriptionUpdateDelivery(SubscriptionUpdateDeliveryCommand command,
                                              SubscriptionUpdateDeliveryMode deliveryMode,
                                              Throwable cause) {
        if (command == null
                || deliveryMode == SubscriptionUpdateDeliveryMode.MUTED
                || command.notificationKind() == null
                || command.retrySenderUid() == null) {
            return false;
        }
        SubscriptionUpdateRetryStrategy retryStrategy = deliveryMode == null
                ? SubscriptionUpdateRetryStrategy.POLICY_REEVALUATION
                : SubscriptionUpdateRetryStrategy.RESOLVED_DELIVERY;
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("dedupKey", subscriptionDeliveryRetryDedupKey(command, deliveryMode, retryStrategy));
        content.put("retryVersion", SUBSCRIPTION_UPDATE_RETRY_VERSION);
        content.put("retryStrategy", retryStrategy.name());
        if (deliveryMode != null) {
            content.put("deliveryMode", deliveryMode.name());
        }
        content.put("receiverUid", command.receiverUid());
        content.put("sourceType", command.sourceType());
        content.put("sourceId", command.sourceId());
        content.put("resourceType", command.resourceType());
        content.put("resourceId", command.resourceId());
        content.put("eventType", command.eventType());
        content.put("eventKey", command.eventKey());
        content.put("actorUid", command.actorUid());
        content.put("notificationKind", command.notificationKind().name());
        content.put("notificationTargetType", command.notificationTargetType());
        content.put("notificationTargetId", command.notificationTargetId());
        content.put("safePayload", command.payloadOrEmpty());
        content.put("occurredAt", command.occurredAt() == null ? null : command.occurredAt().toEpochMilli());
        return enqueue(
                SUBSCRIPTION_UPDATE_DELIVERY_SCENE,
                command.receiverUid(),
                command.retrySenderUid(),
                command.notificationType(),
                command.notificationTargetType(),
                command.notificationTargetId(),
                content,
                cause);
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

    @Scheduled(cron = "${offerlab.notification.retry-retention-cleanup-cron:0 30 * * * *}")
    public void cleanupExpiredTasks() {
        if (!tableReady()) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            int doneDeleted = deleteInBatches(
                    NotificationRetryTaskMapper.STATUS_DONE, now.minusDays(DONE_RETENTION_DAYS));
            int failedDeleted = deleteInBatches(
                    NotificationRetryTaskMapper.STATUS_FAILED, now.minusDays(FAILED_RETENTION_DAYS));
            if (doneDeleted + failedDeleted > 0) {
                log.info("notification retry retention cleanup completed: doneDeleted={} failedDeleted={}",
                        doneDeleted, failedDeleted);
            }
        } catch (RuntimeException e) {
            log.warn("notification retry retention cleanup failed", e);
        }
    }

    public List<NotificationRetryTaskPO> listRecent(Integer status, int limit) {
        if (!tableReady()) {
            return List.of();
        }
        return taskMapper.listRecent(status, clampLimit(limit));
    }

    public PageResult<NotificationRetryTaskPO> pageRecent(Integer status, int page, int pageSize) {
        if (!tableReady()) {
            return PageResult.empty();
        }
        int safePageSize = clampLimit(pageSize);
        int safePage = Math.min(Math.max(1, page), 1000);
        int offset = Math.multiplyExact(safePage - 1, safePageSize);
        long total = taskMapper.countPage(status);
        List<NotificationRetryTaskPO> items = total <= offset
                ? List.of()
                : taskMapper.pageRecent(status, safePageSize, offset);
        return PageResult.<NotificationRetryTaskPO>builder()
                .items(items)
                .hasMore(offset + items.size() < total)
                .total(total)
                .build();
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
        long duePending = taskMapper.countDuePending();
        long failed = byStatus.getOrDefault("failed", 0L);
        boolean attentionRequired = failed > 0 || duePending > 0;
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", attentionRequired ? "DEGRADED" : "UP");
        status.put("available", true);
        status.put("byStatus", byStatus);
        status.put("duePending", duePending);
        status.put("attentionRequired", attentionRequired);
        if (attentionRequired) {
            status.put("message", "Notification retry queue has failed or due tasks");
            status.put("action", "Review failed notification retry tasks in Ops and replay after notification dependencies are healthy.");
            status.put("diagnostics", diagnostics(failed, duePending));
        }
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
            if (SUBSCRIPTION_UPDATE_DELIVERY_SCENE.equals(task.getScene())) {
                retrySubscriptionUpdateDelivery(task);
            } else {
                notificationFacade.createFromRetryTask(
                        task.getReceiverUid(),
                        task.getSenderUid(),
                        task.getNotifType(),
                        task.getTargetType(),
                        task.getTargetId(),
                        parseContent(task.getContentJson()));
            }
            taskMapper.markDone(task.getId(), owner);
            log.debug("notification retry task completed: id={} dedupKey={}",
                    LogMask.id(task.getId()), LogMask.key(task.getDedupKey()));
        } catch (Exception e) {
            handleRetryFailure(task, e);
        }
    }

    private void retrySubscriptionUpdateDelivery(NotificationRetryTaskPO task) {
        if (subscriptionUpdateDeliveryServiceProvider == null) {
            throw new IllegalStateException("subscription update delivery service is unavailable");
        }
        SubscriptionUpdateDeliveryService deliveryService =
                subscriptionUpdateDeliveryServiceProvider.getIfAvailable();
        if (deliveryService == null) {
            throw new IllegalStateException("subscription update delivery service is unavailable");
        }
        SubscriptionUpdateRetryPayload payload =
                parseSubscriptionUpdateRetryPayload(parseContent(task.getContentJson()));
        SubscriptionUpdateDeliveryResult result = switch (payload.retryStrategy()) {
            case RESOLVED_DELIVERY -> deliveryService.deliverResolved(
                    payload.command(), payload.deliveryMode());
            case POLICY_REEVALUATION -> deliveryService.deliverForSource(List.of(payload.command())).get(0);
        };
        if (result.retryable()) {
            throw result.failure() == null
                    ? new IllegalStateException("subscription update delivery replay is unavailable")
                    : result.failure();
        }
    }

    private void handleRetryFailure(NotificationRetryTaskPO task, Exception e) {
        int retryCount = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        if (retryCount >= MAX_RETRY) {
            taskMapper.updateRetry(task.getId(), owner, NotificationRetryTaskMapper.STATUS_FAILED,
                    retryCount, null, shortMessage(e));
            log.error("notification retry task failed after {} retries: id={} dedupKey={}",
                    MAX_RETRY, LogMask.id(task.getId()), LogMask.key(task.getDedupKey()), e);
            return;
        }
        long delaySeconds = (long) Math.pow(2, retryCount) * 30;
        LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(delaySeconds);
        taskMapper.updateRetry(task.getId(), owner, NotificationRetryTaskMapper.STATUS_PENDING,
                retryCount, nextRetry, shortMessage(e));
        log.warn("notification retry task rescheduled: id={} dedupKey={} nextRetry={} delaySeconds={}",
                LogMask.id(task.getId()), LogMask.key(task.getDedupKey()), nextRetry, delaySeconds, e);
    }

    private int deleteInBatches(int status, LocalDateTime before) {
        int total = 0;
        for (int batch = 0; batch < MAX_RETENTION_BATCHES; batch++) {
            int deleted = taskMapper.deleteTerminalBefore(status, before, RETENTION_BATCH_SIZE);
            total += Math.max(0, deleted);
            if (deleted < RETENTION_BATCH_SIZE) {
                break;
            }
        }
        return total;
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

    private static SubscriptionUpdateRetryPayload parseSubscriptionUpdateRetryPayload(
            Map<String, Object> content) {
        if (number(content.get("retryVersion")) != SUBSCRIPTION_UPDATE_RETRY_VERSION) {
            throw new IllegalArgumentException("subscription update retry version is invalid");
        }
        SubscriptionUpdateRetryStrategy retryStrategy = parseRetryStrategy(content);
        SubscriptionUpdateDeliveryMode deliveryMode = retryStrategy == SubscriptionUpdateRetryStrategy.RESOLVED_DELIVERY
                ? parseEnum(content.get("deliveryMode"), SubscriptionUpdateDeliveryMode.class, "deliveryMode")
                : null;
        if (deliveryMode == SubscriptionUpdateDeliveryMode.MUTED
                || (retryStrategy == SubscriptionUpdateRetryStrategy.POLICY_REEVALUATION
                && content.containsKey("deliveryMode"))) {
            throw new IllegalArgumentException("subscription update retry delivery mode is invalid");
        }
        SubscriptionUpdateNotificationKind notificationKind = parseEnum(
                content.get("notificationKind"), SubscriptionUpdateNotificationKind.class, "notificationKind");
        SubscriptionUpdateDeliveryCommand command = new SubscriptionUpdateDeliveryCommand(
                positiveLong(content.get("receiverUid"), "receiverUid"),
                text(content.get("sourceType"), "sourceType", 24),
                positiveLong(content.get("sourceId"), "sourceId"),
                text(content.get("resourceType"), "resourceType", 24),
                positiveLong(content.get("resourceId"), "resourceId"),
                text(content.get("eventType"), "eventType", 64),
                text(content.get("eventKey"), "eventKey", 160),
                positiveLong(content.get("actorUid"), "actorUid"),
                notificationKind,
                optionalPositiveInt(content.get("notificationTargetType"), "notificationTargetType"),
                positiveLong(content.get("notificationTargetId"), "notificationTargetId"),
                nestedMap(content.get("safePayload"), "safePayload"),
                Instant.ofEpochMilli(positiveLong(content.get("occurredAt"), "occurredAt")));
        return new SubscriptionUpdateRetryPayload(command, deliveryMode, retryStrategy);
    }

    private static String subscriptionDeliveryRetryDedupKey(
            SubscriptionUpdateDeliveryCommand command,
            SubscriptionUpdateDeliveryMode deliveryMode,
            SubscriptionUpdateRetryStrategy retryStrategy) {
        return SUBSCRIPTION_UPDATE_DELIVERY_SCENE
                + ":" + command.sourceType()
                + ":" + command.sourceId()
                + ":" + command.eventKey()
                + ":" + (retryStrategy == SubscriptionUpdateRetryStrategy.RESOLVED_DELIVERY
                ? deliveryMode
                : retryStrategy);
    }

    private static SubscriptionUpdateRetryStrategy parseRetryStrategy(Map<String, Object> content) {
        Object value = content.get("retryStrategy");
        return value == null
                ? SubscriptionUpdateRetryStrategy.RESOLVED_DELIVERY
                : parseEnum(value, SubscriptionUpdateRetryStrategy.class, "retryStrategy");
    }

    private static Map<String, Object> nestedMap(Object value, String field) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private static long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return Long.MIN_VALUE;
            }
        }
        return Long.MIN_VALUE;
    }

    private static Long positiveLong(Object value, String field) {
        long parsed = number(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return parsed;
    }

    private static Integer optionalPositiveInt(Object value, String field) {
        if (value == null) {
            return null;
        }
        long parsed = number(value);
        if (parsed <= 0 || parsed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return (int) parsed;
    }

    private static String text(Object value, String field, int maxLength) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        String normalized = text.trim();
        if (normalized.isBlank() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }

    private static <T extends Enum<T>> T parseEnum(
            Object value, Class<T> type, String field) {
        String name = text(value, field, 80);
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " is invalid", e);
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
        status.put("status", "DOWN");
        status.put("available", false);
        status.put("attentionRequired", true);
        status.put("message", "notification retry table unavailable");
        status.put("action", "Apply the notification retry migration before relying on async notification repair.");
        status.put("byStatus", byStatus);
        status.put("duePending", 0L);
        return status;
    }

    private Map<String, Object> diagnostics(long failed, long duePending) {
        NotificationRetryTaskPO failedSample = failed > 0 ? firstTask(NotificationRetryTaskMapper.STATUS_FAILED) : null;
        NotificationRetryTaskPO pendingSample = duePending > 0 ? firstTask(NotificationRetryTaskMapper.STATUS_PENDING) : null;
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("failedSample", summarize(failedSample));
        diagnostics.put("pendingSample", summarize(pendingSample));
        diagnostics.put("latestError", firstText(errorOf(failedSample), errorOf(pendingSample)));
        diagnostics.put("recommendedAction", "Open /api/v1/notification-ops/retry-tasks?status=2, confirm notification dependencies, then replay failed test records first.");
        return diagnostics;
    }

    private NotificationRetryTaskPO firstTask(int status) {
        try {
            List<NotificationRetryTaskPO> rows = taskMapper.listRecent(status, 1);
            return rows == null || rows.isEmpty() ? null : rows.get(0);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Map<String, Object> summarize(NotificationRetryTaskPO task) {
        if (task == null) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", LogMask.id(task.getId()));
        data.put("scene", task.getScene());
        data.put("receiverUid", LogMask.id(task.getReceiverUid()));
        data.put("targetType", task.getTargetType());
        data.put("targetId", LogMask.id(task.getTargetId()));
        data.put("retryCount", task.getRetryCount());
        data.put("nextRetryTime", task.getNextRetryTime() == null ? null : task.getNextRetryTime().toString());
        data.put("lastError", task.getLastError());
        return data;
    }

    private static String errorOf(NotificationRetryTaskPO task) {
        return task == null ? null : task.getLastError();
    }

    private static String firstText(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
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

    private record SubscriptionUpdateRetryPayload(
            SubscriptionUpdateDeliveryCommand command,
            SubscriptionUpdateDeliveryMode deliveryMode,
            SubscriptionUpdateRetryStrategy retryStrategy) {
    }

    private enum SubscriptionUpdateRetryStrategy {
        RESOLVED_DELIVERY,
        POLICY_REEVALUATION
    }
}
