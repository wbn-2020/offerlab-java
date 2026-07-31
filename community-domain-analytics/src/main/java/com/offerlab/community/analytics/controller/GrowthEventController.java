package com.offerlab.community.analytics.controller;

import com.offerlab.community.analytics.api.dto.EffectiveReadCompleteCmd;
import com.offerlab.community.analytics.api.dto.EffectiveReadCompleteDTO;
import com.offerlab.community.analytics.api.dto.EffectiveReadHeartbeatCmd;
import com.offerlab.community.analytics.api.dto.EffectiveReadHeartbeatDTO;
import com.offerlab.community.analytics.api.dto.EffectiveReadSessionDTO;
import com.offerlab.community.analytics.api.dto.EffectiveReadSessionStartCmd;
import com.offerlab.community.analytics.api.dto.GrowthEventTrackCmd;
import com.offerlab.community.analytics.application.EffectiveReadService;
import com.offerlab.community.analytics.application.GrowthEventService;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
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
@RequestMapping("/api/v1/growth")
@RequiredArgsConstructor
public class GrowthEventController {

    private final GrowthEventService growthEventService;
    private final EffectiveReadService effectiveReadService;

    @PostMapping("/track")
    @PublicApi
    @RateLimit(key = "'growth:track:' + #http.remoteAddr", rate = 120, per = 60)
    public Result<Map<String, Object>> track(@Valid @RequestBody GrowthEventTrackCmd cmd, HttpServletRequest http) {
        return Result.ok(Map.of("tracked", growthEventService.track(cmd)));
    }

    @PostMapping("/effective-read/session")
    @RateLimit(key = "'growth:effective-read:start:' + #uid", rate = 30, per = 60, failOpen = false)
    public Result<EffectiveReadSessionDTO> startEffectiveRead(
            @Valid @RequestBody EffectiveReadSessionStartCmd cmd) {
        return Result.ok(effectiveReadService.startSession(UserContext.require(), cmd));
    }

    @PostMapping("/effective-read/heartbeat")
    @RateLimit(key = "'growth:effective-read:heartbeat:' + #uid", rate = 240, per = 60, failOpen = false)
    public Result<EffectiveReadHeartbeatDTO> heartbeatEffectiveRead(
            @Valid @RequestBody EffectiveReadHeartbeatCmd cmd) {
        return Result.ok(effectiveReadService.heartbeat(UserContext.require(), cmd));
    }

    @PostMapping("/effective-read/complete")
    @RateLimit(key = "'growth:effective-read:complete:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<EffectiveReadCompleteDTO> completeEffectiveRead(
            @Valid @RequestBody EffectiveReadCompleteCmd cmd) {
        return Result.ok(effectiveReadService.complete(UserContext.require(), cmd));
    }

    @PostMapping("/effective-read/abandon")
    @RateLimit(key = "'growth:effective-read:abandon:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<Map<String, Boolean>> abandonEffectiveRead(
            @Valid @RequestBody EffectiveReadCompleteCmd cmd) {
        return Result.ok(Map.of("abandoned",
                effectiveReadService.abandon(UserContext.require(), cmd)));
    }
}
