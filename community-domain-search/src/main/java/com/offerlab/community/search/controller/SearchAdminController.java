package com.offerlab.community.search.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.common.utils.RiskConfirmation;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/search/admin")
@RequiredArgsConstructor
public class SearchAdminController {

    private final SearchIndexTaskService taskService;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;

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
