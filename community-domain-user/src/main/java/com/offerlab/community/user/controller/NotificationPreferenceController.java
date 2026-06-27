package com.offerlab.community.user.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.user.api.dto.NotificationPreferenceDTO;
import com.offerlab.community.user.application.UserApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications/preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final UserApplicationService userService;

    @GetMapping
    public Result<NotificationPreferenceDTO> getPreferences() {
        Long uid = UserContext.require();
        return Result.ok(userService.getNotificationPreference(uid));
    }

    @PutMapping
    @RateLimit(key = "'notification:preferences:update:' + #uid", rate = 30, per = 60)
    public Result<NotificationPreferenceDTO> updatePreferences(@RequestBody NotificationPreferenceDTO setting) {
        Long uid = UserContext.require();
        return Result.ok(userService.updateNotificationPreference(uid, setting));
    }
}
