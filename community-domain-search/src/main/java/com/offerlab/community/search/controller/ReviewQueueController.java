package com.offerlab.community.search.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.search.api.dto.ReviewQueueActionCmd;
import com.offerlab.community.search.api.dto.ReviewQueueCreateCmd;
import com.offerlab.community.search.application.ReviewQueueService;
import com.offerlab.community.search.infrastructure.persistence.po.ReviewQueueItemPO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/review-queue")
@RequiredArgsConstructor
public class ReviewQueueController {

    private final ReviewQueueService reviewQueueService;

    @GetMapping
    @RateLimit(key = "'review-queue:list:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<List<ReviewQueueItemPO>> list(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) String sourceType,
                                                @RequestParam(required = false) String riskLevel,
                                                @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(reviewQueueService.list(status, sourceType, riskLevel, limit, UserContext.require()));
    }

    @GetMapping("/status")
    @RateLimit(key = "'review-queue:status:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<Map<String, Object>> status() {
        return Result.ok(reviewQueueService.status(UserContext.require()));
    }

    @PostMapping
    @RateLimit(key = "'review-queue:create:' + #uid", rate = 30, per = 60, failOpen = false)
    public Result<ReviewQueueItemPO> create(@Valid @RequestBody ReviewQueueCreateCmd cmd) {
        Long uid = UserContext.require();
        return Result.ok(reviewQueueService.create(cmd, uid));
    }

    @PostMapping("/{id}/claim")
    @RateLimit(key = "'review-queue:claim:' + #uid + ':' + #id", rate = 30, per = 60, failOpen = false)
    public Result<ReviewQueueItemPO> claim(@PathVariable Long id) {
        Long uid = UserContext.require();
        return Result.ok(reviewQueueService.claim(id, uid));
    }

    @PostMapping("/{id}/release")
    @RateLimit(key = "'review-queue:release:' + #uid + ':' + #id", rate = 30, per = 60, failOpen = false)
    public Result<ReviewQueueItemPO> release(@PathVariable Long id,
                                             @Valid @RequestBody(required = false) ReviewQueueActionCmd cmd) {
        Long uid = UserContext.require();
        return Result.ok(reviewQueueService.release(id, uid, note(cmd)));
    }

    @PostMapping("/{id}/approve")
    @RateLimit(key = "'review-queue:approve:' + #uid + ':' + #id", rate = 20, per = 60, failOpen = false)
    public Result<ReviewQueueItemPO> approve(@PathVariable Long id,
                                             @Valid @RequestBody(required = false) ReviewQueueActionCmd cmd) {
        Long uid = UserContext.require();
        return Result.ok(reviewQueueService.approve(id, uid, note(cmd), confirmationPhrase(cmd)));
    }

    @PostMapping("/{id}/reject")
    @RateLimit(key = "'review-queue:reject:' + #uid + ':' + #id", rate = 20, per = 60, failOpen = false)
    public Result<ReviewQueueItemPO> reject(@PathVariable Long id,
                                            @Valid @RequestBody ReviewQueueActionCmd cmd) {
        Long uid = UserContext.require();
        return Result.ok(reviewQueueService.reject(id, uid, note(cmd), confirmationPhrase(cmd)));
    }

    @PostMapping("/{id}/close")
    @RateLimit(key = "'review-queue:close:' + #uid + ':' + #id", rate = 20, per = 60, failOpen = false)
    public Result<ReviewQueueItemPO> close(@PathVariable Long id,
                                           @Valid @RequestBody ReviewQueueActionCmd cmd) {
        Long uid = UserContext.require();
        return Result.ok(reviewQueueService.close(id, uid, note(cmd), confirmationPhrase(cmd)));
    }

    private static String note(ReviewQueueActionCmd cmd) {
        return cmd == null ? null : cmd.getNote();
    }

    private static String confirmationPhrase(ReviewQueueActionCmd cmd) {
        return cmd == null ? null : cmd.getConfirmationPhrase();
    }
}
