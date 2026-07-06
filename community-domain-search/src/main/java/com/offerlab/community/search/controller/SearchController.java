package com.offerlab.community.search.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.search.api.SearchFacade;
import com.offerlab.community.search.api.dto.SearchAnalyticsTrackCmd;
import com.offerlab.community.search.api.dto.SearchStatusDTO;
import com.offerlab.community.search.application.PostSearchIndexer;
import com.offerlab.community.search.application.SearchAnalyticsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@PublicApi
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Validated
public class SearchController {

    private final SearchFacade facade;
    private final SearchAnalyticsService searchAnalyticsService;
    private final PostFacade postFacade;
    private final PostSearchIndexer postSearchIndexer;

    @GetMapping("/posts")
    public Result<PageResult<PostBriefDTO>> searchPosts(@RequestParam(name = "q", required = false) @Size(max = 100) String keyword,
                                                       @RequestParam(required = false) @Size(max = 128) String company,
                                                       @RequestParam(required = false) @Size(max = 128) String position,
                                                       @RequestParam(required = false) Integer type,
                                                       @RequestParam(required = false) @Size(max = 16) String sort,
                                                       @RequestParam(required = false) @Size(max = 32) String cursor,
                                                       @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(facade.searchPosts(keyword, company, position, type, sort, cursor, size, false).publicView());
    }

    @GetMapping("/suggest")
    public Result<List<String>> suggest(@RequestParam @Size(max = 100) String prefix,
                                        @RequestParam(defaultValue = "10") @Min(1) @Max(20) int size) {
        return Result.ok(facade.suggest(prefix, size));
    }

    @GetMapping("/hot")
    public Result<List<String>> hot(@RequestParam(defaultValue = "10") @Min(1) @Max(20) int size) {
        return Result.ok(facade.getHotKeywords(size));
    }

    @GetMapping("/status")
    public Result<SearchStatusDTO> status() {
        return Result.ok(postSearchIndexer.publicStatus());
    }

    @PostMapping("/analytics/track")
    public Result<Map<String, Object>> track(@RequestBody SearchAnalyticsTrackCmd cmd) {
        if (cmd != null && "COMMUNITY_RECOMMEND_CLICK".equalsIgnoreCase(cmd.getEventType())) {
            searchAnalyticsService.recordCommunityRecommendClick(cmd.getKeyword(), firstText(cmd.getTarget(), cmd.getCompany()));
        }
        return Result.ok(Map.of("tracked", true));
    }

    private static String firstText(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }

    @GetMapping("/posts/{postId}/publish-status")
    public Result<Map<String, Object>> publishStatus(@PathVariable @Positive Long postId) {
        UserContext.require();
        PostBriefDTO publicBrief = postFacade.batchGetPosts(List.of(postId)).get(postId);

        Map<String, Object> database = new java.util.LinkedHashMap<>();
        database.put("publiclyVisible", publicBrief != null);

        Map<String, Object> search = new java.util.LinkedHashMap<>();
        PageResult<PostBriefDTO> recall = facade.searchPosts(String.valueOf(postId), null, null, null,
                "relevance", null, 5, false);
        search.put("visible", containsPost(recall, postId));

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("postId", postId);
        data.put("database", database);
        data.put("search", search);
        data.put("ready", publicBrief != null && Boolean.TRUE.equals(search.get("visible")));
        return Result.ok(data);
    }

    private static boolean containsPost(PageResult<PostBriefDTO> page, Long postId) {
        return page != null && page.getItems() != null && page.getItems().stream()
                .anyMatch(post -> java.util.Objects.equals(post.getId(), postId));
    }

}
