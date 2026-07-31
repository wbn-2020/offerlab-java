package com.offerlab.community.analytics.controller;

import com.offerlab.community.analytics.api.dto.ProjectionHealthDTO;
import com.offerlab.community.analytics.api.dto.ProjectionIssueDTO;
import com.offerlab.community.analytics.api.dto.ProjectionReconcileCmd;
import com.offerlab.community.analytics.api.dto.ProjectionReconcileResultDTO;
import com.offerlab.community.analytics.application.ProjectionHealthService;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/community-health/projections")
@RequiredArgsConstructor
@Validated
public class ProjectionHealthController {

    private final ProjectionHealthService service;

    @GetMapping
    @RateLimit(key = "'community-health:projections:' + #uid", rate = 30, per = 60, failOpen = false)
    public Result<List<ProjectionHealthDTO>> summary() {
        return Result.ok(service.summary(UserContext.require()));
    }

    @GetMapping("/{projectionType}/issues")
    @RateLimit(key = "'community-health:projection-issues:' + #uid", rate = 60, per = 60, failOpen = false)
    public Result<PageResult<ProjectionIssueDTO>> issues(
            @PathVariable @Size(max = 64) String projectionType,
            @RequestParam(defaultValue = "0") @Size(max = 256) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(service.issues(
                projectionType, cursor, size, UserContext.require()));
    }

    @PostMapping("/{projectionType}/reconcile")
    @RateLimit(key = "'community-health:projection-reconcile:' + #uid", rate = 5, per = 600, failOpen = false)
    public Result<ProjectionReconcileResultDTO> reconcile(
            @PathVariable @Size(max = 64) String projectionType,
            @Valid @RequestBody ProjectionReconcileCmd cmd) {
        return Result.ok(service.reconcile(
                projectionType, cmd, UserContext.require()));
    }
}
