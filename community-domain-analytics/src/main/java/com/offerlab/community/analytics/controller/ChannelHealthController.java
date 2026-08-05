package com.offerlab.community.analytics.controller;

import com.offerlab.community.analytics.api.dto.ChannelHealthDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewCandidateDispositionCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewCandidateDispositionDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewCandidatePageDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchCreateCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchDetailDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchPageDTO;
import com.offerlab.community.analytics.application.ChannelHealthService;
import com.offerlab.community.analytics.application.ChannelQualityReviewBatchService;
import com.offerlab.community.analytics.application.ChannelQualityReviewCandidateDispositionService;
import com.offerlab.community.analytics.application.ChannelQualityReviewCandidateService;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/community-health")
@RequiredArgsConstructor
public class ChannelHealthController {

    private final ChannelHealthService channelHealthService;
    private final ChannelQualityReviewCandidateService channelQualityReviewCandidateService;
    private final ChannelQualityReviewCandidateDispositionService candidateDispositionService;
    private final ChannelQualityReviewBatchService batchService;

    @GetMapping("/channels")
    @RateLimit(key = "'channel-health:list:' + #uid", rate = 60, per = 60, failOpen = false)
    public Result<List<ChannelHealthDTO>> channels(@RequestParam(required = false) Integer domain) {
        return Result.ok(channelHealthService.list(domain, UserContext.require()));
    }

    @GetMapping("/quality-review-candidates")
    @RateLimit(key = "'channel-health:quality-review-candidates:' + #uid", rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityReviewCandidatePageDTO> qualityReviewCandidates(
            @RequestParam Integer domain,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return Result.ok(channelQualityReviewCandidateService.list(domain, cursor, size, UserContext.require()));
    }

    @PostMapping("/quality-review-candidates/disposition")
    @RateLimit(key = "'channel-health:quality-review-candidate-disposition:' + #uid",
            rate = 20, per = 300, failOpen = false)
    public Result<ChannelQualityReviewCandidateDispositionDTO> disposeQualityReviewCandidate(
            @Valid @RequestBody ChannelQualityReviewCandidateDispositionCmd cmd) {
        return Result.ok(candidateDispositionService.dispose(cmd, UserContext.require()));
    }

    @PostMapping("/quality-review-batches")
    @RateLimit(key = "'channel-health:quality-review-batch-create:' + #uid",
            rate = 5, per = 300, failOpen = false)
    public Result<ChannelQualityReviewBatchDetailDTO> createQualityReviewBatch(
            @Valid @RequestBody ChannelQualityReviewBatchCreateCmd cmd) {
        return Result.ok(batchService.create(cmd, UserContext.require()));
    }

    @GetMapping("/quality-review-batches")
    @RateLimit(key = "'channel-health:quality-review-batch-list:' + #uid",
            rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityReviewBatchPageDTO> qualityReviewBatches(
            @RequestParam Integer domain,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return Result.ok(batchService.list(domain, cursor, size, UserContext.require()));
    }

    @GetMapping("/quality-review-batches/{batchId}")
    @RateLimit(key = "'channel-health:quality-review-batch-detail:' + #uid",
            rate = 60, per = 60, failOpen = false)
    public Result<ChannelQualityReviewBatchDetailDTO> qualityReviewBatch(
            @PathVariable Long batchId) {
        return Result.ok(batchService.get(batchId, UserContext.require()));
    }
}
