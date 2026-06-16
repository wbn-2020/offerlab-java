package com.offerlab.community.search.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
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
    private final AdminPermissionService adminPermissionService;

    @GetMapping
    public Result<List<ReviewQueueItemPO>> list(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) String sourceType,
                                                @RequestParam(required = false) String riskLevel,
                                                @RequestParam(defaultValue = "50") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(reviewQueueService.list(status, sourceType, riskLevel, limit));
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(reviewQueueService.status());
    }

    @PostMapping
    public Result<ReviewQueueItemPO> create(@Valid @RequestBody ReviewQueueCreateCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(reviewQueueService.create(cmd, uid));
    }

    @PostMapping("/{id}/claim")
    public Result<ReviewQueueItemPO> claim(@PathVariable Long id) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(reviewQueueService.claim(id, uid));
    }

    @PostMapping("/{id}/release")
    public Result<ReviewQueueItemPO> release(@PathVariable Long id,
                                             @Valid @RequestBody(required = false) ReviewQueueActionCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(reviewQueueService.release(id, uid, note(cmd)));
    }

    @PostMapping("/{id}/approve")
    public Result<ReviewQueueItemPO> approve(@PathVariable Long id,
                                             @Valid @RequestBody(required = false) ReviewQueueActionCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(reviewQueueService.approve(id, uid, note(cmd), confirmationPhrase(cmd)));
    }

    @PostMapping("/{id}/reject")
    public Result<ReviewQueueItemPO> reject(@PathVariable Long id,
                                            @Valid @RequestBody ReviewQueueActionCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(reviewQueueService.reject(id, uid, note(cmd), confirmationPhrase(cmd)));
    }

    @PostMapping("/{id}/close")
    public Result<ReviewQueueItemPO> close(@PathVariable Long id,
                                           @Valid @RequestBody ReviewQueueActionCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(reviewQueueService.close(id, uid, note(cmd), confirmationPhrase(cmd)));
    }

    private static String note(ReviewQueueActionCmd cmd) {
        return cmd == null ? null : cmd.getNote();
    }

    private static String confirmationPhrase(ReviewQueueActionCmd cmd) {
        return cmd == null ? null : cmd.getConfirmationPhrase();
    }
}
