package com.offerlab.community.analytics.collaboration.controller;

import com.offerlab.community.analytics.collaboration.api.PublicContributionProfileDTO;
import com.offerlab.community.analytics.collaboration.application.CollaborationAnalyticsService;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class PublicContributionController {

    private final CollaborationAnalyticsService collaborationAnalyticsService;

    @PublicApi
    @GetMapping("/{uid}/public-contributions")
    @RateLimit(key = "'public:collaboration:public-contributions:' + #request.remoteAddr + ':' + #uid",
            rate = 60, per = 60, failOpen = false)
    public Result<PublicContributionProfileDTO> publicContributions(
            @PathVariable Long uid,
            @RequestParam(defaultValue = "200") int limit,
            HttpServletRequest request) {
        return Result.ok(collaborationAnalyticsService.publicContributions(uid, limit));
    }
}
