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
import com.offerlab.community.post.application.ContentAssistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content-assist")
@RequiredArgsConstructor
public class ContentAssistController {

    private final ContentAssistService contentAssistService;

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
