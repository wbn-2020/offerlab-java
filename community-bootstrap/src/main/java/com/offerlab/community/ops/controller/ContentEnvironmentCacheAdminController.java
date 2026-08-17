package com.offerlab.community.ops.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.common.utils.RiskConfirmation;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.ops.application.ContentEnvironmentCacheInvalidationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/content-environment")
@Validated
public class ContentEnvironmentCacheAdminController {

    private static final String ACTION = "CONTENT_ENV_CACHE_INVALIDATE";
    private static final String RESOURCE_TYPE = "POST_CACHE_BATCH";

    private final ContentEnvironmentCacheInvalidationService invalidationService;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;

    public ContentEnvironmentCacheAdminController(
            ContentEnvironmentCacheInvalidationService invalidationService,
            AdminPermissionService adminPermissionService,
            AdminAuditService adminAuditService) {
        this.invalidationService = invalidationService;
        this.adminPermissionService = adminPermissionService;
        this.adminAuditService = adminAuditService;
    }

    @PostMapping("/cache-invalidation")
    @RateLimit(key = "'content-env:cache-invalidation:' + #uid", rate = 2, per = 3600, failOpen = false)
    public Result<ContentEnvironmentCacheInvalidationService.InvalidationResult> invalidate(
            @Valid @RequestBody InvalidationRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireAdmin(uid);
        String remark = RiskConfirmation.requireCritical(request.remark(), request.confirmationPhrase());
        String operationId = UUID.randomUUID().toString();
        adminAuditService.requireWritable(ACTION, RESOURCE_TYPE, operationId);
        Map<String, Object> started = Map.of(
                "operationId", operationId,
                "status", "STARTED",
                "postIds", request.postIds()
        );
        adminAuditService.recordRequired(uid, ACTION, RESOURCE_TYPE, operationId, null, started, remark);
        try {
            ContentEnvironmentCacheInvalidationService.InvalidationResult result =
                    invalidationService.invalidate(operationId, request.postIds());
            adminAuditService.recordRequired(uid, ACTION, RESOURCE_TYPE, operationId, started, result, remark);
            return Result.ok(result);
        } catch (RuntimeException e) {
            adminAuditService.record(uid, ACTION, RESOURCE_TYPE, operationId, started,
                    Map.of("operationId", operationId, "status", "FAILED",
                            "errorType", e.getClass().getSimpleName()),
                    remark);
            throw e;
        }
    }

    public record InvalidationRequest(
            @NotEmpty @Size(max = 100) List<@NotNull @Positive Long> postIds,
            @Size(max = 500) String remark,
            @Size(max = 32) String confirmationPhrase) {
    }
}
