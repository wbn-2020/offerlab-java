package com.offerlab.community.question.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.question.api.dto.MockInterviewDraftCmd;
import com.offerlab.community.question.api.dto.MockInterviewSessionDTO;
import com.offerlab.community.question.api.dto.MockInterviewStartCmd;
import com.offerlab.community.question.api.dto.MockInterviewStatsDTO;
import com.offerlab.community.question.api.dto.MockInterviewSubmitCmd;
import com.offerlab.community.question.application.MockInterviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mock-interviews")
@RequiredArgsConstructor
@Validated
public class MockInterviewController {
    private final MockInterviewService mockInterviewService;

    @PostMapping
    @RateLimit(key = "'mock-interview:start:' + #uid", rate = 20, per = 3600)
    public Result<MockInterviewSessionDTO> start(@Valid @RequestBody MockInterviewStartCmd cmd) {
        return Result.ok(mockInterviewService.start(UserContext.require(), cmd));
    }

    @GetMapping
    public Result<List<MockInterviewSessionDTO>> recent(@RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return Result.ok(mockInterviewService.recent(UserContext.require(), limit));
    }

    @GetMapping("/stats")
    public Result<MockInterviewStatsDTO> stats() {
        return Result.ok(mockInterviewService.stats(UserContext.require()));
    }

    @GetMapping("/{id}")
    public Result<MockInterviewSessionDTO> detail(@PathVariable Long id) {
        return Result.ok(mockInterviewService.get(UserContext.require(), id));
    }

    @PutMapping("/{id}/draft")
    @RateLimit(key = "'mock-interview:draft:' + #uid", rate = 240, per = 3600)
    public Result<MockInterviewSessionDTO> saveDraft(@PathVariable Long id, @Valid @RequestBody MockInterviewDraftCmd cmd) {
        return Result.ok(mockInterviewService.saveDraft(UserContext.require(), id, cmd));
    }

    @PostMapping("/{id}/submit")
    @RateLimit(key = "'mock-interview:submit:' + #uid", rate = 60, per = 3600)
    public Result<MockInterviewSessionDTO> submit(@PathVariable Long id, @Valid @RequestBody MockInterviewSubmitCmd cmd) {
        return Result.ok(mockInterviewService.submit(UserContext.require(), id, cmd));
    }
}
