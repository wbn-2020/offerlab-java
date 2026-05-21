package com.offerlab.community.search.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.search.api.SearchFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@PublicApi
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchFacade facade;

    @GetMapping("/posts")
    public Result<PageResult<PostBriefDTO>> searchPosts(@RequestParam(name = "q", required = false) String keyword,
                                                       @RequestParam(required = false) String company,
                                                       @RequestParam(required = false) String position,
                                                       @RequestParam(required = false) Integer type,
                                                       @RequestParam(required = false) String cursor,
                                                       @RequestParam(defaultValue = "20") int size) {
        return Result.ok(facade.searchPosts(keyword, company, position, type, cursor, size));
    }

    @GetMapping("/suggest")
    public Result<List<String>> suggest(@RequestParam String prefix,
                                        @RequestParam(defaultValue = "10") int size) {
        return Result.ok(facade.suggest(prefix, size));
    }

    @GetMapping("/hot")
    public Result<List<String>> hot(@RequestParam(defaultValue = "10") int size) {
        return Result.ok(facade.getHotKeywords(size));
    }
}
