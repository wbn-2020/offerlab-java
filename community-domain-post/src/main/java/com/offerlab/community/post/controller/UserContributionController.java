package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.api.dto.UserContributionDTO;
import com.offerlab.community.post.application.UserContributionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserContributionController {

    private final UserContributionService contributionService;

    @PublicApi
    @GetMapping("/{uid}/contribution")
    @RateLimit(key = "'public:user:contribution:' + #uid + ':' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<UserContributionDTO> getUserContribution(@PathVariable Long uid,
                                                           HttpServletRequest request) {
        return Result.ok(contributionService.getContribution(uid, UserContext.get()));
    }

    @GetMapping("/me/contribution")
    public Result<UserContributionDTO> getMyContribution() {
        Long uid = UserContext.require();
        return Result.ok(contributionService.getContribution(uid, uid));
    }
}
