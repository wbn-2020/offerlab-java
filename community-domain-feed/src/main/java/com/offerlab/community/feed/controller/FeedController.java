package com.offerlab.community.feed.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.feed.api.FeedFacade;
import com.offerlab.community.feed.api.dto.FeedFeedbackCmd;
import com.offerlab.community.feed.api.dto.FeedItemVO;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feeds")
@RequiredArgsConstructor
public class FeedController {

    private final FeedFacade feedFacade;

    @GetMapping("/following")
    public Result<PageResult<FeedItemVO>> following(@RequestParam(required = false) String cursor,
                                                    @RequestParam(defaultValue = "20") int size) {
        Long uid = UserContext.require();
        return Result.ok(feedFacade.getFollowingFeed(uid, cursor, clamp(size)));
    }

    @PublicApi
    @GetMapping("/recommend")
    public Result<PageResult<FeedItemVO>> recommend(@RequestParam(required = false) String cursor,
                                                    @RequestParam(defaultValue = "20") int size) {
        return Result.ok(feedFacade.getRecommendFeed(UserContext.get(), cursor, clamp(size)));
    }

    @PublicApi
    @GetMapping("/latest")
    public Result<PageResult<FeedItemVO>> latest(@RequestParam(required = false) String cursor,
                                                 @RequestParam(defaultValue = "20") int size) {
        return Result.ok(feedFacade.getLatestFeed(UserContext.get(), cursor, clamp(size)));
    }

    @PublicApi
    @GetMapping("/hot")
    public Result<PageResult<FeedItemVO>> hot(@RequestParam(required = false) String cursor,
                                              @RequestParam(defaultValue = "20") int size) {
        return Result.ok(feedFacade.getHotFeed(UserContext.get(), cursor, clamp(size)));
    }

    @PostMapping("/feedback")
    public Result<Void> feedback(@RequestBody FeedFeedbackCmd cmd) {
        Long uid = UserContext.require();
        feedFacade.recordFeedback(uid,
                cmd == null ? null : cmd.getPostId(),
                cmd == null ? null : cmd.getAction(),
                cmd == null ? null : cmd.getReason());
        return Result.ok();
    }

    private int clamp(int size) {
        if (size <= 0) return 20;
        return Math.min(size, 50);
    }
}
