package com.offerlab.community.analytics.controller;

import com.offerlab.community.analytics.api.dto.CreatorChallengeCompleteCmd;
import com.offerlab.community.analytics.api.dto.CreatorChallengeCompletionResultDTO;
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

@RestController
@RequestMapping("/api/v1/creator-growth/challenges")
@RequiredArgsConstructor
public class CreatorChallengeController {
    private final CreatorChallengeService creatorChallengeService;

    @GetMapping("/workspace")
    public Result<CreatorChallengeWorkspaceDTO> workspace() {
        return Result.ok(creatorChallengeService.workspace(UserContext.require()));
    }

    @PostMapping("/{challengeId}/join")
    @RateLimit(key = "'creator-challenge:join:' + #uid", rate = 12, per = 300, failOpen = false)
    public Result<CreatorChallengeWorkspaceDTO.CreatorChallengeDTO> join(@PathVariable Long challengeId) {
        return Result.ok(creatorChallengeService.join(UserContext.require(), challengeId));
    }

    @PostMapping("/{challengeId}/withdraw")
    @RateLimit(key = "'creator-challenge:withdraw:' + #uid", rate = 12, per = 300, failOpen = false)
    public Result<CreatorChallengeWorkspaceDTO.CreatorChallengeDTO> withdraw(@PathVariable Long challengeId) {
        return Result.ok(creatorChallengeService.withdraw(UserContext.require(), challengeId));
    }

    @PostMapping("/{challengeId}/complete")
    @RateLimit(key = "'creator-challenge:complete:' + #uid", rate = 8, per = 300, failOpen = false)
    public Result<CreatorChallengeCompletionResultDTO> complete(
            @PathVariable Long challengeId,
            @Valid @RequestBody CreatorChallengeCompleteCmd cmd) {
        return Result.ok(creatorChallengeService.complete(UserContext.require(), challengeId, cmd));
    }
}
