package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

    private final PostFacade postFacade;

    @PublicApi
    @GetMapping
    public Result<List<TagDTO>> list() {
        return Result.ok(postFacade.listTags());
    }

    @PublicApi
    @GetMapping("/{tagId}/posts")
    public Result<PageResult<PostBriefDTO>> posts(@PathVariable Long tagId,
                                                  @RequestParam(defaultValue = "0") long cursor,
                                                  @RequestParam(defaultValue = "20") int size) {
        return Result.ok(postFacade.getPostsByTag(tagId, cursor, size));
    }
}
