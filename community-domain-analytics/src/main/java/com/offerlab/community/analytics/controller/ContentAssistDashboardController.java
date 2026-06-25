package com.offerlab.community.analytics.controller;

import com.offerlab.community.analytics.api.dto.ContentAssistDashboardDTO;
import com.offerlab.community.analytics.application.ContentAssistDashboardService;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard/ai")
@RequiredArgsConstructor
public class ContentAssistDashboardController {

    private final ContentAssistDashboardService dashboardService;
    private final AdminPermissionService adminPermissionService;

    @GetMapping("/content-assist")
    public Result<ContentAssistDashboardDTO> contentAssist(@RequestParam(defaultValue = "30") int days) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        return Result.ok(dashboardService.summary(days));
    }
}
