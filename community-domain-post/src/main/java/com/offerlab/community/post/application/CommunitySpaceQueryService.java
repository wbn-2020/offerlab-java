package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.CommunitySpaceDTO;
import com.offerlab.community.post.api.dto.ContentSeriesDTO;
import com.offerlab.community.post.api.dto.DiscoveryMapDTO;
import com.offerlab.community.post.api.dto.KnowledgeAssetOverviewDTO;
import com.offerlab.community.post.api.dto.KnowledgeRelationGraphDTO;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PublicPostUpdateDTO;
import com.offerlab.community.post.infrastructure.persistence.CommunitySpaceRows;
import com.offerlab.community.post.infrastructure.persistence.mapper.CommunitySpaceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunitySpaceQueryService {

    private static final int MAX_PAGE_SIZE = 20;
    private static final int MAX_AUXILIARY_ITEMS = 20;

    private final CommunityTopicService topicService;
    private final ContentSeriesService contentSeriesService;
    private final KnowledgeRelationService knowledgeRelationService;
    private final DiscoveryMapService discoveryMapService;
    private final PostFacade postFacade;
    private final CommunitySpaceMapper spaceMapper;

    public CommunitySpaceDTO getTopicSpace(String slug, Long viewerUid, long cursor, int size) {
        var topic = topicService.getPublic(slug, viewerUid);
        PageResult<PostBriefDTO> posts = safeTopicPosts(slug, cursor, size);
        return buildSpace(
                "TOPIC",
                topic.getId(),
                topic.getSlug(),
                topic.getName(),
                topic.getDescription(),
                null,
                topic.getStatus() != null && topic.getStatus() == 1 ? "PUBLIC" : "HIDDEN",
                "/topics/" + encodePath(topic.getSlug()),
                posts
        );
    }

    public CommunitySpaceDTO getSeriesSpace(Long seriesId, Long viewerUid, long cursor, int size) {
        ContentSeriesDTO series = contentSeriesService.getPublicDetail(seriesId);
        PageResult<PostBriefDTO> posts = safeSeriesPosts(seriesId, cursor, size);
        return buildSpace(
                "COLLECTION",
                series.getId(),
                null,
                series.getTitle(),
                series.getDescription(),
                series.getDomain(),
                Objects.equals(series.getVisibility(), 1) ? "PUBLIC" : "HIDDEN",
                "/collections/" + series.getId(),
                posts
        );
    }

    public CommunitySpaceDTO getCollaborationSeriesSpace(Long seriesId, Long viewerUid, long cursor, int size) {
        CommunitySpaceRows.CollaborationSeriesRow series = requirePublicCollaborationSeries(seriesId);
        PageResult<PostBriefDTO> posts = publicCollaborationSeriesPosts(seriesId, cursor, size);
        return buildSpace(
                "SERIES",
                series.getId(),
                null,
                series.getTitle(),
                series.getDescription(),
                series.getDomain(),
                "PUBLIC",
                "/collaboration/series/" + series.getId(),
                posts
        );
    }

    private CommunitySpaceDTO buildSpace(String spaceType,
                                         Long spaceId,
                                         String slug,
                                         String title,
                                         String description,
                                         Integer domain,
                                         String visibility,
                                         String canonicalPath,
                                         PageResult<PostBriefDTO> postPage) {
        List<String> degraded = new ArrayList<>();
        markRepresentativeItemsDegraded(postPage, degraded);
        List<PostBriefDTO> posts = postPage == null || postPage.getItems() == null
                ? List.of() : postPage.getItems().stream()
                .filter(Objects::nonNull)
                .filter(post -> post.getId() != null && post.getId() > 0)
                .limit(MAX_AUXILIARY_ITEMS)
                .toList();
        List<Long> postIds = posts.stream().map(PostBriefDTO::getId).toList();

        List<CommunitySpaceDTO.PublicUpdateDTO> updates = publicUpdates(posts, degraded);
        List<CommunitySpaceRows.NeedRow> needRows = publicNeeds(
                spaceType, spaceId, postIds, degraded);
        List<CommunitySpaceDTO.RelatedNeedDTO> needs = needRows.stream()
                .map(this::toNeed)
                .filter(Objects::nonNull)
                .toList();

        List<CommunitySpaceRows.ContributionRow> contributionRows = publicContributions(
                spaceType, spaceId, postIds, degraded);
        List<CommunitySpaceDTO.RelatedContributionDTO> contributions = contributionRows.stream()
                .map(this::toContribution)
                .filter(Objects::nonNull)
                .toList();

        long maintenanceCount = publicMaintenanceCount(postIds, degraded);
        KnowledgeAssetOverviewDTO knowledge = publicKnowledge(spaceType, spaceId, postIds, degraded);
        DiscoveryMapDTO discovery = publicDiscovery(degraded);

        return CommunitySpaceDTO.builder()
                .spaceType(spaceType)
                .spaceId(spaceId)
                .slug(slug)
                .title(title)
                .description(description)
                .domain(domain)
                .visibility(visibility)
                .canonicalPath(canonicalPath)
                .representativeItems(posts)
                .knowledge(knowledge)
                .discovery(discovery)
                .latestUpdates(updates)
                .relatedNeeds(needs)
                .relatedContributions(contributions)
                .governanceSummary(CommunitySpaceDTO.GovernanceSummaryDTO.builder()
                        .publicItemCount(posts.size())
                        .publicContributionCount(contributions.size())
                        .hasPublicMaintenance(maintenanceCount > 0)
                        .publicStatus(maintenanceCount > 0 ? "部分内容正在维护" : "公开内容可浏览")
                        .build())
                .nextCursor(postPage == null ? null : postPage.getNextCursor())
                .hasMore(postPage != null && Boolean.TRUE.equals(postPage.getHasMore()))
                .degradedSources(List.copyOf(new LinkedHashSet<>(degraded)))
                .build();
    }

    private PageResult<PostBriefDTO> safeTopicPosts(String slug, long cursor, int size) {
        try {
            return topicService.listPosts(slug, null, null, cursor, pageSize(size), null);
        } catch (RuntimeException e) {
            log.warn("public topic posts query failed, slug={}", slug, e);
            return PageResult.<PostBriefDTO>empty()
                    .withDiagnostic("representativeItemsUnavailable", true);
        }
    }

    private PageResult<PostBriefDTO> safeSeriesPosts(Long seriesId, long cursor, int size) {
        try {
            return contentSeriesService.listPublicPosts(seriesId, cursor, pageSize(size));
        } catch (RuntimeException e) {
            log.warn("public series posts query failed, seriesId={}", seriesId, e);
            return PageResult.<PostBriefDTO>empty()
                    .withDiagnostic("representativeItemsUnavailable", true);
        }
    }

    private PageResult<PostBriefDTO> publicCollaborationSeriesPosts(Long seriesId, long cursor, int size) {
        int limit = pageSize(size);
        try {
            List<CommunitySpaceRows.ContributionRow> rows = spaceMapper.listPublicSeriesContributions(
                    seriesId, cursor > 0 ? cursor : null, limit + 1);
            List<CommunitySpaceRows.ContributionRow> safeRows = rows == null ? List.of() : rows;
            boolean hasMore = safeRows.size() > limit;
            List<CommunitySpaceRows.ContributionRow> visible = safeRows.stream().limit(limit).toList();
            List<Long> postIds = visible.stream()
                    .map(CommunitySpaceRows.ContributionRow::getPostId)
                    .filter(Objects::nonNull)
                    .toList();
            Map<Long, PostBriefDTO> publicPosts = postFacade.batchGetPosts(postIds, null, false);
            List<PostBriefDTO> items = postIds.stream()
                    .map(publicPosts::get)
                    .filter(Objects::nonNull)
                    .toList();
            String next = hasMore && !visible.isEmpty()
                    ? String.valueOf(visible.get(visible.size() - 1).getContributionId())
                    : null;
            return PageResult.of(items, next, hasMore && next != null);
        } catch (RuntimeException e) {
            log.warn("public collaboration series posts query failed, seriesId={}", seriesId, e);
            return PageResult.<PostBriefDTO>empty()
                    .withDiagnostic("representativeItemsUnavailable", true);
        }
    }

    private List<CommunitySpaceDTO.PublicUpdateDTO> publicUpdates(List<PostBriefDTO> posts,
                                                                  List<String> degraded) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Map<String, CommunitySpaceDTO.PublicUpdateDTO> dedup = new LinkedHashMap<>();
        for (PostBriefDTO post : posts) {
            try {
                List<PublicPostUpdateDTO> postUpdates = postFacade.listPublicUpdates(post.getId(), 5);
                for (PublicPostUpdateDTO update : postUpdates == null ? List.<PublicPostUpdateDTO>of() : postUpdates) {
                    if (update == null || update.getResultVersion() == null || !StringUtils.hasText(update.getPublicUpdateSummary())) {
                        continue;
                    }
                    String eventId = "POST_VERSION:" + post.getId() + ":" + update.getResultVersion();
                    dedup.putIfAbsent(eventId, CommunitySpaceDTO.PublicUpdateDTO.builder()
                            .eventId(eventId)
                            .dedupKey(eventId)
                            .sourceType("POST")
                            .sourceId(String.valueOf(post.getId()))
                            .title(post.getTitle())
                            .summary(update.getPublicUpdateSummary())
                            .impactScope(update.getImpactScope())
                            .targetPath("/post/" + post.getId())
                            .resultVersion(update.getResultVersion())
                            .updatedAt(update.getCreateTime())
                            .build());
                }
            } catch (RuntimeException e) {
                degraded.add("latestUpdates");
                log.warn("public post update query failed, postId={}", post.getId(), e);
            }
        }
        return dedup.values().stream()
                .sorted(Comparator.comparing(
                        CommunitySpaceDTO.PublicUpdateDTO::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_AUXILIARY_ITEMS)
                .toList();
    }

    private List<CommunitySpaceRows.NeedRow> publicNeeds(String spaceType,
                                                         Long spaceId,
                                                         Collection<Long> postIds,
                                                         List<String> degraded) {
        if (spaceId == null || spaceId <= 0) {
            return List.of();
        }
        try {
            List<CommunitySpaceRows.NeedRow> rows = new ArrayList<>();
            List<CommunitySpaceRows.NeedRow> found = switch (spaceType) {
                case "TOPIC" -> spaceMapper.listPublicNeeds(
                        "TOPIC", spaceId, null, MAX_AUXILIARY_ITEMS);
                case "SERIES" -> spaceMapper.listPublicNeedsByCollaborationSeries(
                        spaceId, null, MAX_AUXILIARY_ITEMS);
                case "COLLECTION" -> postIds == null || postIds.isEmpty()
                        ? List.of()
                        : spaceMapper.listPublicNeedsByPosts(
                                new LinkedHashSet<>(postIds), null, MAX_AUXILIARY_ITEMS);
                default -> List.of();
            };
            if (found != null) {
                rows.addAll(found);
            }
            return rows.stream()
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.collectingAndThen(
                            java.util.stream.Collectors.toMap(
                                    CommunitySpaceRows.NeedRow::getId,
                                    row -> row,
                                    (left, right) -> left,
                                    LinkedHashMap::new),
                            map -> map.values().stream().limit(MAX_AUXILIARY_ITEMS).toList()));
        } catch (RuntimeException e) {
            degraded.add("relatedNeeds");
            log.warn("public related needs query failed, spaceType={} spaceId={}",
                    spaceType, spaceId, e);
            return List.of();
        }
    }

    private List<CommunitySpaceRows.ContributionRow> publicContributions(
            String spaceType,
            Long spaceId,
            Collection<Long> postIds,
            List<String> degraded) {
        if (!"SERIES".equals(spaceType) && (postIds == null || postIds.isEmpty())) {
            return List.of();
        }
        try {
            List<CommunitySpaceRows.ContributionRow> rows = "SERIES".equals(spaceType)
                    ? spaceMapper.listPublicSeriesContributions(
                            spaceId, null, MAX_AUXILIARY_ITEMS)
                    : spaceMapper.listPublicContributions(
                            new LinkedHashSet<>(postIds), MAX_AUXILIARY_ITEMS);
            return rows == null ? List.of() : rows.stream()
                    .filter(Objects::nonNull)
                    .limit(MAX_AUXILIARY_ITEMS)
                    .toList();
        } catch (RuntimeException e) {
            degraded.add("relatedContributions");
            log.warn("public related contributions query failed", e);
            return List.of();
        }
    }

    private CommunitySpaceRows.CollaborationSeriesRow requirePublicCollaborationSeries(Long seriesId) {
        if (seriesId == null || seriesId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        CommunitySpaceRows.CollaborationSeriesRow series =
                spaceMapper.selectPublicCollaborationSeries(seriesId);
        if (series == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return series;
    }

    private void markRepresentativeItemsDegraded(PageResult<PostBriefDTO> postPage,
                                                  List<String> degraded) {
        if (postPage == null
                || postPage.getDiagnostics() != null
                && Boolean.TRUE.equals(postPage.getDiagnostics().get(
                        "representativeItemsUnavailable"))) {
            degraded.add("representativeItems");
        }
    }

    private long publicMaintenanceCount(Collection<Long> postIds, List<String> degraded) {
        if (postIds == null || postIds.isEmpty()) {
            return 0L;
        }
        try {
            CommunitySpaceRows.MaintenanceSummaryRow row = spaceMapper.publicMaintenanceForPosts(
                    new LinkedHashSet<>(postIds));
            return row == null || row.getPublicMaintenanceCount() == null
                    ? 0L : Math.max(0L, row.getPublicMaintenanceCount());
        } catch (RuntimeException e) {
            degraded.add("governanceSummary");
            log.warn("public maintenance summary query failed", e);
            return 0L;
        }
    }

    private KnowledgeAssetOverviewDTO publicKnowledge(String spaceType,
                                                       Long spaceId,
                                                       List<Long> postIds,
                                                       List<String> degraded) {
        try {
            Long seedPostId = postIds.isEmpty() ? null : postIds.get(0);
            Long topicId = "TOPIC".equals(spaceType) ? spaceId : null;
            if (seedPostId == null && topicId == null) {
                return null;
            }
            KnowledgeRelationGraphDTO graph = knowledgeRelationService.explore(
                    seedPostId, null, topicId, null, Math.min(MAX_AUXILIARY_ITEMS, 8));
            List<ContentSeriesDTO> series = contentSeriesService.listPublicByPostIds(
                    postIds, Math.min(MAX_AUXILIARY_ITEMS, 8));
            return knowledgeRelationService.aggregatePublicKnowledge(
                    graph, series, null, Math.min(MAX_AUXILIARY_ITEMS, 8));
        } catch (RuntimeException e) {
            degraded.add("knowledge");
            log.warn("public knowledge projection failed, spaceType={} spaceId={}", spaceType, spaceId, e);
            return null;
        }
    }

    private DiscoveryMapDTO publicDiscovery(List<String> degraded) {
        try {
            return discoveryMapService.getPublicMap(5, 8);
        } catch (RuntimeException e) {
            degraded.add("discovery");
            log.warn("public discovery projection failed", e);
            return null;
        }
    }

    private CommunitySpaceDTO.RelatedNeedDTO toNeed(CommunitySpaceRows.NeedRow row) {
        if (row == null || row.getId() == null || !StringUtils.hasText(row.getTitle())) {
            return null;
        }
        return CommunitySpaceDTO.RelatedNeedDTO.builder()
                .id(row.getId())
                .sourceType(row.getSourceType())
                .sourceRefId(row.getSourceRefId())
                .contentFormat(row.getContentFormat())
                .title(row.getTitle())
                .description(row.getDescription())
                .status(row.getStatus())
                .updateTime(row.getUpdateTime())
                .targetPath("/collaboration/needs/" + row.getId())
                .build();
    }

    private CommunitySpaceDTO.RelatedContributionDTO toContribution(CommunitySpaceRows.ContributionRow row) {
        if (row == null || row.getContributionId() == null || row.getPostId() == null) {
            return null;
        }
        return CommunitySpaceDTO.RelatedContributionDTO.builder()
                .contributionId(row.getContributionId())
                .postId(row.getPostId())
                .contributorUid(row.getContributorUid())
                .contributionType(row.getContributionType())
                .title(row.getTitle())
                .targetPath("/post/" + row.getPostId())
                .createTime(row.getCreateTime())
                .build();
    }

    private int pageSize(int size) {
        return Math.max(1, Math.min(size <= 0 ? 10 : size, MAX_PAGE_SIZE));
    }

    private String encodePath(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
