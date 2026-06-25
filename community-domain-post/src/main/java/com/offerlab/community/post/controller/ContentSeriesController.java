package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.post.api.dto.ContentSeriesAddPostCmd;
import com.offerlab.community.post.api.dto.ContentSeriesCreateCmd;
import com.offerlab.community.post.api.dto.ContentSeriesDTO;
import com.offerlab.community.post.api.dto.ContentSeriesUpdateCmd;
import com.offerlab.community.post.application.ContentSeriesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
