package com.offerlab.community.search.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.search.application.SearchIndexTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search/admin")
@RequiredArgsConstructor
public class SearchAdminController {

    private final SearchIndexTaskService taskService;
    private final AdminPermissionService adminPermissionService;

    @PostMapping("/rebuild")
    public Result<SearchIndexTaskService.SearchIndexTask> rebuildPostIndex() {
        Long uid = UserContext.require();
        adminPermissionService.requireAdmin(uid);
        return Result.ok(taskService.submitRebuildTask(uid));
    }

    @GetMapping("/tasks/{taskId}")
    public Result<SearchIndexTaskService.SearchIndexTask> getTask(@PathVariable String taskId) {
        adminPermissionService.requireAdmin(UserContext.require());
        return Result.ok(taskService.getTask(taskId));
    }
}
