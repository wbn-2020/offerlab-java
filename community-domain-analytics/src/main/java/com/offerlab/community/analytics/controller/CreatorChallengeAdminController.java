package com.offerlab.community.analytics.controller;

import com.offerlab.community.analytics.api.dto.CreatorChallengeAdminActionCmd;
import com.offerlab.community.analytics.api.dto.CreatorChallengeAdminCmd;
import com.offerlab.community.analytics.api.dto.CreatorChallengeWorkspaceDTO;
import com.offerlab.community.analytics.application.CreatorChallengeService;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/creator-growth/admin/challenges")
@RequiredArgsConstructor
public class CreatorChallengeAdminController {
    private final CreatorChallengeService creatorChallengeService;

    @GetMapping
    public Result<List<CreatorChallengeWorkspaceDTO.CreatorChallengeDTO>> challenges() {
        return Result.ok(creatorChallengeService.adminChallenges(UserContext.require()));
    }

    @PostMapping
    @RateLimit(key = "'creator-challenge:admin:upsert:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<CreatorChallengeWorkspaceDTO.CreatorChallengeDTO> upsert(
            @Valid @RequestBody CreatorChallengeAdminCmd cmd) {
        return Result.ok(creatorChallengeService.upsert(UserContext.require(), cmd));
    }

    @PostMapping("/{challengeId}/publish")
    @RateLimit(key = "'creator-challenge:admin:publish:' + #uid", rate = 8, per = 300, failOpen = false)
    public Result<CreatorChallengeWorkspaceDTO.CreatorChallengeDTO> publish(
            @PathVariable Long challengeId,
            @Valid @RequestBody CreatorChallengeAdminActionCmd cmd) {
        return Result.ok(creatorChallengeService.publish(UserContext.require(), challengeId, cmd));
    }

    @PostMapping("/{challengeId}/offline")
    @RateLimit(key = "'creator-challenge:admin:offline:' + #uid", rate = 8, per = 300, failOpen = false)
    public Result<CreatorChallengeWorkspaceDTO.CreatorChallengeDTO> offline(
            @PathVariable Long challengeId,
            @Valid @RequestBody CreatorChallengeAdminActionCmd cmd) {
        return Result.ok(creatorChallengeService.offline(UserContext.require(), challengeId, cmd));
    }
}
