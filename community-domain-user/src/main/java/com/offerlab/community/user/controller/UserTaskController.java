package com.offerlab.community.user.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.user.api.dto.UserTaskOverviewDTO;
import com.offerlab.community.user.application.UserTaskApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class UserTaskController {

    private final UserTaskApplicationService taskService;

    @GetMapping("/onboarding-tasks")
    public Result<UserTaskOverviewDTO> getOnboardingTasks() {
        return Result.ok(taskService.getOnboardingTasks(UserContext.require()));
    }

    @PostMapping("/onboarding-tasks/{taskCode}/complete")
    @RateLimit(key = "'user-task:onboarding:' + #uid", rate = 60, per = 60)
    public Result<Void> completeOnboardingTask(@PathVariable String taskCode) {
        taskService.completeOnboardingTask(UserContext.require(), taskCode);
        return Result.ok();
    }

    @GetMapping("/daily-tasks")
    public Result<UserTaskOverviewDTO> getDailyTasks() {
        return Result.ok(taskService.getDailyTasks(UserContext.require()));
    }

    @PostMapping("/daily-tasks/{taskCode}/complete")
    @RateLimit(key = "'user-task:daily:' + #uid", rate = 60, per = 60)
    public Result<Void> completeDailyTask(@PathVariable String taskCode) {
        taskService.completeDailyTask(UserContext.require(), taskCode);
        return Result.ok();
    }
}
