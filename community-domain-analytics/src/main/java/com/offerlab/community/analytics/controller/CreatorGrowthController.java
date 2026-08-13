package com.offerlab.community.analytics.controller;

import com.offerlab.community.analytics.api.dto.CreatorCurationFeedbackDTO;
import com.offerlab.community.analytics.api.dto.CreatorContentImprovementSignalsDTO;
import com.offerlab.community.analytics.api.dto.CreatorGrowthWorkspaceDTO;
import com.offerlab.community.analytics.api.dto.CreatorRepresentativePostCmd;
import com.offerlab.community.analytics.application.CreatorCurationFeedbackService;
import com.offerlab.community.analytics.application.CreatorContentImprovementService;
import com.offerlab.community.analytics.application.CreatorGrowthService;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/creator-growth")
@RequiredArgsConstructor
public class CreatorGrowthController {

    private final CreatorGrowthService creatorGrowthService;
    private final CreatorCurationFeedbackService creatorCurationFeedbackService;
    private final CreatorContentImprovementService creatorContentImprovementService;

    @GetMapping("/workspace")
    public Result<CreatorGrowthWorkspaceDTO> workspace() {
        return Result.ok(creatorGrowthService.workspace(UserContext.require()));
    }

    @GetMapping("/feedback-summary")
    public Result<CreatorGrowthWorkspaceDTO.CreatorFeedbackSummaryDTO> feedbackSummary() {
        return Result.ok(creatorGrowthService.feedbackSummary(UserContext.require()));
    }

    @GetMapping("/curation-feedback")
    public Result<CreatorCurationFeedbackDTO.CreatorCurationFeedbackSummaryDTO> curationFeedback() {
        return Result.ok(creatorCurationFeedbackService.summary(UserContext.require()));
    }

    @GetMapping("/content-improvement-signals")
    public Result<CreatorContentImprovementSignalsDTO> contentImprovementSignals(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "5") int size) {
        return Result.ok(creatorContentImprovementService.list(UserContext.require(), cursor, size));
    }

    @GetMapping("/topic-ideas")
    public Result<List<CreatorGrowthWorkspaceDTO.CreatorTopicIdeaDTO>> topicIdeas() {
        return Result.ok(creatorGrowthService.topicIdeas(UserContext.require()));
    }

    @GetMapping("/representative-posts")
    public Result<List<CreatorGrowthWorkspaceDTO.RepresentativePostDTO>> representativePosts() {
        return Result.ok(creatorGrowthService.representativePosts(UserContext.require()));
    }

    @PutMapping("/representative-posts")
    public Result<List<CreatorGrowthWorkspaceDTO.RepresentativePostDTO>> updateRepresentativePosts(
            @Valid @RequestBody CreatorRepresentativePostCmd cmd) {
        return Result.ok(creatorGrowthService.updateRepresentativePosts(UserContext.require(), cmd));
    }
}
