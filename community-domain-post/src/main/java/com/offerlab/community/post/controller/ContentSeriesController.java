package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.post.api.dto.ContentSeriesAddPostCmd;
import com.offerlab.community.post.api.dto.ContentSeriesCreateCmd;
import com.offerlab.community.post.api.dto.ContentSeriesDTO;
import com.offerlab.community.post.api.dto.ContentSeriesUpdateCmd;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.application.ContentSeriesService;
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
    public Result<ContentSeriesDTO> getPublicDetail(@PathVariable Long seriesId) {
        return Result.ok(contentSeriesService.getPublicDetail(seriesId));
    }

    @PublicApi
    @GetMapping("/{seriesId}/posts")
    public Result<PageResult<PostBriefDTO>> listPublicPosts(@PathVariable Long seriesId,
                                                            @RequestParam(defaultValue = "0") long cursor,
                                                            @RequestParam(defaultValue = "20") @Min(1) @Max(30) int size) {
        return Result.ok(contentSeriesService.listPublicPosts(seriesId, cursor, size));
    }

    @PublicApi
    @GetMapping("/users/{uid}")
    public Result<List<ContentSeriesDTO>> listPublicByUser(@PathVariable Long uid,
                                                           @RequestParam(defaultValue = "0") long cursor,
                                                           @RequestParam(defaultValue = "12") @Min(1) @Max(30) int size) {
        return Result.ok(contentSeriesService.listPublicByUser(uid, cursor, size));
    }

    @PostMapping
    public Result<ContentSeriesDTO> create(@Valid @RequestBody ContentSeriesCreateCmd cmd) {
        return Result.ok(contentSeriesService.create(cmd, UserContext.require()));
    }

    @PutMapping("/{seriesId}")
    public Result<ContentSeriesDTO> update(@PathVariable Long seriesId,
                                           @Valid @RequestBody ContentSeriesUpdateCmd cmd) {
        return Result.ok(contentSeriesService.update(seriesId, cmd, UserContext.require()));
    }

    @PostMapping("/{seriesId}/posts")
    public Result<ContentSeriesDTO> addPost(@PathVariable Long seriesId,
                                            @Valid @RequestBody ContentSeriesAddPostCmd cmd) {
        return Result.ok(contentSeriesService.addPost(seriesId, cmd, UserContext.require()));
    }

    @DeleteMapping("/{seriesId}/posts/{postId}")
    public Result<ContentSeriesDTO> removePost(@PathVariable Long seriesId,
                                               @PathVariable Long postId) {
        return Result.ok(contentSeriesService.removePost(seriesId, postId, UserContext.require()));
    }
}
