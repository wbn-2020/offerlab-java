package com.offerlab.community.analytics.controller;

import com.offerlab.community.analytics.api.AnalyticsFacade;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
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

    @PublicApi
    @GetMapping("/trend")
    public Result<Map<String, Object>> trend(@RequestParam(defaultValue = "30d") String range,
                                             @RequestParam(required = false) String period) {
        return Result.ok(facade.getTrendDashboard(period == null ? range : period));
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(facade.getPersonalDashboard(UserContext.require()));
    }
}
