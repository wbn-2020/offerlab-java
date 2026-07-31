package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.api.dto.DiscoveryMapDTO;
import com.offerlab.community.post.application.DiscoveryMapService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/discovery")
@RequiredArgsConstructor
public class DiscoveryMapController {

    private final DiscoveryMapService discoveryMapService;

    @PublicApi
    @GetMapping("/map")
    @RateLimit(key = "'public:discovery:map:' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<DiscoveryMapDTO> map(@RequestParam(defaultValue = "5") int featuredLimit,
                                       @RequestParam(defaultValue = "8") int topicLimit,
                                       HttpServletRequest request) {
        return Result.ok(discoveryMapService.getPublicMap(featuredLimit, topicLimit));
    }
}
