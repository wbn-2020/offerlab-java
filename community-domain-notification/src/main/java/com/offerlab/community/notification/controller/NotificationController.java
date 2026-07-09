package com.offerlab.community.notification.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.notification.api.NotificationFacade;
import com.offerlab.community.notification.api.dto.NotificationReadAllResultDTO;
import com.offerlab.community.notification.api.dto.NotificationRealtimeStatusDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    static final int MAX_READ_BATCH_SIZE = 200;

    private final NotificationFacade facade;

    @GetMapping
    @RateLimit(key = "'notification:list:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<NotificationListItemResponse>> list(@RequestParam(required = false) @Size(max = 32) String type,
                                                                 @RequestParam(defaultValue = "0") @Size(max = 64) String cursor,
                                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        PageResult<Map<String, Object>> page = facade.listNotifications(UserContext.require(), type, cursor, size);
        List<NotificationListItemResponse> items = page.getItems() == null ? List.of() : page.getItems().stream()
                .map(this::toResponse)
                .toList();
        PageResult<NotificationListItemResponse> response = PageResult.of(items, page.getNextCursor(), page.getHasMore());
        response.setTotal(page.getTotal());
        response.setSource(page.getSource());
        response.setDegraded(page.getDegraded());
        response.setFallbackReason(page.getFallbackReason());
        response.setScanLimit(page.getScanLimit());
        response.setDiagnostics(page.getDiagnostics());
        return Result.ok(response);
    }

    @GetMapping("/unread-count")
    @RateLimit(key = "'notification:unread-count:' + #uid", rate = 180, per = 60, failOpen = false)
    public Result<Map<String, Long>> unreadCount() {
        return Result.ok(facade.getUnreadCountByType(UserContext.require()));
    }

    @GetMapping("/realtime-status")
    @RateLimit(key = "'notification:realtime-status:' + #uid", rate = 180, per = 60, failOpen = false)
    public Result<NotificationRealtimeStatusDTO> realtimeStatus() {
        return Result.ok(facade.getRealtimeStatus(UserContext.require()));
    }

    @PostMapping("/read")
    @RateLimit(key = "'notification:read:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<Void> read(@Valid @RequestBody ReadReq req) {
        facade.markAsRead(UserContext.require(), req == null ? List.of() : req.getIds());
        return Result.ok();
    }

    @PostMapping("/read-all")
    @RateLimit(key = "'notification:read-all:' + #uid", rate = 20, per = 60, failOpen = false)
    public Result<NotificationReadAllResultDTO> readAll() {
        return Result.ok(facade.markAllAsRead(UserContext.require()));
    }

    public static class ReadReq {
        @Size(max = MAX_READ_BATCH_SIZE)
        private List<@NotNull @Positive Long> ids;

        public List<Long> getIds() {
            return ids;
        }

        public void setIds(List<Long> ids) {
            this.ids = ids;
        }
    }

    @SuppressWarnings("unchecked")
    private NotificationListItemResponse toResponse(Map<String, Object> item) {
        if (item == null) {
            return NotificationListItemResponse.builder().build();
        }
        Map<String, Object> content = item.get("content") instanceof Map<?, ?> raw
                ? sanitizeContent(raw)
                : Map.of();
        return NotificationListItemResponse.builder()
                .id(toLong(item.get("id")))
                .sender((com.offerlab.community.user.api.dto.UserBriefDTO) item.get("sender"))
                .type((String) item.get("type"))
                .content(content)
                .isRead((Boolean) item.get("isRead"))
                .createTime((java.time.LocalDateTime) item.get("createTime"))
                .notificationIds((List<Long>) item.get("notificationIds"))
                .aggregateCount(toInteger(item.get("aggregateCount")))
                .unreadCount(toInteger(item.get("unreadCount")))
                .build();
    }

    private Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer toInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static final Set<String> SAFE_CONTENT_FIELDS = Set.of(
            "action", "targetType", "targetId", "postId", "postTitle", "commentId", "userId",
            "requestId", "sourceType", "reportId", "userStatus", "reportStatus", "status",
            "resultText", "userResultText", "targetPath", "jumpPath", "href", "topicId",
            "topicSlug", "topicName", "topics", "placementType", "placementKey", "source",
            "dedupKey", "message", "title", "eventId", "eventType", "contentId", "contentTitle",
            "placementId", "placementLabel", "sectionKey", "reason", "reasonText", "entrance"
    );

    private Map<String, Object> sanitizeContent(Map<?, ?> raw) {
        Map<String, Object> content = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (!(key instanceof String field) || !SAFE_CONTENT_FIELDS.contains(field)) {
                return;
            }
            if ("targetPath".equals(field) || "jumpPath".equals(field) || "href".equals(field)) {
                String path = safePath(value);
                if (path != null) {
                    content.put(field, path);
                }
                return;
            }
            if ("topics".equals(field)) {
                List<Map<String, Object>> topics = sanitizeTopics(value);
                if (!topics.isEmpty()) {
                    content.put(field, topics);
                }
                return;
            }
            Object safeValue = safeScalar(value);
            if (safeValue != null) {
                content.put(field, safeValue);
            }
        });
        return content;
    }

    private List<Map<String, Object>> sanitizeTopics(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::sanitizeTopic)
                .filter(topic -> !topic.isEmpty())
                .toList();
    }

    private Map<String, Object> sanitizeTopic(Map<?, ?> raw) {
        Map<String, Object> topic = new LinkedHashMap<>();
        putSafeScalar(topic, "topicId", raw.get("topicId"));
        putSafeScalar(topic, "topicSlug", raw.get("topicSlug"));
        putSafeScalar(topic, "topicName", raw.get("topicName"));
        return topic;
    }

    private void putSafeScalar(Map<String, Object> target, String field, Object value) {
        Object safeValue = safeScalar(value);
        if (safeValue != null) {
            target.put(field, safeValue);
        }
    }

    private Object safeScalar(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
        }
        return null;
    }

    private String safePath(Object value) {
        if (!(value instanceof String text)) {
            return null;
        }
        String path = text.trim();
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (path.isEmpty()
                || !path.startsWith("/")
                || path.startsWith("//")
                || path.startsWith("/api/")
                || path.contains("\\")
                || path.matches(".*\\s+.*")
                || lower.startsWith("/javascript:")
                || lower.startsWith("/data:")
                || lower.contains("://")) {
            return null;
        }
        return path.length() > 300 ? null : path;
    }
}
