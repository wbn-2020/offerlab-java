package com.offerlab.community.notification.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.notification.api.NotificationFacade;
import com.offerlab.community.notification.api.dto.NotificationRealtimeStatusDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    static final int MAX_READ_BATCH_SIZE = 200;

    private final NotificationFacade facade;

    @GetMapping
    public Result<PageResult<NotificationListItemResponse>> list(@RequestParam(required = false) String type,
                                                                 @RequestParam(defaultValue = "0") long cursor,
                                                                 @RequestParam(defaultValue = "20") int size) {
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
    public Result<Map<String, Long>> unreadCount() {
        return Result.ok(facade.getUnreadCountByType(UserContext.require()));
    }

    @GetMapping("/realtime-status")
    public Result<NotificationRealtimeStatusDTO> realtimeStatus() {
        return Result.ok(facade.getRealtimeStatus(UserContext.require()));
    }

    @PostMapping("/read")
    @RateLimit(key = "'notification:read:' + #uid", rate = 120, per = 60)
    public Result<Void> read(@Valid @RequestBody ReadReq req) {
        facade.markAsRead(UserContext.require(), req == null ? List.of() : req.getIds());
        return Result.ok();
    }

    @PostMapping("/read-all")
    @RateLimit(key = "'notification:read-all:' + #uid", rate = 20, per = 60)
    public Result<Void> readAll() {
        facade.markAllAsRead(UserContext.require());
        return Result.ok();
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
                ? new LinkedHashMap<>((Map<String, Object>) raw)
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
}
