package com.offerlab.community.search.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.search.application.PostSearchIndexer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/search/admin")
@RequiredArgsConstructor
public class SearchAdminController {

    private final PostSearchIndexer indexer;

    @PostMapping("/rebuild")
    public Result<Map<String, Object>> rebuildPostIndex() {
        return Result.ok(indexer.rebuildAll());
    }
}
