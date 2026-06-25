package com.offerlab.community.analytics.controller;

import com.offerlab.community.analytics.api.dto.GrowthProfileDTO;
import com.offerlab.community.analytics.api.dto.GrowthReportDTO;
import com.offerlab.community.analytics.application.GrowthInsightService;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/growth")
@RequiredArgsConstructor
public class GrowthInsightController {

    private final GrowthInsightService growthInsightService;

    @GetMapping("/profile")
    public Result<GrowthProfileDTO> profile(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(growthInsightService.profile(UserContext.require(), days));
    }

    @GetMapping("/report")
    public Result<GrowthReportDTO> report(@RequestParam(defaultValue = "weekly") String period) {
        return Result.ok(growthInsightService.report(UserContext.require(), period));
    }
}
