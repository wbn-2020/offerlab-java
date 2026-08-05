package com.offerlab.community.feed.controller;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.feed.api.FeedFacade;
import com.offerlab.community.feed.api.dto.FeedAuthorControlCmd;
import com.offerlab.community.feed.api.dto.ChannelHotBoardVO;
import com.offerlab.community.feed.api.dto.FeedControlVO;
import com.offerlab.community.feed.api.dto.FeedFeedbackCmd;
import com.offerlab.community.feed.api.dto.FeedFeedbackPreferenceVO;
import com.offerlab.community.feed.api.dto.FeedItemVO;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.domain.model.Post;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @RequestParam(required = false) Integer domain) {
        Long uid = UserContext.require();
        return Result.ok(feedFacade.getFollowingFeed(uid, cursor, clamp(size), requireOptionalDomain(domain)));
    }

    @PublicApi
    @GetMapping("/recommend")
    @RateLimit(key = "'public:feed:recommend:' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<FeedItemVO>> recommend(@RequestParam(required = false) String cursor,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @RequestParam(required = false) Integer domain,
                                                    HttpServletRequest request) {
        return Result.ok(feedFacade.getRecommendFeed(UserContext.get(), cursor, clamp(size), requireOptionalDomain(domain)));
    }

    @PublicApi
    @GetMapping("/latest")
    @RateLimit(key = "'public:feed:latest:' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<FeedItemVO>> latest(@RequestParam(required = false) String cursor,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(required = false) Integer domain,
                                                 HttpServletRequest request) {
        return Result.ok(feedFacade.getLatestFeed(UserContext.get(), cursor, clamp(size), requireOptionalDomain(domain)));
    }

    @PublicApi
    @GetMapping("/hot")
    @RateLimit(key = "'public:feed:hot:' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<FeedItemVO>> hot(@RequestParam(required = false) String cursor,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) Integer domain,
                                              HttpServletRequest request) {
        return Result.ok(feedFacade.getHotFeed(UserContext.get(), cursor, clamp(size), requireOptionalDomain(domain)));
    }

    @PublicApi
    @GetMapping("/channels/{domain}/hot-board")
    @RateLimit(key = "'public:feed:channel-hot-board:' + #domain + ':' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<ChannelHotBoardVO> channelHotBoard(@PathVariable Integer domain,
                                                      @RequestParam(defaultValue = "10") int size,
                                                      HttpServletRequest request) {
        return Result.ok(feedFacade.getChannelHotBoard(UserContext.get(), requireOptionalDomain(domain),
                Math.min(Math.max(size, 1), 20)));
    }

    @PostMapping("/feedback")
    @RateLimit(key = "'feed:feedback:' + #uid", rate = 60, per = 60, failOpen = false)
    public Result<Void> feedback(@Valid @RequestBody FeedFeedbackCmd cmd) {
        Long uid = UserContext.require();
        feedFacade.recordFeedback(uid, cmd.getPostId(), cmd.getAction(), cmd.getReason(), cmd.getReasonCode());
        return Result.ok();
    }

    @PostMapping("/author-controls")
    @RateLimit(key = "'feed:author-controls:' + #uid", rate = 30, per = 60, failOpen = false)
    public Result<FeedControlVO> blockAuthor(@Valid @RequestBody FeedAuthorControlCmd cmd) {
        Long uid = UserContext.require();
        return Result.ok(feedFacade.blockAuthor(uid, cmd.getAuthorUid()));
    }

    @DeleteMapping("/author-controls/{authorUid}")
    @RateLimit(key = "'feed:author-controls:remove:' + #uid", rate = 30, per = 60, failOpen = false)
    public Result<Void> unblockAuthor(@PathVariable Long authorUid) {
        Long uid = UserContext.require();
        feedFacade.unblockAuthor(uid, authorUid);
        return Result.ok();
    }

    @GetMapping("/controls")
    @RateLimit(key = "'feed:controls:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<FeedControlVO>> controls(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        Long uid = UserContext.require();
        return Result.ok(feedFacade.listControls(uid, cursor, clamp(size)));
    }

    @DeleteMapping("/controls/{controlId}")
    @RateLimit(key = "'feed:controls:remove:' + #uid", rate = 60, per = 60, failOpen = false)
    public Result<Void> deleteControl(@PathVariable Long controlId) {
        Long uid = UserContext.require();
        feedFacade.deleteControl(uid, controlId);
        return Result.ok();
    }

    @GetMapping("/feedback/preferences")
    @RateLimit(key = "'feed:feedback:preferences:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<FeedFeedbackPreferenceVO>> feedbackPreferences(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        Long uid = UserContext.require();
        return Result.ok(feedFacade.listFeedbackPreferences(uid, cursor, clamp(size)));
    }

    @GetMapping("/feedback/preferences/{postId}")
    @RateLimit(key = "'feed:feedback:preference:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<FeedFeedbackPreferenceVO> feedbackPreference(@PathVariable Long postId) {
        Long uid = UserContext.require();
        return Result.ok(feedFacade.getFeedbackPreference(uid, postId));
    }

    @DeleteMapping("/feedback/preferences/{postId}")
    @RateLimit(key = "'feed:feedback:restore:' + #uid", rate = 60, per = 60, failOpen = false)
    public Result<Void> restoreFeedbackPreference(@PathVariable Long postId) {
        Long uid = UserContext.require();
        feedFacade.recordFeedback(uid, postId, "RESTORE", null);
        return Result.ok();
    }

    private int clamp(int size) {
        if (size <= 0) return 20;
        return Math.min(size, 50);
    }

    private Integer requireOptionalDomain(Integer domain) {
        if (domain == null) {
            return null;
        }
        if (isValidDomain(domain)) {
            return domain;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private boolean isValidDomain(Integer domain) {
        return domain != null && (domain == Post.DOMAIN_TECH
                || domain == Post.DOMAIN_CAREER
                || domain == Post.DOMAIN_READING
                || domain == Post.DOMAIN_LIFESTYLE
                || domain == Post.DOMAIN_INVESTMENT);
    }
}
