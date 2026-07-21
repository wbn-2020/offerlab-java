package com.offerlab.community.interaction.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.interaction.api.dto.PostOutcomeDTO;
import com.offerlab.community.interaction.api.dto.PostOutcomeReviewCmd;
import com.offerlab.community.interaction.application.PostOutcomeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/post-outcomes")
@RequiredArgsConstructor
@Validated
public class PostOutcomeAdminController {

    private final PostOutcomeService outcomeService;

    @PutMapping("/{outcomeId}/review")
    @RateLimit(key = "'post-outcome:review:' + #uid", rate = 60, per = 300, failOpen = false)
    public Result<PostOutcomeDTO> review(@PathVariable @Positive Long outcomeId,
                                         @Valid @RequestBody PostOutcomeReviewCmd cmd) {
        return Result.ok(outcomeService.review(
                outcomeId, UserContext.require(), Boolean.TRUE.equals(cmd.getApproved()), cmd.getNote()));
    }
}
