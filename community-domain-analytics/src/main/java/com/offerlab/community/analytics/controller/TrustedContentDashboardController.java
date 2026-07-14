package com.offerlab.community.analytics.controller;

import com.offerlab.community.analytics.api.dto.CreatorGrowthWorkspaceDTO;
import com.offerlab.community.analytics.application.CreatorGrowthService;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class TrustedContentDashboardController {

    private final CreatorGrowthService creatorGrowthService;

    @GetMapping("/trusted-content")
    public Result<CreatorGrowthWorkspaceDTO.TrustedContentDTO> trustedContent() {
        return Result.ok(creatorGrowthService.trustedContent(UserContext.require()));
    }
}
