package com.offerlab.community.analytics.controller;

import com.offerlab.community.analytics.api.AnalyticsFacade;
import com.offerlab.community.analytics.api.dto.GrowthFunnelDTO;
import com.offerlab.community.analytics.api.dto.GrowthEventSummaryDTO;
import com.offerlab.community.analytics.application.GrowthEventService;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.domain.model.PostDomain;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsFacade facade;
    private final GrowthEventService growthEventService;
    private final AdminPermissionService adminPermissionService;

    @PublicApi
    @GetMapping("/trend")
    @RateLimit(key = "'public:dashboard:trend:' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<Map<String, Object>> trend(@RequestParam(defaultValue = "30d") String range,
                                             @RequestParam(required = false) String period,
                                             @RequestParam(required = false) Integer domain,
                                             HttpServletRequest request) {
        return Result.ok(facade.getTrendDashboard(period == null ? range : period, requireOptionalDomain(domain)));
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(facade.getPersonalDashboard(UserContext.require()));
    }

    @GetMapping("/growth")
    public Result<GrowthEventSummaryDTO> growth(@RequestParam(defaultValue = "30") int days,
                                                @RequestParam(required = false) Integer domain) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        return Result.ok(growthEventService.summary(days, requireOptionalDomain(domain)));
    }

    @GetMapping("/growth/funnel")
    public Result<GrowthFunnelDTO> growthFunnel(@RequestParam(defaultValue = "30") int days,
                                                @RequestParam(required = false) Integer domain) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_OPS);
        return Result.ok(growthEventService.funnel(days, requireOptionalDomain(domain)));
    }

    private Integer requireOptionalDomain(Integer domain) {
        if (domain == null) {
            return null;
        }
        if (PostDomain.isValid(domain)) {
            return domain;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }
}
