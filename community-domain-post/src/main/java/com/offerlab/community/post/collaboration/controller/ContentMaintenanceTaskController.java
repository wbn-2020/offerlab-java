package com.offerlab.community.post.collaboration.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskCreateCmd;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceCandidateDTO;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskAttemptDTO;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskCloseCmd;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskDTO;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskReassignCmd;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskReviewContextDTO;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskReviewCmd;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskSubmitCmd;
import com.offerlab.community.post.collaboration.application.ContentMaintenanceTaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
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
@RequestMapping("/api/v1/content-maintenance/tasks")
@RequiredArgsConstructor
@Validated
public class ContentMaintenanceTaskController {

    private final ContentMaintenanceTaskService service;

    @PostMapping
    @RateLimit(key = "'maintenance:create:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<ContentMaintenanceTaskDTO> create(@Valid @RequestBody ContentMaintenanceTaskCreateCmd cmd) {
        return Result.ok(service.create(cmd, UserContext.require()));
    }

    @GetMapping("/mine")
    @RateLimit(key = "'maintenance:mine:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<ContentMaintenanceTaskDTO>> mine(
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(service.listMine(UserContext.require(), status, cursor, size));
    }

    @GetMapping("/queue")
    @RateLimit(key = "'maintenance:queue:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<ContentMaintenanceTaskDTO>> queue(
            @RequestParam(required = false) @Min(1) @Max(5) Integer domain,
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(service.listQueue(domain, status, UserContext.require(), cursor, size));
    }

    @GetMapping("/{taskId}/review-context")
    @RateLimit(key = "'maintenance:review-context:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<ContentMaintenanceTaskReviewContextDTO> reviewContext(
            @PathVariable @Positive Long taskId) {
        return Result.ok(service.reviewContext(taskId, UserContext.require()));
    }

    @GetMapping("/{taskId}/attempts")
    @RateLimit(key = "'maintenance:attempts:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<List<ContentMaintenanceTaskAttemptDTO>> attempts(
            @PathVariable @Positive Long taskId) {
        return Result.ok(service.attempts(taskId, UserContext.require()));
    }

    @GetMapping("/candidates")
    @RateLimit(key = "'maintenance:candidates:' + #uid", rate = 60, per = 60, failOpen = false)
    public Result<PageResult<ContentMaintenanceCandidateDTO>> candidates(
            @RequestParam(required = false) @Min(1) @Max(5) Integer domain,
            @RequestParam(required = false) @Size(max = 24) String sourceType,
            @RequestParam(required = false) @Min(1) @Max(100) Integer contentType,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(service.listCandidates(
                UserContext.require(), domain, sourceType, contentType, cursor, size));
    }

    @PostMapping("/{taskId}/claim")
    @RateLimit(key = "'maintenance:claim:' + #uid", rate = 30, per = 300, failOpen = false)
    public Result<ContentMaintenanceTaskDTO> claim(@PathVariable @Positive Long taskId) {
        return Result.ok(service.claim(taskId, UserContext.require()));
    }

    @PostMapping("/{taskId}/reassign")
    @RateLimit(key = "'maintenance:reassign:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<ContentMaintenanceTaskDTO> reassign(
            @PathVariable @Positive Long taskId,
            @Valid @RequestBody ContentMaintenanceTaskReassignCmd cmd) {
        return Result.ok(service.reassign(taskId, cmd, UserContext.require()));
    }

    @PostMapping("/{taskId}/submit")
    @RateLimit(key = "'maintenance:submit:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<ContentMaintenanceTaskDTO> submit(@PathVariable @Positive Long taskId,
                                                     @Valid @RequestBody ContentMaintenanceTaskSubmitCmd cmd) {
        return Result.ok(service.submit(taskId, cmd, UserContext.require()));
    }

    @PostMapping("/{taskId}/review")
    @RateLimit(key = "'maintenance:review:' + #uid", rate = 30, per = 300, failOpen = false)
    public Result<ContentMaintenanceTaskDTO> review(@PathVariable @Positive Long taskId,
                                                     @Valid @RequestBody ContentMaintenanceTaskReviewCmd cmd) {
        return Result.ok(service.review(taskId, cmd, UserContext.require()));
    }

    @PostMapping("/{taskId}/close")
    @RateLimit(key = "'maintenance:close:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<ContentMaintenanceTaskDTO> close(@PathVariable @Positive Long taskId,
                                                    @Valid @RequestBody ContentMaintenanceTaskCloseCmd cmd) {
        return Result.ok(service.close(taskId, cmd, UserContext.require()));
    }
}
