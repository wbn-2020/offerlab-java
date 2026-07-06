package com.offerlab.community.question.controller;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.question.api.dto.InterviewMaterialPackDTO;
import com.offerlab.community.question.api.dto.InterviewMaterialUpdateCmd;
import com.offerlab.community.question.api.dto.UserKnowledgeDTO;
import com.offerlab.community.question.application.InterviewMaterialService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class InterviewMaterialController {
    private final InterviewMaterialService materialService;

    @PostMapping("/posts/{postId}/interview-materials/generate")
    @RateLimit(key = "'interview-material:generate:' + #uid + ':' + #postId", rate = 20, per = 3600)
    public Result<InterviewMaterialPackDTO> generate(@PathVariable Long postId) {
        return disabledLegacyTrainingFeature();
    }

    @GetMapping("/posts/{postId}/interview-materials")
    public Result<InterviewMaterialPackDTO> getForPost(@PathVariable Long postId) {
        return disabledLegacyTrainingFeature();
    }

    @PutMapping("/interview-materials/{id}")
    @RateLimit(key = "'interview-material:update:' + #uid + ':' + #id", rate = 60, per = 3600)
    public Result<InterviewMaterialPackDTO> update(@PathVariable Long id,
                                                   @Valid @RequestBody InterviewMaterialUpdateCmd cmd) {
        return disabledLegacyTrainingFeature();
    }

    @PostMapping("/interview-materials/{id}/save-to-prep")
    @RateLimit(key = "'interview-material:save:' + #uid + ':' + #id", rate = 60, per = 3600)
    public Result<InterviewMaterialPackDTO> saveToPrep(@PathVariable Long id) {
        return disabledLegacyTrainingFeature();
    }

    @GetMapping("/me/knowledge")
    public Result<UserKnowledgeDTO> myKnowledge(@RequestParam(required = false) @Size(max = 128) String company,
                                                @RequestParam(required = false) @Size(max = 128) String position,
                                                @RequestParam(required = false) @Size(max = 64) String techStack,
                                                @RequestParam(required = false) @Size(max = 64) String interviewRound,
                                                @RequestParam(required = false) Integer postType,
                                                @RequestParam(defaultValue = "false") boolean savedOnly,
                                                @RequestParam(defaultValue = "12") @Min(1) @Max(50) int limit) {
        return disabledLegacyTrainingFeature();
    }

    private static <T> T disabledLegacyTrainingFeature() {
        throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
