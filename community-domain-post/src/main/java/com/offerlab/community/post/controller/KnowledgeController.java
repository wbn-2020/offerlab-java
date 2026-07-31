package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.api.dto.ContentSeriesDTO;
import com.offerlab.community.post.api.dto.DiscoveryMapDTO;
import com.offerlab.community.post.api.dto.KnowledgeAssetOverviewDTO;
import com.offerlab.community.post.api.dto.KnowledgeRelationGraphDTO;
import com.offerlab.community.post.api.dto.KnowledgeRelationNodeDTO;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.application.ContentSeriesService;
import com.offerlab.community.post.application.DiscoveryMapService;
import com.offerlab.community.post.application.KnowledgeRelationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeRelationService knowledgeRelationService;
    private final DiscoveryMapService discoveryMapService;
    private final ContentSeriesService contentSeriesService;

    @PublicApi
    @GetMapping("/relations")
    @RateLimit(key = "'public:knowledge:relations:' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<KnowledgeRelationGraphDTO> relations(@RequestParam(required = false) Long postId,
                                                       @RequestParam(required = false) Long tagId,
                                                       @RequestParam(required = false) Long topicId,
                                                       @RequestParam(required = false) Integer domain,
                                                       @RequestParam(defaultValue = "8") int limit,
                                                       HttpServletRequest request) {
        return Result.ok(knowledgeRelationService.explore(postId, tagId, topicId, domain, limit));
    }

    @PublicApi
    @GetMapping("/assets")
    @RateLimit(key = "'public:knowledge:assets:' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<KnowledgeAssetOverviewDTO> assets(@RequestParam(required = false) Long postId,
                                                    @RequestParam(required = false) Long tagId,
                                                    @RequestParam(required = false) Long topicId,
                                                    @RequestParam(required = false) String assetId,
                                                    @RequestParam(required = false) String assetType,
                                                    @RequestParam(required = false) Integer domain,
                                                    @RequestParam(defaultValue = "8") int limit,
                                                    HttpServletRequest request) {
        Long resolvedPostId = postId;
        if (resolvedPostId == null && "post".equals(assetType)) {
            resolvedPostId = parseAssetNumericId(assetId);
        }
        List<ContentSeriesDTO> requestedSeries = List.of();
        List<Long> requestedSeriesPostIds = List.of();
        if ("series".equals(assetType)) {
            Long seriesId = parseAssetNumericId(assetId);
            if (seriesId != null && seriesId > 0) {
                ContentSeriesDTO series = contentSeriesService.getPublicDetail(seriesId);
                requestedSeries = List.of(series);
                PageResult<PostBriefDTO> seriesPosts = contentSeriesService.listPublicPosts(seriesId, 0, limit);
                requestedSeriesPostIds = seriesPosts.getItems() == null ? List.of() : seriesPosts.getItems().stream()
                        .map(PostBriefDTO::getId)
                        .filter(id -> id != null && id > 0)
                        .toList();
                if (resolvedPostId == null && !requestedSeriesPostIds.isEmpty()) {
                    resolvedPostId = requestedSeriesPostIds.get(0);
                }
            }
        }
        KnowledgeRelationGraphDTO graph = knowledgeRelationService.explore(resolvedPostId, tagId, topicId, domain, limit);
        List<Long> postIds = graph.getNodes() == null ? List.of() : graph.getNodes().stream()
                .filter(node -> "post".equals(node.getType()))
                .map(KnowledgeRelationNodeDTO::getKey)
                .map(KnowledgeController::parseAssetNumericId)
                .filter(id -> id != null && id > 0)
                .toList();
        Map<Long, ContentSeriesDTO> seriesById = new LinkedHashMap<>();
        requestedSeries.forEach(item -> {
            if (item != null && item.getId() != null) {
                seriesById.put(item.getId(), item);
            }
        });
        List<Long> relatedPostIds = new java.util.ArrayList<>(postIds);
        relatedPostIds.addAll(requestedSeriesPostIds);
        contentSeriesService.listPublicByPostIds(relatedPostIds, limit).forEach(item -> {
            if (item != null && item.getId() != null) {
                seriesById.putIfAbsent(item.getId(), item);
            }
        });
        DiscoveryMapDTO discoveryMap = discoveryMapService.getPublicMap(limit, limit);
        return Result.ok(knowledgeRelationService.aggregatePublicKnowledge(graph, List.copyOf(seriesById.values()), discoveryMap, limit));
    }

    private static Long parseAssetNumericId(String key) {
        if (key == null) {
            return null;
        }
        int index = key.indexOf(':');
        String id = index >= 0 && index + 1 < key.length() ? key.substring(index + 1) : key;
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
