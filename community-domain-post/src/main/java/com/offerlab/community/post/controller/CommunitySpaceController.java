package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.api.dto.CommunitySpaceDTO;
import com.offerlab.community.post.application.CommunitySpaceQueryService;
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
@RequestMapping("/api/v1/community-spaces")
@RequiredArgsConstructor
@Validated
public class CommunitySpaceController {

    private final CommunitySpaceQueryService queryService;

    @PublicApi
    @GetMapping("/topics/{slug}")
    @RateLimit(key = "'public:community-space:topic:' + #slug + ':' + #request.remoteAddr",
            rate = 120, per = 60, failOpen = false)
    public Result<CommunitySpaceDTO> topic(@PathVariable @Size(min = 1, max = 64) String slug,
                                           @RequestParam(defaultValue = "0") long cursor,
                                           @RequestParam(defaultValue = "10") @Min(1) @Max(20) int size,
                                           HttpServletRequest request) {
        return Result.ok(queryService.getTopicSpace(slug, UserContext.get(), cursor, size));
    }

    @PublicApi
    @GetMapping("/collections/{seriesId}")
    @RateLimit(key = "'public:community-space:collection:' + #seriesId + ':' + #request.remoteAddr",
            rate = 120, per = 60, failOpen = false)
    public Result<CommunitySpaceDTO> collection(@PathVariable @Positive Long seriesId,
                                                @RequestParam(defaultValue = "0") long cursor,
                                                @RequestParam(defaultValue = "10") @Min(1) @Max(20) int size,
                                                HttpServletRequest request) {
        return Result.ok(queryService.getSeriesSpace(seriesId, UserContext.get(), cursor, size));
    }

    @PublicApi
    @GetMapping("/series/{seriesId}")
    @RateLimit(key = "'public:community-space:series:' + #seriesId + ':' + #request.remoteAddr",
            rate = 120, per = 60, failOpen = false)
    public Result<CommunitySpaceDTO> collaborationSeries(@PathVariable @Positive Long seriesId,
                                                         @RequestParam(defaultValue = "0") long cursor,
                                                         @RequestParam(defaultValue = "10") @Min(1) @Max(20) int size,
                                                         HttpServletRequest request) {
        return Result.ok(queryService.getCollaborationSeriesSpace(
                seriesId, UserContext.get(), cursor, size));
    }
}
