package com.offerlab.community.post.collaboration.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.collaboration.api.CollaborationActionItemDTO;
import com.offerlab.community.post.collaboration.api.CollaborationActionSummaryDTO;
import com.offerlab.community.post.collaboration.api.NeedDeliveryCandidateDTO;
import com.offerlab.community.post.collaboration.api.NeedDiscoveryItemDTO;
import com.offerlab.community.post.collaboration.application.CollaborationActionQueryService;
import com.offerlab.community.post.collaboration.application.NeedDeliveryCandidateService;
import com.offerlab.community.post.collaboration.application.NeedDiscoveryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/collaboration")
@RequiredArgsConstructor
@Validated
public class CollaborationV7ReadController {

    private final CollaborationActionQueryService actionQueryService;
    private final NeedDeliveryCandidateService deliveryCandidateService;
    private final NeedDiscoveryService discoveryService;

    @GetMapping("/actions/summary")
    @RateLimit(key = "'collaboration:actions:summary:' + #uid",
            rate = 120, per = 60, failOpen = false)
    public Result<CollaborationActionSummaryDTO> actionSummary() {
        return Result.ok(actionQueryService.summary(UserContext.require()));
    }

    @GetMapping("/actions")
    @RateLimit(key = "'collaboration:actions:' + #uid",
            rate = 120, per = 60, failOpen = false)
    public Result<PageResult<CollaborationActionItemDTO>> actions(
            @RequestParam(required = false) @Size(max = 32) String actionType,
            @RequestParam(defaultValue = "0") String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(actionQueryService.list(
                UserContext.require(), actionType, cursor, size));
    }

    @GetMapping("/needs/{needId}/delivery-candidates")
    @RateLimit(key = "'collaboration:need:delivery-candidates:' + #uid",
            rate = 120, per = 60, failOpen = false)
    public Result<PageResult<NeedDeliveryCandidateDTO>> deliveryCandidates(
            @PathVariable @Positive Long needId,
            @RequestParam(required = false) @Size(max = 24) String resolutionType,
            @RequestParam(required = false) @Size(max = 80) String keyword,
            @RequestParam(defaultValue = "0") String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(deliveryCandidateService.list(
                needId, UserContext.require(), resolutionType, keyword, cursor, size));
    }

    @PublicApi
    @GetMapping("/needs/discovery")
    @RateLimit(key = "'public:collaboration:needs:discovery:' + #request.remoteAddr",
            rate = 120, per = 60, failOpen = false)
    public Result<PageResult<NeedDiscoveryItemDTO>> discovery(
            @RequestParam(required = false) @Size(max = 120) String keyword,
            @RequestParam(required = false) @Min(1) @Max(5) Integer domain,
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(required = false) @Size(max = 32) String contentFormat,
            @RequestParam(required = false) @Size(max = 32) String sourceType,
            @RequestParam(required = false) @Size(max = 24) String sort,
            @RequestParam(defaultValue = "0") String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            HttpServletRequest request) {
        return Result.ok(discoveryService.list(
                keyword, domain, status, contentFormat, sourceType, sort,
                cursor, size, UserContext.get()));
    }
}
