package com.offerlab.community.notification.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.notification.api.NotificationFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        return Result.ok(Map.of("count", facade.getUnreadCount(UserContext.require())));
    }

    @PostMapping("/read-all")
    public Result<Void> readAll() {
        facade.markAllAsRead(UserContext.require());
        return Result.ok();
    }
}
