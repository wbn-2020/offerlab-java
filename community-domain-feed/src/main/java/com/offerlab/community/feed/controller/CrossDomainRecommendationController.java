package com.offerlab.community.feed.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.feed.api.FeedFacade;
import com.offerlab.community.feed.api.dto.CrossDomainRecommendationVO;
import com.offerlab.community.infra.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class CrossDomainRecommendationController {

    private final FeedFacade feedFacade;

    @GetMapping("/cross-domain")
    public Result<PageResult<CrossDomainRecommendationVO>> crossDomain(@RequestParam(required = false) String cursor,
                                                                      @RequestParam(defaultValue = "20") int size) {
        return Result.ok(feedFacade.getCrossDomainRecommendations(UserContext.require(), cursor, clamp(size)));
    }

    private int clamp(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 50);
    }
}
