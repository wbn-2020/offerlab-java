package com.offerlab.community.search.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.infra.mq.outbox.OutboxMessage;
import com.offerlab.community.infra.mq.outbox.OutboxMessageMapper;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.search.api.SearchFacade;
import com.offerlab.community.search.application.PostSearchIndexer;
import com.offerlab.community.search.api.dto.SearchAnalyticsTrackCmd;
import com.offerlab.community.search.application.SearchAnalyticsService;
import com.offerlab.community.search.application.SearchIndexRetryService;
import com.offerlab.community.search.infrastructure.persistence.po.SearchIndexRetryTaskPO;
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
    private final PostSearchIndexer indexer;
    private final SearchAnalyticsService searchAnalyticsService;
    private final PostFacade postFacade;
    private final ElasticsearchHttpClient elasticsearch;
    private final SearchIndexRetryService searchIndexRetryService;
    private final OutboxMessageMapper outboxMessageMapper;

    @GetMapping("/posts")
    public Result<PageResult<PostBriefDTO>> searchPosts(@RequestParam(name = "q", required = false) @Size(max = 100) String keyword,
                                                       @RequestParam(required = false) @Size(max = 128) String company,
                                                       @RequestParam(required = false) @Size(max = 128) String position,
                                                       @RequestParam(required = false) Integer type,
                                                       @RequestParam(required = false) @Size(max = 16) String sort,
                                                       @RequestParam(required = false) @Size(max = 32) String cursor,
                                                       @RequestParam(defaultValue = "false") boolean includeTestData,
                                                       @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(facade.searchPosts(keyword, company, position, type, sort, cursor, size, includeTestData));
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

    @PostMapping("/analytics/track")
    public Result<Map<String, Object>> track(@RequestBody SearchAnalyticsTrackCmd cmd) {
        if (cmd != null && "PREP_CLICK".equalsIgnoreCase(cmd.getEventType())) {
            searchAnalyticsService.recordPrepClick(cmd.getKeyword(), cmd.getCompany());
        } else if (cmd != null && "COMMUNITY_RECOMMEND_CLICK".equalsIgnoreCase(cmd.getEventType())) {
            searchAnalyticsService.recordCommunityRecommendClick(cmd.getKeyword(), firstText(cmd.getTarget(), cmd.getCompany()));
        }
        return Result.ok(Map.of("tracked", true));
    }

    private static String firstText(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(indexer.status());
    }

    @GetMapping("/posts/{postId}/publish-status")
    public Result<Map<String, Object>> publishStatus(@PathVariable @Positive Long postId) {
        PostBriefDTO publicBrief = postFacade.batchGetPosts(List.of(postId)).get(postId);
        PostBriefDTO testDataBrief = postFacade.batchGetPosts(List.of(postId), true).get(postId);
        boolean dbVisible = publicBrief != null || testDataBrief != null;

        Map<String, Object> database = new java.util.LinkedHashMap<>();
        database.put("landed", dbVisible);
        database.put("publiclyVisible", publicBrief != null);
        database.put("visibleWithTestData", testDataBrief != null);
        database.put("postType", testDataBrief == null ? null : testDataBrief.getPostType());
        database.put("title", testDataBrief == null ? null : testDataBrief.getTitle());

        Map<String, Object> search = new java.util.LinkedHashMap<>();
        PageResult<PostBriefDTO> recall = facade.searchPosts(String.valueOf(postId), null, null, null,
                "relevance", null, 5, true);
        search.put("visible", containsPost(recall, postId));
        search.put("source", recall.getSource());
        search.put("degraded", recall.getDegraded());
        search.put("fallbackReason", recall.getFallbackReason());
        search.put("diagnostics", recall.getDiagnostics());

        Map<String, Object> index = new java.util.LinkedHashMap<>();
        index.put("status", publicIndexStatus());
        index.put("documentFound", elasticsearch.getDocument(elasticsearch.postIndex(), String.valueOf(postId))
                .map(document -> document.path("found").asBoolean(false))
                .orElse(false));
        index.put("retryTask", summarizeRetryTask(searchIndexRetryService.findLatestByPostId(postId)));

        Map<String, Object> outbox = new java.util.LinkedHashMap<>();
        outbox.put("latest", summarizeOutbox(outboxMessageMapper.findLatestByAggregate("post", postId)));

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("postId", postId);
        data.put("database", database);
        data.put("index", index);
        data.put("search", search);
        data.put("outbox", outbox);
        data.put("ready", dbVisible && Boolean.TRUE.equals(search.get("visible")));
        return Result.ok(data);
    }

    private static boolean containsPost(PageResult<PostBriefDTO> page, Long postId) {
        return page != null && page.getItems() != null && page.getItems().stream()
                .anyMatch(post -> java.util.Objects.equals(post.getId(), postId));
    }

    private Map<String, Object> publicIndexStatus() {
        boolean enabled = elasticsearch.enabled();
        boolean available = enabled && elasticsearch.available();
        boolean exists = available && elasticsearch.indexExists(elasticsearch.postIndex());
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("enabled", enabled);
        data.put("available", available);
        data.put("indexName", elasticsearch.postIndex());
        data.put("indexExists", exists);
        data.put("status", exists ? "UP" : available ? "DEGRADED" : "DOWN");
        return data;
    }

    private static Map<String, Object> summarizeRetryTask(SearchIndexRetryTaskPO task) {
        if (task == null) {
            return null;
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("id", task.getId());
        data.put("operation", task.getOperation());
        data.put("status", task.getTaskStatus());
        data.put("statusText", switch (task.getTaskStatus() == null ? -1 : task.getTaskStatus()) {
            case 0 -> "pending";
            case 1 -> "running";
            case 2 -> "failed";
            case 3 -> "done";
            default -> "unknown";
        });
        data.put("retryCount", task.getRetryCount());
        data.put("updateTime", task.getUpdateTime() == null ? null : task.getUpdateTime().toString());
        return data;
    }

    private static Map<String, Object> summarizeOutbox(OutboxMessage message) {
        if (message == null) {
            return null;
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("id", message.getId());
        data.put("topic", message.getTopic());
        data.put("status", message.getMsgStatus());
        data.put("statusText", switch (message.getMsgStatus() == null ? -1 : message.getMsgStatus()) {
            case OutboxMessageMapper.STATUS_PENDING -> "pending";
            case OutboxMessageMapper.STATUS_SENT -> "sent";
            case OutboxMessageMapper.STATUS_FAILED -> "failed";
            case OutboxMessageMapper.STATUS_SENDING -> "sending";
            default -> "unknown";
        });
        data.put("retryCount", message.getRetryCount());
        data.put("nextRetryTime", message.getNextRetryTime() == null ? null : message.getNextRetryTime().toString());
        data.put("updateTime", message.getUpdateTime() == null ? null : message.getUpdateTime().toString());
        return data;
    }
}
