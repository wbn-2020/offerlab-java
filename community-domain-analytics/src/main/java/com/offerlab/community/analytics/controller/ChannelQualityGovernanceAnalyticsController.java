package com.offerlab.community.analytics.controller;

import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsBreakdownDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsOverviewDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsProjectionHealthDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsRebuildCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsRebuildResultDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsTrendsDTO;
import com.offerlab.community.analytics.application.ChannelQualityGovernanceAnalyticsQueryService;
import com.offerlab.community.analytics.application.ChannelQualityGovernanceAnalyticsRebuildService;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/community-health/quality-governance-analytics")
@RequiredArgsConstructor
public class ChannelQualityGovernanceAnalyticsController {

    private final ChannelQualityGovernanceAnalyticsQueryService analyticsQueryService;
    private final ChannelQualityGovernanceAnalyticsRebuildService analyticsRebuildService;

    @GetMapping("/overview")
    @RateLimit(key = "'channel-quality-governance-analytics:overview:' + #uid",
            rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityGovernanceAnalyticsOverviewDTO> overview(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) Integer domain,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) String riskCategory,
            @RequestParam(required = false) Integer recurrenceWindowDays) {
        return Result.ok(analyticsQueryService.overview(
                from, to, domain, triggerType, riskCategory, recurrenceWindowDays, UserContext.require()));
    }

    @GetMapping("/trends")
    @RateLimit(key = "'channel-quality-governance-analytics:trends:' + #uid",
            rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityGovernanceAnalyticsTrendsDTO> trends(
            @RequestParam String metricCode,
            @RequestParam String grain,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) Integer domain,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) String riskCategory,
            @RequestParam(required = false) Integer recurrenceWindowDays) {
        return Result.ok(analyticsQueryService.trends(
                metricCode, grain, from, to, domain, triggerType, riskCategory, recurrenceWindowDays,
                UserContext.require()));
    }

    @GetMapping("/breakdown")
    @RateLimit(key = "'channel-quality-governance-analytics:breakdown:' + #uid",
            rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityGovernanceAnalyticsBreakdownDTO> breakdown(
            @RequestParam String metricCode,
            @RequestParam String dimension,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) Integer domain) {
        return Result.ok(analyticsQueryService.breakdown(
                metricCode, dimension, from, to, domain, UserContext.require()));
    }

    @GetMapping("/projection-health")
    @RateLimit(key = "'channel-quality-governance-analytics:projection-health:' + #uid",
            rate = 20, per = 60, failOpen = false)
    public Result<ChannelQualityGovernanceAnalyticsProjectionHealthDTO> projectionHealth() {
        return Result.ok(analyticsQueryService.projectionHealth(UserContext.require()));
    }

    @PostMapping("/projection/rebuild")
    @RateLimit(key = "'channel-quality-governance-analytics:projection-rebuild:' + #uid",
            rate = 5, per = 300, failOpen = false)
    public Result<ChannelQualityGovernanceAnalyticsRebuildResultDTO> rebuild(
            @Valid @RequestBody ChannelQualityGovernanceAnalyticsRebuildCmd cmd) {
        return Result.ok(analyticsRebuildService.request(cmd, UserContext.require()));
    }
}
