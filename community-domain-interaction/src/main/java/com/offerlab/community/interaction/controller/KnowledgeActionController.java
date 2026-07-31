package com.offerlab.community.interaction.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.interaction.api.dto.KnowledgeActionItemDTO;
import com.offerlab.community.interaction.api.dto.KnowledgeActionPage;
import com.offerlab.community.interaction.api.dto.KnowledgeActionSummaryDTO;
import com.offerlab.community.interaction.application.KnowledgeActionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Validated
public class KnowledgeActionController {

    private final KnowledgeActionService actionService;

    @GetMapping("/knowledge-actions")
    @RateLimit(key = "'knowledge-actions:list:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<KnowledgeActionPage<KnowledgeActionItemDTO>> actions(
            @RequestParam(required = false) @Size(max = 32) String type,
            @RequestParam(required = false) @Size(max = 32) String status,
            @RequestParam(defaultValue = "0") @Size(max = 512) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(actionService.list(UserContext.require(), type, status, cursor, size));
    }

    @GetMapping("/knowledge-action-summary")
    @RateLimit(key = "'knowledge-actions:summary:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<KnowledgeActionSummaryDTO> summary() {
        return Result.ok(actionService.summary(UserContext.require()));
    }
}
