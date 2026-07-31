package com.offerlab.community.interaction.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.interaction.api.dto.AcceptedAnswerCmd;
import com.offerlab.community.interaction.api.dto.ContentSuggestionDTO;
import com.offerlab.community.interaction.api.dto.ContentSuggestionDecisionCmd;
import com.offerlab.community.interaction.api.dto.ContentSuggestionSettingsCmd;
import com.offerlab.community.interaction.api.dto.ContentSuggestionSubmitCmd;
import com.offerlab.community.interaction.api.dto.FreshnessUpdateCmd;
import com.offerlab.community.interaction.api.dto.QuestionStateUpdateCmd;
import com.offerlab.community.interaction.api.dto.TrustedContentDTO;
import com.offerlab.community.interaction.api.dto.UsefulFeedbackCmd;
import com.offerlab.community.interaction.api.enums.ContentSuggestionStatus;
import com.offerlab.community.interaction.application.TrustedContentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequiredArgsConstructor
@RequestMapping("/api/v1/posts")
@Validated
public class TrustedContentController {

    private final TrustedContentService trustedContentService;

    @PublicApi
    @GetMapping("/{postId}/trusted-content")
    @RateLimit(key = "'public:trusted-content:' + #postId + ':' + #request.remoteAddr",
            rate = 240, per = 60, failOpen = false)
    public Result<TrustedContentDTO> getTrustedContent(
            @PathVariable @Positive Long postId,
            HttpServletRequest request) {
        return Result.ok(trustedContentService.getTrustedContent(postId, UserContext.get()));
    }

    @PutMapping("/{postId}/useful-feedback")
    @RateLimit(key = "'trusted-content:useful:' + #postId + ':' + #uid", rate = 60, per = 60)
    public Result<TrustedContentDTO> saveUsefulFeedback(
            @PathVariable @Positive Long postId,
            @Valid @RequestBody UsefulFeedbackCmd cmd) {
        return Result.ok(trustedContentService.saveUsefulFeedback(
                postId, UserContext.require(), cmd));
    }

    @DeleteMapping("/{postId}/useful-feedback")
    @RateLimit(key = "'trusted-content:useful-clear:' + #postId + ':' + #uid", rate = 60, per = 60)
    public Result<TrustedContentDTO> clearUsefulFeedback(
            @PathVariable @Positive Long postId) {
        return Result.ok(trustedContentService.clearUsefulFeedback(
                postId, UserContext.require()));
    }

    @PostMapping("/{postId}/content-suggestions")
    @RateLimit(key = "'trusted-content:suggestion-submit:' + #postId + ':' + #uid",
            rate = 20, per = 3600)
    public Result<ContentSuggestionDTO> submitSuggestion(
            @PathVariable @Positive Long postId,
            @Valid @RequestBody ContentSuggestionSubmitCmd cmd) {
        return Result.ok(trustedContentService.submitSuggestion(
                postId, UserContext.require(), cmd));
    }

    @GetMapping("/{postId}/content-suggestions/mine")
    @RateLimit(key = "'trusted-content:suggestion-mine:' + #postId + ':' + #uid",
            rate = 120, per = 60)
    public Result<List<ContentSuggestionDTO>> listMySuggestions(
            @PathVariable @Positive Long postId,
            @RequestParam(required = false) ContentSuggestionStatus status,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return Result.ok(trustedContentService.listMySuggestions(
                postId, UserContext.require(), status, limit));
    }

    @GetMapping("/{postId}/content-suggestions/author")
    @RateLimit(key = "'trusted-content:suggestion-author:' + #postId + ':' + #uid",
            rate = 120, per = 60)
    public Result<List<ContentSuggestionDTO>> listAuthorSuggestions(
            @PathVariable @Positive Long postId,
            @RequestParam(required = false) ContentSuggestionStatus status,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return Result.ok(trustedContentService.listAuthorSuggestions(
                postId, UserContext.require(), status, limit));
    }

    @PutMapping("/{postId}/content-suggestions/settings")
    @RateLimit(key = "'trusted-content:suggestion-settings:' + #postId + ':' + #uid",
            rate = 60, per = 60)
    public Result<TrustedContentDTO> updateSuggestionSettings(
            @PathVariable @Positive Long postId,
            @Valid @RequestBody ContentSuggestionSettingsCmd cmd) {
        return Result.ok(trustedContentService.updateSuggestionSettings(
                postId, UserContext.require(), cmd));
    }

    @PutMapping("/{postId}/question-state")
    @RateLimit(key = "'trusted-content:question-state:' + #postId + ':' + #uid",
            rate = 60, per = 60)
    public Result<TrustedContentDTO> updateQuestionState(
            @PathVariable @Positive Long postId,
            @Valid @RequestBody QuestionStateUpdateCmd cmd) {
        return Result.ok(trustedContentService.updateQuestionState(
                postId, UserContext.require(), cmd));
    }

    @PutMapping("/{postId}/accepted-answer")
    @RateLimit(key = "'trusted-content:accept-answer:' + #postId + ':' + #uid",
            rate = 60, per = 60)
    public Result<TrustedContentDTO> acceptAnswer(
            @PathVariable @Positive Long postId,
            @Valid @RequestBody AcceptedAnswerCmd cmd) {
        return Result.ok(trustedContentService.acceptAnswer(
                postId, UserContext.require(), cmd));
    }

    @DeleteMapping("/{postId}/accepted-answer")
    @RateLimit(key = "'trusted-content:clear-answer:' + #postId + ':' + #uid",
            rate = 60, per = 60)
    public Result<TrustedContentDTO> clearAcceptedAnswer(
            @PathVariable @Positive Long postId) {
        return Result.ok(trustedContentService.clearAcceptedAnswer(
                postId, UserContext.require()));
    }

    @PutMapping("/{postId}/freshness")
    @RateLimit(key = "'trusted-content:freshness:' + #postId + ':' + #uid",
            rate = 60, per = 60)
    public Result<TrustedContentDTO> updateFreshness(
            @PathVariable @Positive Long postId,
            @Valid @RequestBody FreshnessUpdateCmd cmd) {
        return Result.ok(trustedContentService.updateFreshness(
                postId, UserContext.require(), cmd));
    }
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Validated
class ContentSuggestionDecisionController {

    private final TrustedContentService trustedContentService;

    @GetMapping("/content-suggestions/{suggestionId}")
    @RateLimit(key = "'trusted-content:suggestion-read:' + #suggestionId + ':' + #uid",
            rate = 120, per = 60)
    public Result<ContentSuggestionDTO> getSuggestion(
            @PathVariable @Positive Long suggestionId) {
        return Result.ok(trustedContentService.getSuggestion(
                suggestionId, UserContext.require()));
    }

    @PutMapping("/content-suggestions/{suggestionId}/decision")
    @RateLimit(key = "'trusted-content:suggestion-decision:' + #suggestionId + ':' + #uid",
            rate = 60, per = 60)
    public Result<ContentSuggestionDTO> decideSuggestion(
            @PathVariable @Positive Long suggestionId,
            @Valid @RequestBody ContentSuggestionDecisionCmd cmd) {
        return Result.ok(trustedContentService.decideSuggestion(
                suggestionId, UserContext.require(), cmd));
    }
}
