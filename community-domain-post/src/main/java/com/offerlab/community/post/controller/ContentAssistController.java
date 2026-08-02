package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.api.dto.ContentAssistQualityScoreCmd;
import com.offerlab.community.post.api.dto.ContentAssistQualityScoreDTO;
import com.offerlab.community.post.api.dto.ContentAssistTagTopicSuggestionsCmd;
import com.offerlab.community.post.api.dto.ContentAssistTagTopicSuggestionsDTO;
import com.offerlab.community.post.api.dto.ContentAssistWritingCmd;
import com.offerlab.community.post.api.dto.ContentAssistWritingDTO;
import com.offerlab.community.post.api.dto.ContentAssistCapabilityDTO;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedCmd;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedRequestSummaryDTO;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedResultDTO;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedStatusDTO;
import com.offerlab.community.post.application.ContentAssistEnhancedService;
import com.offerlab.community.post.application.ContentAssistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api/v1/content-assist")
@RequiredArgsConstructor
public class ContentAssistController {

    private final ContentAssistService contentAssistService;
    private final ContentAssistEnhancedService contentAssistEnhancedService;

    @GetMapping("/capability")
    public Result<ContentAssistCapabilityDTO> capability() {
        return Result.ok(contentAssistEnhancedService.capability(UserContext.require()));
    }

    @PostMapping("/enhanced")
    @RateLimit(key = "'content-assist:enhanced:' + #uid", rate = 12, per = 3600, failOpen = false)
    public ResponseEntity<Result<ContentAssistEnhancedResultDTO>> enhanced(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ContentAssistEnhancedCmd cmd) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Result.ok(contentAssistEnhancedService.enhance(UserContext.require(), idempotencyKey, cmd)));
    }

    @GetMapping("/enhanced/status")
    public ResponseEntity<Result<ContentAssistEnhancedStatusDTO>> enhancedStatus(@RequestParam String idempotencyKey) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Result.ok(contentAssistEnhancedService.status(UserContext.require(), idempotencyKey)));
    }

    @GetMapping("/enhanced/recent")
    public ResponseEntity<Result<List<ContentAssistEnhancedRequestSummaryDTO>>> enhancedRecent(
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Result.ok(contentAssistEnhancedService.recent(UserContext.require(), limit)));
    }

    @GetMapping("/enhanced/requests/{requestId}/status")
    public ResponseEntity<Result<ContentAssistEnhancedStatusDTO>> enhancedStatusByRequestId(
            @org.springframework.web.bind.annotation.PathVariable Long requestId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Result.ok(contentAssistEnhancedService.statusByRequestId(UserContext.require(), requestId)));
    }

    @PostMapping("/writing")
    @RateLimit(key = "'content-assist:writing:' + #uid", rate = 120, per = 3600)
    public Result<ContentAssistWritingDTO> writing(@Valid @RequestBody ContentAssistWritingCmd cmd) {
        return Result.ok(contentAssistService.assistWriting(UserContext.require(), cmd));
    }

    @PostMapping("/quality-score")
    @RateLimit(key = "'content-assist:quality-score:' + #uid", rate = 120, per = 3600)
    public Result<ContentAssistQualityScoreDTO> qualityScore(@Valid @RequestBody ContentAssistQualityScoreCmd cmd) {
        return Result.ok(contentAssistService.scoreQuality(UserContext.require(), cmd));
    }

    @PostMapping("/tag-topic-suggestions")
    @RateLimit(key = "'content-assist:tag-topic:' + #uid", rate = 180, per = 3600)
    public Result<ContentAssistTagTopicSuggestionsDTO> tagTopicSuggestions(
            @Valid @RequestBody ContentAssistTagTopicSuggestionsCmd cmd) {
        return Result.ok(contentAssistService.suggestTagsAndTopics(UserContext.require(), cmd));
    }
}
