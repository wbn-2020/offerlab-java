package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.api.dto.ContentSeriesAddPostCmd;
import com.offerlab.community.post.api.dto.ContentSeriesCreateCmd;
import com.offerlab.community.post.api.dto.ContentSeriesDTO;
import com.offerlab.community.post.api.dto.ContentSeriesUpdateCmd;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.application.ContentSeriesService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/content-series")
@RequiredArgsConstructor
public class ContentSeriesController {

    private final ContentSeriesService contentSeriesService;

    @GetMapping
    public Result<List<ContentSeriesDTO>> listMine() {
        return Result.ok(contentSeriesService.listMine(UserContext.require()));
    }

    @PublicApi
    @GetMapping("/{seriesId}")
    @RateLimit(key = "'public:content-series:detail:' + #seriesId + ':' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<ContentSeriesDTO> getPublicDetail(@PathVariable Long seriesId,
                                                    HttpServletRequest request) {
        return Result.ok(contentSeriesService.getPublicDetail(seriesId));
    }

    @PublicApi
    @GetMapping("/{seriesId}/posts")
    @RateLimit(key = "'public:content-series:posts:' + #seriesId + ':' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<PostBriefDTO>> listPublicPosts(@PathVariable Long seriesId,
                                                            @RequestParam(defaultValue = "0") long cursor,
                                                            @RequestParam(defaultValue = "20") @Min(1) @Max(30) int size,
                                                            HttpServletRequest request) {
        return Result.ok(contentSeriesService.listPublicPosts(seriesId, cursor, size));
    }

    @PublicApi
    @GetMapping("/users/{uid}")
    @RateLimit(key = "'public:content-series:user:' + #uid + ':' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<List<ContentSeriesDTO>> listPublicByUser(@PathVariable Long uid,
                                                           @RequestParam(defaultValue = "0") long cursor,
                                                           @RequestParam(defaultValue = "12") @Min(1) @Max(30) int size,
                                                           HttpServletRequest request) {
        return Result.ok(contentSeriesService.listPublicByUser(uid, cursor, size));
    }

    @PostMapping
    @RateLimit(key = "'content-series:create:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<ContentSeriesDTO> create(@Valid @RequestBody ContentSeriesCreateCmd cmd) {
        Long uid = UserContext.require();
        return Result.ok(contentSeriesService.create(cmd, uid));
    }

    @PutMapping("/{seriesId}")
    @RateLimit(key = "'content-series:update:' + #uid", rate = 40, per = 300, failOpen = false)
    public Result<ContentSeriesDTO> update(@PathVariable Long seriesId,
                                           @Valid @RequestBody ContentSeriesUpdateCmd cmd) {
        Long uid = UserContext.require();
        return Result.ok(contentSeriesService.update(seriesId, cmd, uid));
    }

    @PostMapping("/{seriesId}/posts")
    @RateLimit(key = "'content-series:add-post:' + #uid", rate = 60, per = 300, failOpen = false)
    public Result<ContentSeriesDTO> addPost(@PathVariable Long seriesId,
                                            @Valid @RequestBody ContentSeriesAddPostCmd cmd) {
        Long uid = UserContext.require();
        return Result.ok(contentSeriesService.addPost(seriesId, cmd, uid));
    }

    @DeleteMapping("/{seriesId}/posts/{postId}")
    @RateLimit(key = "'content-series:remove-post:' + #uid", rate = 60, per = 300, failOpen = false)
    public Result<ContentSeriesDTO> removePost(@PathVariable Long seriesId,
                                               @PathVariable Long postId) {
        Long uid = UserContext.require();
        return Result.ok(contentSeriesService.removePost(seriesId, postId, uid));
    }
}
