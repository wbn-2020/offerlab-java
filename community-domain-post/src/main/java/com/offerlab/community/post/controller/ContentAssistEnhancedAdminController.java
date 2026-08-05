package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedExceptionDTO;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedReconcileCmd;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedReconcileResultDTO;
import com.offerlab.community.post.application.ContentAssistEnhancedOperationsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content-assist/admin/enhanced")
@RequiredArgsConstructor
@Validated
public class ContentAssistEnhancedAdminController {
    private final ContentAssistEnhancedOperationsService operationsService;

    @GetMapping("/exceptions")
    public Result<?> exceptions(
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size,
            @RequestParam(required = false) @Min(1) @Max(100) Integer limit) {
        int requestedSize = size != null ? size : (limit != null ? limit : 20);
        PageResult<ContentAssistEnhancedExceptionDTO> page = operationsService.exceptions(
                UserContext.require(), cursor, requestedSize);
        if (cursor == null && size == null) {
            return Result.ok(page.getItems());
        }
        return Result.ok(page);
    }

    @PostMapping("/reconcile")
    @RateLimit(key = "'content-assist:admin:enhanced-reconcile:' + #uid", rate = 3, per = 600, failOpen = false)
    public Result<ContentAssistEnhancedReconcileResultDTO> reconcile(
            @Valid @RequestBody ContentAssistEnhancedReconcileCmd cmd) {
        return Result.ok(operationsService.reconcile(cmd, UserContext.require()));
    }
}
