package com.offerlab.community.analytics.controller;

import com.offerlab.community.analytics.api.dto.GrowthEventTrackCmd;
import com.offerlab.community.analytics.application.GrowthEventService;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@PublicApi
@RequestMapping("/api/v1/growth")
@RequiredArgsConstructor
public class GrowthEventController {

    private final GrowthEventService growthEventService;

    @PostMapping("/track")
    @RateLimit(key = "'growth:track:' + #http.remoteAddr", rate = 120, per = 60)
    public Result<Map<String, Object>> track(@Valid @RequestBody GrowthEventTrackCmd cmd, HttpServletRequest http) {
        return Result.ok(Map.of("tracked", growthEventService.track(cmd)));
    }
}
