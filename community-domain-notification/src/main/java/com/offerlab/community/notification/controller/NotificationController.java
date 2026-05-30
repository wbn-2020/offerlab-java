package com.offerlab.community.notification.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.notification.api.NotificationFacade;
import com.offerlab.community.notification.api.dto.NotificationRealtimeStatusDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationFacade facade;

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(@RequestParam(required = false) String type,
                                                        @RequestParam(defaultValue = "0") long cursor,
                                                        @RequestParam(defaultValue = "20") int size) {
        return Result.ok(facade.listNotifications(UserContext.require(), type, cursor, size));
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
    public Result<Void> read(@RequestBody ReadReq req) {
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
        private List<Long> ids;

        public List<Long> getIds() {
            return ids;
        }

        public void setIds(List<Long> ids) {
            this.ids = ids;
        }
    }
}
