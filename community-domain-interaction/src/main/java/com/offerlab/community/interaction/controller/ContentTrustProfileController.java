package com.offerlab.community.interaction.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.interaction.api.dto.TrustProfileDTO;
import com.offerlab.community.interaction.api.dto.TrustProfileUpdateCmd;
import com.offerlab.community.interaction.application.ContentTrustProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/posts")
@Validated
public class ContentTrustProfileController {

    private final ContentTrustProfileService trustProfileService;

    @PublicApi
    @GetMapping("/{postId}/trust-profile")
    @RateLimit(key = "'public:trust-profile:' + #postId + ':' + #request.remoteAddr",
            rate = 180, per = 60, failOpen = false)
    public Result<TrustProfileDTO> getProfile(@PathVariable @Positive Long postId,
                                               HttpServletRequest request) {
        return Result.ok(trustProfileService.getProfile(postId, UserContext.get()));
    }

    @PutMapping("/{postId}/trust-profile")
    @RateLimit(key = "'trust-profile:write:' + #postId + ':' + #uid", rate = 30, per = 300)
    public Result<TrustProfileDTO> updateProfile(@PathVariable @Positive Long postId,
                                                  @Valid @RequestBody TrustProfileUpdateCmd cmd) {
        return Result.ok(trustProfileService.updateProfile(postId, UserContext.require(), cmd));
    }
}
