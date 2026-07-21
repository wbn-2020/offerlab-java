package com.offerlab.community.analytics.collaboration.controller;

import com.offerlab.community.analytics.collaboration.api.CollaborationFunnelDTO;
import com.offerlab.community.analytics.collaboration.application.CollaborationAnalyticsService;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/collaboration/analytics")
@RequiredArgsConstructor
public class CollaborationAnalyticsController {

    private final CollaborationAnalyticsService collaborationAnalyticsService;

    @GetMapping("/need-funnel")
    @RateLimit(key = "'collaboration:analytics:need-funnel:' + #uid",
            rate = 30, per = 60, failOpen = false)
    public Result<CollaborationFunnelDTO> needFunnel(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) Integer domain) {
        Long uid = UserContext.require();
        return Result.ok(collaborationAnalyticsService.needFunnel(uid, days, domain));
    }
}
