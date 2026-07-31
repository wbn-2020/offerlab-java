package com.offerlab.community.search.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.common.utils.RiskConfirmation;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.search.api.dto.SearchContentGapConvertCmd;
import com.offerlab.community.search.api.dto.SearchContentGapDTO;
import com.offerlab.community.search.api.dto.SearchContentGapResolveCmd;
import com.offerlab.community.search.api.dto.SearchContentGapReviewCmd;
import com.offerlab.community.search.application.SearchContentGapService;
import com.offerlab.community.search.application.SearchIndexTaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/search/admin")
@RequiredArgsConstructor
@Validated
public class SearchAdminController {

    private final SearchIndexTaskService taskService;
    private final SearchContentGapService contentGapService;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;

    @GetMapping("/content-gaps")
    @RateLimit(key = "'search:content-gaps:list:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<List<SearchContentGapDTO>> listContentGaps(
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(required = false) @Size(max = 12) String riskLevel,
            @RequestParam(required = false) Integer domain,
            @RequestParam(defaultValue = "30") int limit) {
        Long uid = UserContext.require();
        return Result.ok(contentGapService.list(status, riskLevel, domain, limit, uid));
    }

    @PostMapping("/content-gaps/{gapKey}/approve")
    @RateLimit(key = "'search:content-gaps:approve:' + #uid + ':' + #gapKey", rate = 30, per = 300, failOpen = false)
    public Result<SearchContentGapDTO> approveContentGap(@PathVariable String gapKey,
                                                         @Valid @RequestBody SearchContentGapReviewCmd cmd) {
        return Result.ok(contentGapService.approve(gapKey, cmd, UserContext.require()));
    }

    @PostMapping("/content-gaps/{gapKey}/ignore")
    @RateLimit(key = "'search:content-gaps:ignore:' + #uid + ':' + #gapKey", rate = 30, per = 300, failOpen = false)
    public Result<SearchContentGapDTO> ignoreContentGap(@PathVariable String gapKey,
                                                        @Valid @RequestBody SearchContentGapReviewCmd cmd) {
        return Result.ok(contentGapService.ignore(gapKey, cmd, UserContext.require()));
    }

    @PostMapping("/content-gaps/{gapKey}/convert")
    @RateLimit(key = "'search:content-gaps:convert:' + #uid + ':' + #gapKey", rate = 20, per = 300, failOpen = false)
    public Result<SearchContentGapDTO> convertContentGap(@PathVariable String gapKey,
                                                         @Valid @RequestBody SearchContentGapConvertCmd cmd) {
        return Result.ok(contentGapService.convert(gapKey, cmd, UserContext.require()));
    }

    @PostMapping("/content-gaps/{gapKey}/resolve")
    @RateLimit(key = "'search:content-gaps:resolve:' + #uid + ':' + #gapKey", rate = 20, per = 300, failOpen = false)
    public Result<SearchContentGapDTO> resolveContentGap(@PathVariable String gapKey,
                                                         @Valid @RequestBody SearchContentGapResolveCmd cmd) {
        return Result.ok(contentGapService.resolve(gapKey, cmd, UserContext.require()));
    }

    @PostMapping("/rebuild")
    @RateLimit(key = "'search:index:rebuild:' + #uid", rate = 2, per = 3600, failOpen = false)
    public Result<SearchIndexTaskService.SearchIndexTask> rebuildPostIndex(
            @Valid @RequestBody(required = false) RebuildRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireAdmin(uid);
        String remark = RiskConfirmation.requireCritical(request == null ? null : request.remark(),
                request == null ? null : request.confirmationPhrase());
        adminAuditService.requireWritable("POST_INDEX_REBUILD_TASK", "SEARCH_INDEX", null);
        SearchIndexTaskService.SearchIndexTask task = taskService.submitRebuildTask(uid);
        adminAuditService.recordRequired(uid, "POST_INDEX_REBUILD_TASK", "SEARCH_INDEX", task.getTaskId(),
                null,
                Map.of("taskId", task.getTaskId(), "type", task.getType(), "status", task.getStatus()),
                remark);
        return Result.ok(task);
    }

    @GetMapping("/tasks/{taskId}")
    public Result<SearchIndexTaskService.SearchIndexTask> getTask(@PathVariable String taskId) {
        adminPermissionService.requireAdmin(UserContext.require());
        return Result.ok(taskService.getTask(taskId));
    }

    @GetMapping("/tasks")
    public Result<List<SearchIndexTaskService.SearchIndexTask>> listTasks(@RequestParam(defaultValue = "10") int limit) {
        adminPermissionService.requireAdmin(UserContext.require());
        return Result.ok(taskService.listRecentTasks(limit));
    }

    public record RebuildRequest(@Size(max = 500) String remark,
                                 @Size(max = 32) String confirmationPhrase) {
    }

    private static String cleanRemark(String remark) {
        if (remark == null || remark.trim().isEmpty()) {
            return null;
        }
        String value = remark.trim();
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
