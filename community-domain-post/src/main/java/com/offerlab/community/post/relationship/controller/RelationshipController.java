package com.offerlab.community.post.relationship.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.relationship.api.RelationshipItemDTO;
import com.offerlab.community.post.relationship.api.RelationshipSummaryDTO;
import com.offerlab.community.post.relationship.application.RelationshipQueryService;
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
public class RelationshipController {

    private final RelationshipQueryService relationshipQueryService;

    @GetMapping("/relationships")
    @RateLimit(key = "'user:relationships:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<RelationshipItemDTO>> list(
            @RequestParam(required = false) @Size(max = 24) String sourceType,
            @RequestParam(required = false) @Size(max = 24) String mode,
            @RequestParam(defaultValue = "0") String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(relationshipQueryService.list(
                UserContext.require(), sourceType, mode, cursor, size));
    }

    @GetMapping("/relationship-summary")
    @RateLimit(key = "'user:relationship-summary:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<RelationshipSummaryDTO> summary() {
        return Result.ok(relationshipQueryService.summary(UserContext.require()));
    }
}
