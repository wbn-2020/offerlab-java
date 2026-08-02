package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedExceptionDTO;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedReconcileCmd;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedReconcileResultDTO;
import com.offerlab.community.post.application.ContentAssistEnhancedOperationsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/content-assist/admin/enhanced")
@RequiredArgsConstructor
public class ContentAssistEnhancedAdminController {
    private final ContentAssistEnhancedOperationsService operationsService;

    @GetMapping("/exceptions")
    public Result<List<ContentAssistEnhancedExceptionDTO>> exceptions(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(operationsService.exceptions(UserContext.require(), limit));
    }

    @PostMapping("/reconcile")
    @RateLimit(key = "'content-assist:admin:enhanced-reconcile:' + #uid", rate = 3, per = 600, failOpen = false)
    public Result<ContentAssistEnhancedReconcileResultDTO> reconcile(
            @Valid @RequestBody ContentAssistEnhancedReconcileCmd cmd) {
        return Result.ok(operationsService.reconcile(cmd, UserContext.require()));
    }
}
