package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.post.api.PublicContentFilter;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.ContentSeriesDTO;
import com.offerlab.community.post.api.dto.DiscoveryMapDTO;
import com.offerlab.community.post.api.dto.KnowledgeAssetOverviewDTO;
import com.offerlab.community.post.api.dto.KnowledgeAssetSnapshotDTO;
import com.offerlab.community.post.api.dto.KnowledgeGapDTO;
import com.offerlab.community.post.api.dto.KnowledgePathDTO;
import com.offerlab.community.post.api.dto.KnowledgeRelationEdgeDTO;
import com.offerlab.community.post.api.dto.KnowledgeRelationGraphDTO;
import com.offerlab.community.post.api.dto.KnowledgeRelationNodeDTO;
import com.offerlab.community.post.api.dto.PublicKnowledgeAssetDTO;
import com.offerlab.community.post.api.dto.PublicKnowledgeRelationDTO;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.post.domain.model.PostDomain;
import com.offerlab.community.post.infrastructure.persistence.mapper.CommunityTopicMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.CommunityTopicTagMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.po.CommunityTopicPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import com.offerlab.community.post.infrastructure.persistence.po.TagPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class KnowledgeRelationService {

    private static final int MAX_LIMIT = 20;
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_ARCHIVED = "archived";
    private static final String VISIBILITY_VISIBLE = "visible";
    private static final String VISIBILITY_ARCHIVED = "archived";
    private static final String PREVIEW_REMOTE = "remote";
    private static final String REVIEW_AUTO_SAFE = "AUTO_SAFE";
    private static final String RISK_LOW = "LOW";

    private final PostFacade postFacade;
    private final PostMapper postMapper;
    private final CommunityTopicMapper topicMapper;
    private final CommunityTopicTagMapper topicTagMapper;

    public KnowledgeRelationGraphDTO explore(Long postId, Long tagId, Long topicId, Integer domain, int limit) {
        if (postId == null && tagId == null && topicId == null && domain == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Integer activeDomain = domain == null ? null : requireDomain(domain);
        int safeLimit = safeLimit(limit);

        LinkedHashSet<Long> postIds = new LinkedHashSet<>();
        if (postId != null && postId > 0) {
            postIds.add(postId);
        }
        if (tagId != null && tagId > 0) {
            postIds.addAll(selectPostIds(postMapper.selectPublicPosts(null, tagId, null, null, null, null, Long.MAX_VALUE, safeLimit)));
        }
        if (topicId != null && topicId > 0) {
            CommunityTopicPO topic = topicMapper.selectById(topicId);
            if (isOnlineTopic(topic)) {
                List<Long> topicTagIds = topicTagMapper.selectTagsByTopicId(topicId).stream()
                        .map(TagPO::getId)
                        .filter(Objects::nonNull)
                        .toList();
                postIds.addAll(selectPostIds(postMapper.selectPublicPostsByTopic(topicId, topicTagIds,
                        topic.getTopicName(), null, null, null, Long.MAX_VALUE, safeLimit)));
            }
        }
        if (activeDomain != null) {
            postIds.addAll(selectPostIds(postMapper.selectPublicPosts(null, null, null, null, activeDomain, null, Long.MAX_VALUE, safeLimit)));
        }
        Map<Long, PostBriefDTO> posts = postFacade.batchGetPosts(postIds, null, false);
        LinkedHashMap<String, KnowledgeRelationNodeDTO> nodes = new LinkedHashMap<>();
        LinkedHashMap<String, KnowledgeRelationEdgeDTO> edges = new LinkedHashMap<>();
        LinkedHashSet<Long> tagIds = new LinkedHashSet<>();

        for (PostBriefDTO post : posts.values()) {
            if (post == null || post.getId() == null) {
                continue;
            }
            addNode(nodes, KnowledgeRelationNodeDTO.builder()
                    .key("post:" + post.getId())
                    .type("post")
                    .label(post.getTitle())
                    .domain(post.getDomain())
                    .build());
            Integer postDomain = post.getDomain();
            if (PostDomain.isValid(postDomain)) {
                addNode(nodes, KnowledgeRelationNodeDTO.builder()
                        .key("domain:" + postDomain)
                        .type("domain")
                        .label(PostDomain.fromCode(postDomain).name().toLowerCase())
                        .domain(postDomain)
                        .build());
                addEdge(edges, "domain:" + postDomain, "post:" + post.getId(), "domain_post");
            }
            for (TagDTO tag : post.getTags() == null ? List.<TagDTO>of() : post.getTags()) {
                if (!isPublicTag(tag)) {
                    continue;
                }
                tagIds.add(tag.getId());
                addNode(nodes, KnowledgeRelationNodeDTO.builder()
                        .key("tag:" + tag.getId())
                        .type("tag")
                        .label(tag.getName())
                        .build());
                addEdge(edges, "post:" + post.getId(), "tag:" + tag.getId(), "post_tag");
            }
        }

        if (!tagIds.isEmpty()) {
            for (CommunityTopicPO topic : safeTopicsByTagIds(tagIds, safeLimit)) {
                if (!isOnlineTopic(topic)) {
                    continue;
                }
                addNode(nodes, KnowledgeRelationNodeDTO.builder()
                        .key("topic:" + topic.getId())
                        .type("topic")
                        .label(topic.getTopicName())
                        .build());
                for (TagPO tag : topicTagMapper.selectTagsByTopicId(topic.getId())) {
                    if (!isPublicTag(tag)) {
                        continue;
                    }
                    addNode(nodes, KnowledgeRelationNodeDTO.builder()
                            .key("tag:" + tag.getId())
                            .type("tag")
                            .label(tag.getTagName())
                            .build());
                    addEdge(edges, "topic:" + topic.getId(), "tag:" + tag.getId(), "topic_tag");
                }
            }
        }

        return KnowledgeRelationGraphDTO.builder()
                .limit(safeLimit)
                .nodes(List.copyOf(nodes.values()))
                .edges(List.copyOf(edges.values()))
                .build();
    }

    public KnowledgeAssetOverviewDTO aggregatePublicKnowledge(KnowledgeRelationGraphDTO graph,
                                                              List<ContentSeriesDTO> series,
                                                              DiscoveryMapDTO discoveryMap,
                                                              int limit) {
        return knowledgeAssets(graph, series, discoveryMap, limit);
    }

    public KnowledgeAssetOverviewDTO knowledgeAssets(KnowledgeRelationGraphDTO graph,
                                                     List<ContentSeriesDTO> series,
                                                     DiscoveryMapDTO discoveryMap,
                                                     int limit) {
        // Phase 5 contract: previewSource remote is required for formal writes; assetStatus active/archived are lifecycle states.
        // displayState normal/partial/degraded is response-only, and degraded is never persisted as a path lifecycle.
        int safeLimit = safeLimit(limit);
        LocalDateTime now = LocalDateTime.now();
        LinkedHashMap<String, PublicKnowledgeAssetDTO> assets = new LinkedHashMap<>();

        for (KnowledgeRelationNodeDTO node : graph == null || graph.getNodes() == null
                ? List.<KnowledgeRelationNodeDTO>of() : graph.getNodes()) {
            PublicKnowledgeAssetDTO asset = assetFromNode(node, now);
            if (asset != null) {
                assets.putIfAbsent(asset.getAssetId(), asset);
            }
        }
        for (ContentSeriesDTO item : series == null ? List.<ContentSeriesDTO>of() : series) {
            PublicKnowledgeAssetDTO asset = assetFromSeries(item, now);
            if (asset != null) {
                assets.putIfAbsent(asset.getAssetId(), asset);
            }
        }
        for (DiscoveryMapDTO.DiscoveryItemDTO item : discoveryItems(discoveryMap)) {
            PublicKnowledgeAssetDTO asset = assetFromDiscovery(item, now);
            if (asset != null) {
                assets.putIfAbsent(asset.getAssetId(), asset);
            }
        }

        LinkedHashMap<String, PublicKnowledgeRelationDTO> relations = new LinkedHashMap<>();
        for (KnowledgeRelationEdgeDTO edge : graph == null || graph.getEdges() == null
                ? List.<KnowledgeRelationEdgeDTO>of() : graph.getEdges()) {
            PublicKnowledgeRelationDTO relation = relationFromEdge(edge, assets, now);
            if (relation != null) {
                relations.putIfAbsent(relation.getRelationId(), relation);
            }
        }
        addSeriesRelations(relations, assets, now);
        addSearchRelations(relations, assets, now);

        List<PublicKnowledgeAssetDTO> publicAssets = assets.values().stream()
                .limit(safeLimit)
                .toList();
        List<PublicKnowledgeRelationDTO> publicRelations = relations.values().stream()
                .filter(relation -> containsAsset(publicAssets, relation.getSourceAssetId()))
                .filter(relation -> containsAsset(publicAssets, relation.getTargetAssetId()))
                .limit(safeLimit)
                .toList();
        List<KnowledgePathDTO> paths = buildPaths(publicAssets, publicRelations, safeLimit, now);
        List<KnowledgeGapDTO> gaps = buildGaps(publicAssets, safeLimit);
        List<KnowledgeAssetSnapshotDTO> snapshots = buildSnapshots(publicAssets, publicRelations, safeLimit, now);

        return KnowledgeAssetOverviewDTO.builder()
                .limit(safeLimit)
                .generatedAt(now)
                .assets(publicAssets)
                .relations(publicRelations)
                .paths(paths)
                .gaps(gaps)
                .snapshots(snapshots)
                .build();
    }

    private List<CommunityTopicPO> safeTopicsByTagIds(Collection<Long> tagIds, int limit) {
        try {
            return topicMapper.selectOnlineTopicsByTagIds(tagIds, limit);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static boolean isOnlineTopic(CommunityTopicPO topic) {
        return topic != null
                && topic.getId() != null
                && !Integer.valueOf(1).equals(topic.getIsDeleted())
                && Integer.valueOf(1).equals(topic.getTopicStatus())
                && !PublicContentFilter.isSyntheticText(topic.getTopicName())
                && !PublicContentFilter.isUnsafeSuggestionText(topic.getTopicName());
    }

    private static boolean isPublicTag(TagDTO tag) {
        return tag != null
                && tag.getId() != null
                && (tag.getStatus() == null || Integer.valueOf(1).equals(tag.getStatus()))
                && tag.getMergeTargetId() == null
                && !PublicContentFilter.isSyntheticText(tag.getName())
                && !PublicContentFilter.isUnsafeSuggestionText(tag.getName());
    }

    private static boolean isPublicTag(TagPO tag) {
        return tag != null
                && tag.getId() != null
                && (tag.getTagStatus() == null || Integer.valueOf(1).equals(tag.getTagStatus()))
                && tag.getMergeTargetId() == null
                && !PublicContentFilter.isSyntheticText(tag.getTagName())
                && !PublicContentFilter.isUnsafeSuggestionText(tag.getTagName());
    }

    private static void addNode(Map<String, KnowledgeRelationNodeDTO> nodes, KnowledgeRelationNodeDTO node) {
        nodes.putIfAbsent(node.getKey(), node);
    }

    private static void addEdge(Map<String, KnowledgeRelationEdgeDTO> edges, String source, String target, String relation) {
        String key = source + "|" + target + "|" + relation;
        edges.putIfAbsent(key, KnowledgeRelationEdgeDTO.builder()
                .source(source)
                .target(target)
                .relation(relation)
                .weight(1)
                .build());
    }

    private static List<Long> selectPostIds(List<PostPO> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        return posts.stream()
                .map(PostPO::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    private static PublicKnowledgeAssetDTO assetFromNode(KnowledgeRelationNodeDTO node, LocalDateTime now) {
        if (node == null
                || !StringUtils.hasText(node.getKey())
                || !StringUtils.hasText(node.getType())
                || !hasSafeText(node.getLabel())) {
            return null;
        }
        String assetType = switch (node.getType()) {
            case "post" -> "post";
            case "topic" -> "topic";
            case "tag" -> "tag";
            default -> null;
        };
        if (assetType == null) {
            return null;
        }
        return PublicKnowledgeAssetDTO.builder()
                .assetId(node.getKey())
                .assetType(assetType)
                .title(node.getLabel())
                .summary(sourceNote(assetType))
                .assetStatus(STATUS_ACTIVE)
                .visibilityState(VISIBILITY_VISIBLE)
                .source(assetType)
                .previewSource(PREVIEW_REMOTE)
                .sourceNote(sourceNote(assetType))
                .targetHref(targetHref(assetType, node.getKey()))
                .domain(node.getDomain())
                .updatedAt(now)
                .build();
    }

    private static PublicKnowledgeAssetDTO assetFromSeries(ContentSeriesDTO series, LocalDateTime now) {
        if (series == null
                || series.getId() == null
                || !Integer.valueOf(1).equals(series.getVisibility())
                || !hasSafeText(series.getTitle())
                || !hasSafeText(series.getDescription())) {
            return null;
        }
        return PublicKnowledgeAssetDTO.builder()
                .assetId("series:" + series.getId())
                .assetType("series")
                .title(series.getTitle())
                .summary(series.getDescription())
                .assetStatus(STATUS_ACTIVE)
                .visibilityState(VISIBILITY_VISIBLE)
                .source("series")
                .previewSource(PREVIEW_REMOTE)
                .sourceNote("From public content series")
                .targetHref("/collections/" + series.getId())
                .domain(series.getDomain())
                .updatedAt(series.getUpdateTime() == null ? now : series.getUpdateTime())
                .build();
    }

    private static PublicKnowledgeAssetDTO assetFromDiscovery(DiscoveryMapDTO.DiscoveryItemDTO item, LocalDateTime now) {
        if (!isFormalDiscoveryItem(item)) {
            return null;
        }
        String assetStatus = normalizeAssetStatus(item.getAssetStatus());
        if (assetStatus == null) {
            return null;
        }
        String source = "search";
        String assetType = "search_entry";
        if ("operation-topic".equals(item.getType()) || "community-topic".equals(item.getType())) {
            assetType = "topic";
            source = "curation";
        } else if ("post".equals(item.getType())) {
            assetType = "post";
            source = "curation";
        }
        String sourceKey = item.getId();
        String assetId = assetType + ":" + assetKey(sourceKey);
        return PublicKnowledgeAssetDTO.builder()
                .assetId(assetId)
                .assetType(assetType)
                .title(item.getTitle())
                .summary(item.getSummary())
                .assetStatus(assetStatus)
                .visibilityState(STATUS_ARCHIVED.equals(assetStatus) ? VISIBILITY_ARCHIVED : VISIBILITY_VISIBLE)
                .source(source)
                .previewSource(PREVIEW_REMOTE)
                .sourceNote(item.getReasonText() == null ? sourceNote(assetType) : item.getReasonText())
                .targetHref(item.getHref())
                .domain(item.getDomain())
                .updatedAt(item.getUpdatedAt() == null ? now : item.getUpdatedAt())
                .build();
    }

    private static PublicKnowledgeRelationDTO relationFromEdge(KnowledgeRelationEdgeDTO edge,
                                                               Map<String, PublicKnowledgeAssetDTO> assets,
                                                               LocalDateTime now) {
        if (edge == null || !assets.containsKey(edge.getSource()) || !assets.containsKey(edge.getTarget())) {
            return null;
        }
        String relationType = switch (edge.getRelation()) {
            case "post_tag" -> "belongs_to";
            case "topic_tag" -> "related";
            default -> null;
        };
        if (relationType == null) {
            return null;
        }
        String source = "post_tag".equals(edge.getRelation()) ? "tag" : "topic";
        return relation(edge.getSource(), edge.getTarget(), relationType, relationReason(edge.getRelation()), source, now);
    }

    private static void addSeriesRelations(Map<String, PublicKnowledgeRelationDTO> relations,
                                           Map<String, PublicKnowledgeAssetDTO> assets,
                                           LocalDateTime now) {
        List<String> postAssetIds = assets.values().stream()
                .filter(asset -> "post".equals(asset.getAssetType()))
                .map(PublicKnowledgeAssetDTO::getAssetId)
                .toList();
        if (postAssetIds.isEmpty()) {
            return;
        }
        for (PublicKnowledgeAssetDTO series : assets.values()) {
            if (!"series".equals(series.getAssetType())) {
                continue;
            }
            PublicKnowledgeRelationDTO relation = relation(series.getAssetId(), postAssetIds.get(0),
                    "continues", "Public series extends this public content", "series", now);
            relations.putIfAbsent(relation.getRelationId(), relation);
        }
    }

    private static void addSearchRelations(Map<String, PublicKnowledgeRelationDTO> relations,
                                           Map<String, PublicKnowledgeAssetDTO> assets,
                                           LocalDateTime now) {
        List<String> targetAssetIds = assets.values().stream()
                .filter(asset -> !"search_entry".equals(asset.getAssetType()))
                .map(PublicKnowledgeAssetDTO::getAssetId)
                .toList();
        if (targetAssetIds.isEmpty()) {
            return;
        }
        for (PublicKnowledgeAssetDTO search : assets.values()) {
            if (!"search_entry".equals(search.getAssetType())) {
                continue;
            }
            PublicKnowledgeRelationDTO relation = relation(search.getAssetId(), targetAssetIds.get(0),
                    "search_entry", "Search entry points to public knowledge assets", "search", now);
            relations.putIfAbsent(relation.getRelationId(), relation);
        }
    }

    private static PublicKnowledgeRelationDTO relation(String sourceAssetId,
                                                       String targetAssetId,
                                                       String relationType,
                                                       String reasonText,
                                                       String source,
                                                       LocalDateTime now) {
        return PublicKnowledgeRelationDTO.builder()
                .relationId("relation:" + sourceAssetId + ":" + targetAssetId + ":" + relationType)
                .sourceAssetId(sourceAssetId)
                .targetAssetId(targetAssetId)
                .relationType(relationType)
                .reasonText(reasonText)
                .source(source)
                .reviewStatus(REVIEW_AUTO_SAFE)
                .riskLevel(RISK_LOW)
                .createdAt(now)
                .build();
    }

    private static List<KnowledgePathDTO> buildPaths(List<PublicKnowledgeAssetDTO> assets,
                                                     List<PublicKnowledgeRelationDTO> relations,
                                                     int limit,
                                                     LocalDateTime now) {
        List<PublicKnowledgeAssetDTO> steps = assets.stream()
                .filter(asset -> !"search_entry".equals(asset.getAssetType()))
                .limit(Math.min(limit, 5))
                .toList();
        if (steps.isEmpty()) {
            return List.of();
        }
        PublicKnowledgeAssetDTO entry = steps.get(0);
        List<KnowledgePathDTO.KnowledgePathStepDTO> pathSteps = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            PublicKnowledgeAssetDTO asset = steps.get(i);
            pathSteps.add(KnowledgePathDTO.KnowledgePathStepDTO.builder()
                    .assetId(asset.getAssetId())
                    .title(asset.getTitle())
                    .assetType(asset.getAssetType())
                    .order(i + 1)
                    .build());
        }
        String pathStatus = steps.stream().anyMatch(asset -> STATUS_ARCHIVED.equals(asset.getAssetStatus()))
                ? STATUS_ARCHIVED : STATUS_ACTIVE;
        return List.of(KnowledgePathDTO.builder()
                .pathId("path:" + entry.getAssetId())
                .title(entry.getTitle())
                .summary("Public reading path generated from public asset relations")
                .entryAssetId(entry.getAssetId())
                .steps(pathSteps)
                .sourceRefs(relations.stream().map(PublicKnowledgeRelationDTO::getRelationId).limit(limit).toList())
                .pathStatus(pathStatus)
                .displayState("normal")
                .updatedAt(now)
                .build());
    }

    private static List<KnowledgeGapDTO> buildGaps(List<PublicKnowledgeAssetDTO> assets, int limit) {
        return assets.stream()
                .filter(asset -> "search_entry".equals(asset.getAssetType()))
                .limit(Math.min(limit, 3))
                .map(asset -> KnowledgeGapDTO.builder()
                        .gapId("gap:" + asset.getAssetId())
                        .title(asset.getTitle())
                        .reasonText("Aggregated search entry indicates a public content coverage gap")
                        .source("search")
                        .sourceRefs(List.of(asset.getAssetId()))
                        .minSampleMet(true)
                        .reviewStatus("CANDIDATE")
                        .targetStage("knowledge")
                        .build())
                .toList();
    }

    private static List<KnowledgeAssetSnapshotDTO> buildSnapshots(List<PublicKnowledgeAssetDTO> assets,
                                                                  List<PublicKnowledgeRelationDTO> relations,
                                                                  int limit,
                                                                  LocalDateTime now) {
        return assets.stream()
                .filter(asset -> STATUS_ARCHIVED.equals(asset.getAssetStatus()) || !"search_entry".equals(asset.getAssetType()))
                .limit(Math.min(limit, 3))
                .map(asset -> KnowledgeAssetSnapshotDTO.builder()
                        .snapshotId("snapshot:" + asset.getAssetId())
                        .assetId(asset.getAssetId())
                        .assetType(asset.getAssetType())
                        .title(asset.getTitle())
                        .summary(asset.getSummary())
                        .sections(List.of(asset.getSourceNote()))
                        .relations(relations.stream()
                                .filter(relation -> Objects.equals(relation.getSourceAssetId(), asset.getAssetId())
                                        || Objects.equals(relation.getTargetAssetId(), asset.getAssetId()))
                                .map(PublicKnowledgeRelationDTO::getRelationId)
                                .limit(limit)
                                .toList())
                        .sourceNote(asset.getSourceNote())
                        .archivedAt(STATUS_ARCHIVED.equals(asset.getAssetStatus()) ? asset.getUpdatedAt() : now)
                        .build())
                .toList();
    }

    private static boolean containsAsset(List<PublicKnowledgeAssetDTO> assets, String assetId) {
        return assets.stream().anyMatch(asset -> Objects.equals(asset.getAssetId(), assetId));
    }

    private static List<DiscoveryMapDTO.DiscoveryItemDTO> discoveryItems(DiscoveryMapDTO discoveryMap) {
        if (discoveryMap == null) {
            return List.of();
        }
        List<DiscoveryMapDTO.DiscoveryItemDTO> result = new ArrayList<>();
        result.addAll(discoveryMap.getFeaturedTopics() == null ? List.of() : discoveryMap.getFeaturedTopics());
        result.addAll(discoveryMap.getActiveTopics() == null ? List.of() : discoveryMap.getActiveTopics());
        result.addAll(discoveryMap.getSearchEntrypoints() == null ? List.of() : discoveryMap.getSearchEntrypoints());
        result.addAll(discoveryMap.getChannels() == null ? List.of() : discoveryMap.getChannels());
        result.addAll(discoveryMap.getContentForms() == null ? List.of() : discoveryMap.getContentForms());
        return result;
    }

    private static boolean isFormalDiscoveryItem(DiscoveryMapDTO.DiscoveryItemDTO item) {
        return item != null
                && isFormalDiscoverySource(item.getSource())
                && isFormalPreviewSource(item.getPreviewSource())
                && hasSafeHref(item.getHref())
                && hasSafeText(item.getTitle())
                && hasSafeText(item.getSummary())
                && hasSafeText(item.getReasonText());
    }

    private static boolean isFormalDiscoverySource(String source) {
        return DiscoveryMapService.SOURCE_OPERATION_CURATION.equals(source)
                || DiscoveryMapService.SOURCE_COMMUNITY_TOPIC.equals(source)
                || DiscoveryMapService.SOURCE_SEARCH_ANALYTICS.equals(source)
                || DiscoveryMapService.SOURCE_PUBLIC_CONTENT_QUERY.equals(source);
    }

    private static boolean isFormalPreviewSource(String previewSource) {
        return previewSource == null || PREVIEW_REMOTE.equals(previewSource);
    }

    private static boolean hasSafeHref(String href) {
        String normalized = href == null ? "" : href.toLowerCase();
        return StringUtils.hasText(href)
                && href.startsWith("/")
                && !href.startsWith("//")
                && !normalized.contains("fallback")
                && !normalized.contains("demo")
                && !normalized.contains("fixture")
                && !normalized.contains("local");
    }

    private static boolean hasSafeText(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        String normalized = value.toLowerCase();
        return !normalized.contains("fallback")
                && !normalized.contains("demo")
                && !normalized.contains("fixture")
                && !normalized.contains("local-only")
                && !normalized.contains("offline")
                && !normalized.contains("degraded")
                && !PublicContentFilter.isSyntheticText(value)
                && !PublicContentFilter.isUnsafeSuggestionText(value);
    }

    private static String normalizeAssetStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return STATUS_ACTIVE;
        }
        String normalized = status.trim().toLowerCase();
        if (STATUS_ACTIVE.equals(normalized) || STATUS_ARCHIVED.equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private static String assetKey(String id) {
        if (!StringUtils.hasText(id)) {
            return "unknown";
        }
        int index = id.indexOf(':');
        return index >= 0 && index + 1 < id.length() ? id.substring(index + 1) : id;
    }

    private static String sourceNote(String assetType) {
        return switch (assetType) {
            case "post" -> "From public post";
            case "series" -> "From public content series";
            case "topic" -> "From public topic";
            case "tag" -> "From public tag";
            case "search_entry" -> "From search discovery entry";
            default -> "From public knowledge asset";
        };
    }

    private static String targetHref(String assetType, String assetId) {
        String id = assetKey(assetId);
        return switch (assetType) {
            case "post" -> "/post/" + id;
            case "topic" -> "/topics/" + id;
            case "tag" -> "/search?tagId=" + id;
            default -> "/knowledge";
        };
    }

    private static String relationReason(String relation) {
        return switch (relation) {
            case "post_tag" -> "Public post uses this tag";
            case "topic_tag" -> "Public topic is related to this tag";
            default -> "Public assets have an explainable relation";
        };
    }

    private static Integer requireDomain(Integer domain) {
        if (!PostDomain.isValid(domain)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return domain;
    }

    private static int safeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 8 : limit, MAX_LIMIT));
    }
}
