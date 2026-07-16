package com.offerlab.community.analytics.controller;

import com.offerlab.community.analytics.api.dto.ChannelHealthDTO;
import com.offerlab.community.analytics.application.ChannelHealthService;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/community-health")
@RequiredArgsConstructor
public class ChannelHealthController {

    private final ChannelHealthService channelHealthService;

    @GetMapping("/channels")
    @RateLimit(key = "'channel-health:list:' + #uid", rate = 60, per = 60, failOpen = false)
    public Result<List<ChannelHealthDTO>> channels(@RequestParam(required = false) Integer domain) {
        return Result.ok(channelHealthService.list(domain, UserContext.require()));
    }
}
