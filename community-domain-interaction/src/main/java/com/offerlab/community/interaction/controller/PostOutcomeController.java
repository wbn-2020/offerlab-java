package com.offerlab.community.interaction.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.interaction.api.dto.PostOutcomeCmd;
import com.offerlab.community.interaction.api.dto.PostOutcomeDTO;
import com.offerlab.community.interaction.api.dto.PostOutcomeSummaryDTO;
import com.offerlab.community.interaction.application.PostOutcomeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts/{postId}/outcomes")
@RequiredArgsConstructor
@Validated
public class PostOutcomeController {

    private final PostOutcomeService outcomeService;

    @PublicApi
    @GetMapping("/summary")
    @RateLimit(key = "'public:post-outcome:summary:' + #postId + ':' + #request.remoteAddr",
            rate = 180, per = 60, failOpen = false)
    public Result<PostOutcomeSummaryDTO> summary(@PathVariable @Positive Long postId,
                                                 HttpServletRequest request) {
        return Result.ok(outcomeService.publicSummary(postId));
    }

    @GetMapping("/mine")
    @RateLimit(key = "'post-outcome:mine:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<PostOutcomeDTO> mine(@PathVariable @Positive Long postId) {
        return Result.ok(outcomeService.mine(postId, UserContext.require()));
    }

    @PutMapping("/mine")
    @RateLimit(key = "'post-outcome:save:' + #uid", rate = 30, per = 300, failOpen = false)
    public Result<PostOutcomeDTO> saveMine(@PathVariable @Positive Long postId,
                                           @Valid @RequestBody PostOutcomeCmd cmd) {
        return Result.ok(outcomeService.saveMine(postId, UserContext.require(), cmd));
    }

    @DeleteMapping("/mine")
    @RateLimit(key = "'post-outcome:withdraw:' + #uid", rate = 30, per = 300, failOpen = false)
    public Result<Void> withdrawMine(@PathVariable @Positive Long postId,
                                     @RequestParam @Positive Integer revision) {
        outcomeService.withdrawMine(postId, UserContext.require(), revision);
        return Result.ok();
    }
}
