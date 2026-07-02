package com.offerlab.community.search.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.post.api.PublicContentFilter;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostCounterDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostExtensionMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostExtensionPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import com.offerlab.community.post.infrastructure.persistence.po.TagPO;
import com.offerlab.community.post.infrastructure.persistence.projection.PostTagView;
import com.offerlab.community.search.api.SearchFacade;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchFacadeImpl implements SearchFacade {

    private static final int SUMMARY_LEN = 120;
    private static final int MYSQL_FALLBACK_MAX_SCAN = 200;
    private static final List<String> FALLBACK_HOT = List.of("Java", "Spring", "Redis", "Kafka", "架构复盘", "踩坑记录");

    private final PostMapper postMapper;
    private final PostExtensionMapper extensionMapper;
    private final TagMapper tagMapper;
    private final ObjectMapper objectMapper;
    private final ElasticsearchHttpClient elasticsearch;
    private final PostSearchIndexer postSearchIndexer;
    private final PostFacade postFacade;
    private final UserFacade userFacade;
    private final SearchAnalyticsService searchAnalyticsService;
    private final MigrationCheckService migrationCheckService;

    @Override
    public PageResult<PostBriefDTO> searchPosts(String keyword, String company, String position,
                                                Integer type, String sort, String cursor, int size) {
        return searchPosts(keyword, company, position, type, sort, cursor, size, false);
    }

    @Override
    public PageResult<PostBriefDTO> searchPosts(String keyword, String company, String position,
                                                Integer type, String sort, String cursor, int size,
                                                boolean includeTestData) {
        int limit = Math.min(size <= 0 ? 20 : size, 50);
        String normalizedSort = normalizeSort(sort);
        boolean firstPage = parseCursor(cursor) <= 0;
        PageResult<PostBriefDTO> result;
        if (!"hot".equals(normalizedSort) && postSearchIndexer.ensurePostIndex()) {
            Optional<ElasticsearchSearchPage> esResult = searchByElasticsearch(keyword, company, position, type,
                    normalizedSort, cursor, limit, includeTestData);
            if (esResult.isPresent()) {
                ElasticsearchSearchPage esPage = esResult.get();
                result = withSearchMetadata(esPage.page(), "elasticsearch", false, null, esPage.scanLimit(),
                        includeTestData, keyword, type);
                boolean emptyFirstPage = firstPage && isEmptyPage(result);
                boolean sparseAfterVisibilityFilter = isSparseAfterVisibilityFiltering(esPage, limit);
                if (emptyFirstPage || sparseAfterVisibilityFilter) {
                    PageResult<PostBriefDTO> mysqlFallback = searchByMysql(keyword, company, position, type,
                            normalizedSort, cursor, limit, includeTestData);
                    if (shouldUseMysqlFallback(result, mysqlFallback)) {
                        result = withSearchMetadata(mysqlFallback, "mysql", true,
                                emptyFirstPage ? "elasticsearch_empty" : "elasticsearch_visibility_filtered",
                                fallbackScanLimit(limit), includeTestData, keyword, type);
                    }
                }
                searchAnalyticsService.recordSearch(keyword, company, position, type, normalizedSort, result.getItems().size(), firstPage);
                return result;
            }
        }
        result = searchByMysql(keyword, company, position, type, normalizedSort, cursor, limit, includeTestData);
        result = withSearchMetadata(result, "mysql", !"hot".equals(normalizedSort),
                "hot".equals(normalizedSort) ? "hot_sort_mysql" : "elasticsearch_unavailable",
                fallbackScanLimit(limit), includeTestData, keyword, type);
        searchAnalyticsService.recordSearch(keyword, company, position, type, normalizedSort, result.getItems().size(), firstPage);
        return result;
    }

    private PageResult<PostBriefDTO> withSearchMetadata(PageResult<PostBriefDTO> result, String source,
                                                        boolean degraded, String fallbackReason, int scanLimit,
                                                        boolean includeTestData, String keyword, Integer type) {
        boolean syntheticQuery = PublicContentFilter.isSyntheticText(keyword);
        result.withMetadata(source, degraded, fallbackReason, scanLimit)
                .withDiagnostic("includeTestData", includeTestData)
                .withDiagnostic("testDataFilterActive", !includeTestData)
                .withDiagnostic("syntheticQuery", syntheticQuery)
                .withDiagnostic("type", type);
        if (isEmptyPage(result) && syntheticQuery && !includeTestData) {
            result.withDiagnostic("emptyReason", "test_data_filtered_unless_includeTestData");
        } else if (isEmptyPage(result) && type != null) {
            result.withDiagnostic("emptyReason", "type_or_filter_no_match");
        }
        return result;
    }

    private boolean isEmptyPage(PageResult<PostBriefDTO> page) {
        return page == null || page.getItems() == null || page.getItems().isEmpty();
    }

    private boolean isSparseAfterVisibilityFiltering(ElasticsearchSearchPage esPage, int limit) {
        return esPage != null
                && esPage.rawHitCount() >= esPage.scanLimit()
                && itemCount(esPage.page()) < limit;
    }

    private boolean shouldUseMysqlFallback(PageResult<PostBriefDTO> esPage, PageResult<PostBriefDTO> mysqlFallback) {
        if (isEmptyPage(mysqlFallback)) {
            return false;
        }
        if (isEmptyPage(esPage)) {
            return true;
        }
        return itemCount(mysqlFallback) > itemCount(esPage) || Boolean.TRUE.equals(mysqlFallback.getHasMore());
    }

    private int itemCount(PageResult<PostBriefDTO> page) {
        return page == null || page.getItems() == null ? 0 : page.getItems().size();
    }

    @Override
    public List<String> suggest(String prefix, int size) {
        int limit = Math.min(size <= 0 ? 10 : size, 20);
        String p = clean(prefix);
        if (p.isBlank()) {
            return getHotKeywords(limit);
        }
        if (postSearchIndexer.ensurePostIndex()) {
            Optional<List<String>> es = suggestByElasticsearch(p, limit);
            if (es.isPresent()) {
                return es.get();
            }
        }
        List<String> mysqlSuggestions = suggestByMysql(p, limit);
        if (!mysqlSuggestions.isEmpty()) {
            return mysqlSuggestions;
        }
        return FALLBACK_HOT.stream()
                .filter(item -> item.toLowerCase().contains(p.toLowerCase()))
                .limit(limit)
                .toList();
    }

    @Override
    public List<String> getHotKeywords(int size) {
        int limit = Math.min(size <= 0 ? 10 : size, 20);
        Set<String> result = new LinkedHashSet<>();
        activeTags().stream()
                .sorted(Comparator.comparing(TagPO::getUseCount, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(TagPO::getTagName)
                .filter(name -> name != null && !name.isBlank() && !PublicContentFilter.isSyntheticText(name))
                .limit(limit)
                .forEach(result::add);
        LocalDateTime since = LocalDateTime.now().minusDays(90);
        postMapper.countCompanies(since, limit).forEach(row -> addName(result, row.get("name")));
        postMapper.countPositions(since, limit).forEach(row -> addName(result, row.get("name")));
        FALLBACK_HOT.forEach(result::add);
        return result.stream().limit(limit).toList();
    }

    private Optional<ElasticsearchSearchPage> searchByElasticsearch(String keyword, String company, String position,
                                                                    Integer type, String sort, String cursor, int limit,
                                                                    boolean includeTestData) {
        int scanLimit = elasticsearchScanLimit(limit);
        Map<String, Object> body = new HashMap<>();
        body.put("query", buildEsQuery(keyword, company, position, type, cursor));
        body.put("sort", buildEsSort(sort));
        body.put("highlight", Map.of(
                "pre_tags", List.of("<em>"),
                "post_tags", List.of("</em>"),
                "fields", Map.of(
                        "title", Map.of("number_of_fragments", 0),
                        "content", Map.of("fragment_size", 150, "number_of_fragments", 1)
                )
        ));
        body.put("size", scanLimit);
        return elasticsearch.search(elasticsearch.postIndex(), body)
                .map(json -> toElasticsearchPage(json, limit, scanLimit, includeTestData));
    }

    private List<Object> buildEsSort(String sort) {
        if ("relevance".equals(sort)) {
            return List.of(
                    Map.of("_score", Map.of("order", "desc")),
                    Map.of("createTime", Map.of("order", "desc")),
                    Map.of("id", Map.of("order", "asc"))
            );
        }
        return List.of(Map.of("createTime", Map.of("order", "desc")), Map.of("id", Map.of("order", "asc")));
    }

    private Map<String, Object> buildEsQuery(String keyword, String company, String position, Integer type, String cursor) {
        List<Object> must = new ArrayList<>();
        List<Object> filter = new ArrayList<>();
        String kw = clean(keyword);
        if (kw.isBlank()) {
            must.add(Map.of("match_all", Map.of()));
        } else {
            List<Object> should = new ArrayList<>();
            should.add(Map.of("multi_match", Map.of(
                    "query", kw,
                    "fields", List.of("title^3", "content", "summary^2", "company^2", "position", "scenario^2",
                            "techStacks^2", "tagNames", "tagSynonyms^2", "tagSearchTerms^2"),
                    "type", "best_fields",
                    "operator", "or"
            )));
            should.add(Map.of("match_phrase", Map.of("title", kw)));
            should.add(Map.of("match_phrase", Map.of("content", kw)));
            should.add(Map.of("match_phrase", Map.of("summary", kw)));
            should.add(Map.of("term", Map.of("id", kw)));
            parsePostIdKeyword(kw).ifPresent(postId -> should.add(Map.of("term", Map.of("postId", postId))));
            must.add(Map.of("bool", Map.of("should", should, "minimum_should_match", 1)));
        }
        filter.add(Map.of("term", Map.of("status", "published")));
        filter.add(Map.of("term", Map.of("visibility", 1)));
        if (type != null) {
            filter.add(Map.of("term", Map.of("type", type)));
        }
        if (!clean(company).isBlank()) {
            filter.add(Map.of("bool", Map.of("should", List.of(
                    Map.of("match_phrase", Map.of("company", clean(company))),
                    Map.of("match_phrase", Map.of("techStacks", clean(company))),
                    Map.of("match_phrase", Map.of("tagSynonyms", clean(company))),
                    Map.of("match_phrase", Map.of("tagSearchTerms", clean(company)))
            ), "minimum_should_match", 1)));
        }
        if (!clean(position).isBlank()) {
            filter.add(Map.of("bool", Map.of("should", List.of(
                    Map.of("term", Map.of("position", clean(position))),
                    Map.of("match_phrase", Map.of("scenario", clean(position))),
                    Map.of("match_phrase", Map.of("tagSynonyms", clean(position))),
                    Map.of("match_phrase", Map.of("tagSearchTerms", clean(position)))
            ), "minimum_should_match", 1)));
        }
        long c = parseCursor(cursor);
        if (c > 0) {
            filter.add(Map.of("range", Map.of("createTime", Map.of("lt", c))));
        }
        return Map.of("bool", Map.of("must", must, "filter", filter));
    }

    private ElasticsearchSearchPage toElasticsearchPage(JsonNode json, int limit, int scanLimit, boolean includeTestData) {
        JsonNode hits = json.path("hits").path("hits");
        if (!hits.isArray() || hits.isEmpty()) {
            return new ElasticsearchSearchPage(PageResult.empty(), 0, scanLimit);
        }
        List<PostBriefDTO> items = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            String summary = source.path("summary").asText("");
            items.add(PostBriefDTO.builder()
                    .id(source.path("id").asLong())
                    .authorId(source.path("authorId").asLong())
                    .postType(source.path("type").asInt())
                    .title(source.path("title").asText(""))
                    .summary(summary)
                    .highlightTitle(firstHighlight(hit, "title").orElse(null))
                    .highlightSummary(firstHighlight(hit, "content").orElse(null))
                    .coverUrl(source.path("coverUrl").asText(null))
                    .extJson(source.path("extJson").asText(null))
                    .tags(toTags(source.path("tags")))
                    .createTime(toLocalDateTime(source.path("createTime").asLong(0L)))
                    .build());
        }
        List<PostBriefDTO> visibleItems = filterVisibleSearchResults(items, includeTestData);
        int syntheticFiltered = includeTestData ? 0 : (int) items.stream()
                .filter(PublicContentFilter::isSyntheticPost)
                .count();
        boolean hasMore = visibleItems.size() > limit;
        List<PostBriefDTO> pageItems = hasMore ? visibleItems.subList(0, limit) : visibleItems;
        PostBriefDTO cursorItem = pageItems.isEmpty() ? null : pageItems.get(pageItems.size() - 1);
        String next = hasMore && cursorItem.getCreateTime() != null
                ? String.valueOf(cursorItem.getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli())
                : null;
        PageResult<PostBriefDTO> page = PageResult.of(pageItems, next, hasMore)
                .withDiagnostic("rawHits", items.size())
                .withDiagnostic("visibleHits", visibleItems.size())
                .withDiagnostic("syntheticFiltered", syntheticFiltered);
        return new ElasticsearchSearchPage(page, items.size(), scanLimit);
    }

    private List<PostBriefDTO> filterVisibleSearchResults(List<PostBriefDTO> esItems, boolean includeTestData) {
        if (esItems == null || esItems.isEmpty()) {
            return List.of();
        }
        Map<Long, PostBriefDTO> visibleById = postFacade.batchGetPosts(esItems.stream()
                .map(PostBriefDTO::getId)
                .toList(), null, includeTestData);
        List<PostBriefDTO> visible = new ArrayList<>();
        for (PostBriefDTO esItem : esItems) {
            PostBriefDTO current = visibleById.get(esItem.getId());
            if (current == null) {
                log.debug("stale elasticsearch post filtered: postId={}", esItem.getId());
                continue;
            }
            current.setHighlightTitle(esItem.getHighlightTitle());
            current.setHighlightSummary(esItem.getHighlightSummary());
            visible.add(current);
        }
        return visible;
    }

    private Optional<String> firstHighlight(JsonNode hit, String field) {
        JsonNode values = hit.path("highlight").path(field);
        if (values.isArray() && !values.isEmpty()) {
            return Optional.ofNullable(values.get(0).asText(null));
        }
        return Optional.empty();
    }

    private Optional<List<String>> suggestByElasticsearch(String prefix, int limit) {
        Map<String, Object> body = Map.of(
                "query", Map.of("bool", Map.of(
                        "filter", List.of(
                                Map.of("term", Map.of("status", "published")),
                                Map.of("term", Map.of("visibility", 1))
                        ),
                        "should", List.of(
                                Map.of("match_phrase_prefix", Map.of("title", prefix)),
                                Map.of("match_phrase_prefix", Map.of("company", prefix)),
                                Map.of("prefix", Map.of("position", prefix)),
                                Map.of("match_phrase_prefix", Map.of("scenario", prefix)),
                                Map.of("match_phrase_prefix", Map.of("techStacks", prefix)),
                                Map.of("match_phrase_prefix", Map.of("tagSynonyms", prefix)),
                                Map.of("match_phrase_prefix", Map.of("tagSearchTerms", prefix))
                        ),
                        "minimum_should_match", 1
                )),
                "_source", List.of("id", "postId", "title", "company", "position", "scenario", "techStacks", "tagNames", "tagSynonyms", "tagSearchTerms"),
                "size", limit
        );
        return elasticsearch.search(elasticsearch.postIndex(), body).map(json -> {
            Set<String> result = new LinkedHashSet<>();
            JsonNode hits = json.path("hits").path("hits");
            Map<Long, JsonNode> visibleCandidateSources = visibleSuggestionSources(hits);
            if (visibleCandidateSources.isEmpty()) {
                return List.<String>of();
            }
            for (JsonNode source : visibleCandidateSources.values()) {
                if (PublicContentFilter.isSyntheticText(source.path("title").asText(null))
                        || PublicContentFilter.isSyntheticText(source.path("company").asText(null))
                        || PublicContentFilter.isSyntheticText(source.path("position").asText(null))) {
                    continue;
                }
                addIfMatches(result, source.path("company").asText(null), prefix);
                addIfMatches(result, source.path("position").asText(null), prefix);
                addIfMatches(result, source.path("scenario").asText(null), prefix);
                addArrayMatches(result, source.path("techStacks"), prefix);
                addArrayMatches(result, source.path("tagNames"), prefix);
                addArrayMatches(result, source.path("tagSynonyms"), prefix);
                addArrayMatches(result, source.path("tagSearchTerms"), prefix);
                addIfMatches(result, source.path("title").asText(null), prefix);
            }
            return result.stream().limit(limit).toList();
        });
    }

    private Map<Long, JsonNode> visibleSuggestionSources(JsonNode hits) {
        if (!hits.isArray() || hits.isEmpty()) {
            return Map.of();
        }
        Map<Long, JsonNode> sourcesById = new java.util.LinkedHashMap<>();
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            Long postId = suggestionPostId(source);
            if (postId != null && postId > 0) {
                sourcesById.putIfAbsent(postId, source);
            }
        }
        if (sourcesById.isEmpty()) {
            return Map.of();
        }
        Map<Long, PostBriefDTO> visibleById = postFacade.batchGetPosts(sourcesById.keySet(), null, false);
        Map<Long, JsonNode> visibleSources = new java.util.LinkedHashMap<>();
        for (Map.Entry<Long, JsonNode> entry : sourcesById.entrySet()) {
            if (visibleById.containsKey(entry.getKey())) {
                visibleSources.put(entry.getKey(), entry.getValue());
            } else {
                log.debug("stale elasticsearch suggestion filtered: postId={}", entry.getKey());
            }
        }
        return visibleSources;
    }

    private Long suggestionPostId(JsonNode source) {
        JsonNode postId = source.path("postId");
        if (postId.canConvertToLong()) {
            return postId.asLong();
        }
        return parsePostIdKeyword(source.path("id").asText(null)).orElse(null);
    }

    private List<String> suggestByMysql(String prefix, int limit) {
        String p = clean(prefix);
        if (p.isBlank()) {
            return List.of();
        }
        List<PostPO> candidates = migrationCheckService.tagGovernanceReady()
                ? postMapper.suggestPublicPostsFallback(p, fallbackScanLimit(limit))
                : postMapper.suggestPublicPostsFallbackCompat(p, fallbackScanLimit(limit));
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<Long, String> extByPostId = loadExtJson(candidates.stream().map(PostPO::getId).toList());
        Map<Long, List<TagDTO>> tags = tagsByPostIds(candidates.stream().map(PostPO::getId).toList());
        Set<String> result = new LinkedHashSet<>();
        for (PostPO post : candidates) {
            JsonNode ext = parseExt(extByPostId.get(post.getId()));
            if (PublicContentFilter.isSyntheticText(post.getTitle())
                    || PublicContentFilter.isSyntheticText(post.getContent())
                    || PublicContentFilter.isSyntheticText(extByPostId.get(post.getId()))) {
                continue;
            }
            addIfMatches(result, ext.path("company").asText(null), p);
            addIfMatches(result, ext.path("position").asText(null), p);
            addIfMatches(result, ext.path("scenario").asText(null), p);
            addArrayMatches(result, ext.path("techStacks"), p);
            addTagMatches(result, tags.getOrDefault(post.getId(), List.of()), p);
            addIfMatches(result, post.getTitle(), p);
            if (result.size() >= limit) {
                break;
            }
        }
        return result.stream().limit(limit).toList();
    }

    private PageResult<PostBriefDTO> searchByMysql(String keyword, String company, String position,
                                                   Integer type, String sort, String cursor, int limit,
                                                   boolean includeTestData) {
        long c = parseCursor(cursor);
        String kw = clean(keyword);
        Long keywordPostId = parsePostIdKeyword(kw).orElse(null);
        LocalDateTime cursorTime = c > 0 ? LocalDateTime.ofInstant(Instant.ofEpochMilli(c), ZoneOffset.UTC) : null;
        List<PostPO> candidates = migrationCheckService.tagGovernanceReady()
                ? postMapper.searchPublicPostsFallback(
                        blankToNull(kw),
                        keywordPostId,
                        blankToNull(clean(company)),
                        blankToNull(clean(position)),
                        type,
                        cursorTime,
                        fallbackScanLimit(limit))
                : postMapper.searchPublicPostsFallbackCompat(
                        blankToNull(kw),
                        keywordPostId,
                        blankToNull(clean(company)),
                        blankToNull(clean(position)),
                        type,
                        cursorTime,
                        fallbackScanLimit(limit));
        if (candidates.isEmpty()) {
            return PageResult.empty();
        }
        Map<Long, String> extByPostId = loadExtJson(candidates.stream().map(PostPO::getId).toList());
        List<PostPO> filtered = candidates;
        if (filtered.isEmpty()) {
            return PageResult.empty();
        }
        boolean hasMore = filtered.size() > limit;
        Map<Long, List<TagDTO>> tags = tagsByPostIds(filtered.stream().map(PostPO::getId).toList());
        List<PostBriefDTO> items = filtered.stream().map(p -> PostBriefDTO.builder()
                .id(p.getId())
                .authorId(p.getAuthorId())
                .postType(p.getPostType())
                .title(p.getTitle())
                .summary(summary(p.getContent()))
                .coverUrl(p.getCoverUrl())
                .extJson(extByPostId.get(p.getId()))
                .tags(tags.getOrDefault(p.getId(), List.of()))
                .createTime(p.getCreateTime())
                .build()).toList();
        int syntheticFiltered = includeTestData ? 0 : (int) items.stream()
                .filter(PublicContentFilter::isSyntheticPost)
                .count();
        items = filterVisibleSearchResults(items, includeTestData);
        if ("hot".equals(sort)) {
            items = items.stream()
                    .sorted(Comparator.comparingDouble(this::hotScore).reversed()
                            .thenComparing(PostBriefDTO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        }
        items = items.stream().limit(limit).toList();
        String next = hasMore && !items.isEmpty()
                ? String.valueOf(items.get(items.size() - 1).getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli())
                : null;
        return PageResult.of(items, next, hasMore)
                .withDiagnostic("rawHits", candidates.size())
                .withDiagnostic("visibleHits", items.size())
                .withDiagnostic("syntheticFiltered", syntheticFiltered);
    }

    private List<PostBriefDTO> enrich(List<PostBriefDTO> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        Map<Long, PostCounterDTO> counters = postFacade.batchGetCounters(posts.stream().map(PostBriefDTO::getId).toList());
        Map<Long, UserBriefDTO> authors = userFacade.batchGetUserBriefs(posts.stream()
                .map(PostBriefDTO::getAuthorId)
                .collect(Collectors.toSet()));
        posts.forEach(p -> {
            p.setCounter(counters.getOrDefault(p.getId(), emptyCounter(p.getId())));
            p.setAuthor(authors.get(p.getAuthorId()));
        });
        return posts;
    }

    private static PostCounterDTO emptyCounter(Long postId) {
        return PostCounterDTO.builder()
                .postId(postId)
                .viewCount(0L)
                .likeCount(0L)
                .commentCount(0L)
                .favoriteCount(0L)
                .build();
    }

    private int fallbackScanLimit(int limit) {
        return Math.min(Math.max(limit + 1, limit * 2), MYSQL_FALLBACK_MAX_SCAN);
    }

    private int elasticsearchScanLimit(int limit) {
        return Math.min(Math.max(limit + 1, limit * 2), MYSQL_FALLBACK_MAX_SCAN);
    }

    private record ElasticsearchSearchPage(PageResult<PostBriefDTO> page, int rawHitCount, int scanLimit) {
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Map<Long, String> loadExtJson(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return extensionMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(PostExtensionPO::getPostId, PostExtensionPO::getExtJson, (a, b) -> a));
    }

    private Map<Long, List<TagDTO>> tagsByPostIds(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return selectTagsByPostIds(postIds).stream()
                .collect(Collectors.groupingBy(PostTagView::getPostId,
                        Collectors.mapping(this::toTagDto, Collectors.toList())));
    }

    private List<TagPO> activeTags() {
        return migrationCheckService.tagGovernanceReady()
                ? tagMapper.selectActiveTags()
                : tagMapper.selectActiveTagsCompat();
    }

    private List<PostTagView> selectTagsByPostIds(Collection<Long> postIds) {
        return migrationCheckService.tagGovernanceReady()
                ? tagMapper.selectTagsByPostIds(postIds)
                : tagMapper.selectTagsByPostIdsCompat(postIds);
    }

    private TagDTO toTagDto(PostTagView tag) {
        return TagDTO.builder()
                .id(tag.getId())
                .name(tag.getTagName())
                .slug(String.valueOf(tag.getId()))
                .category(toCategory(tag.getTagType()))
                .tagType(tag.getTagType())
                .useCount(tag.getUseCount())
                .official(tag.getIsOfficial() != null && tag.getIsOfficial() == 1)
                .synonyms(parseSynonyms(tag.getSynonyms()))
                .build();
    }

    private List<TagDTO> toTags(JsonNode tags) {
        if (!tags.isArray()) {
            return List.of();
        }
        List<TagDTO> result = new ArrayList<>();
        for (JsonNode tag : tags) {
            result.add(TagDTO.builder()
                    .id(tag.path("id").asLong())
                    .name(tag.path("name").asText(""))
                    .slug(tag.path("slug").asText(""))
                    .category(tag.path("category").asText("custom"))
                    .tagType(tag.path("tagType").isMissingNode() ? null : tag.path("tagType").asInt())
                    .useCount(tag.path("useCount").asLong(0L))
                    .official(tag.path("official").asBoolean(false))
                    .synonyms(textArray(tag.path("synonyms")))
                    .build());
        }
        return result;
    }

    private JsonNode parseExt(String extJson) {
        if (extJson == null || extJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(extJson);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private void addIfMatches(Set<String> result, String value, String prefix) {
        if (value != null && !value.isBlank() && value.toLowerCase().contains(prefix.toLowerCase())) {
            result.add(value);
        }
    }

    private void addArrayMatches(Set<String> result, JsonNode values, String prefix) {
        if (values == null || !values.isArray()) {
            addIfMatches(result, values == null ? null : values.asText(null), prefix);
            return;
        }
        for (JsonNode value : values) {
            addIfMatches(result, value.asText(null), prefix);
        }
    }

    private void addTagMatches(Set<String> result, List<TagDTO> tags, String prefix) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        for (TagDTO tag : tags) {
            addIfMatches(result, tag.getName(), prefix);
            if (tag.getSynonyms() != null) {
                tag.getSynonyms().forEach(value -> addIfMatches(result, value, prefix));
            }
        }
    }

    private static List<String> parseSynonyms(String synonyms) {
        if (synonyms == null || synonyms.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String raw : synonyms.split("[,\\uFF0C\\u3001/;\\uFF1B\\r\\n]+")) {
            String value = raw == null ? "" : raw.trim();
            if (!value.isBlank() && !result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private static List<String> textArray(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            String value = node.asText("").trim();
            return value.isBlank() ? List.of() : List.of(value);
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank() && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private long parseCursor(String c) {
        if (c == null || c.isBlank()) return 0L;
        try {
            return Long.parseLong(c);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Optional<Long> parsePostIdKeyword(String keyword) {
        String value = clean(keyword);
        if (value.isBlank() || !value.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String normalizeSort(String sort) {
        String value = clean(sort).toLowerCase();
        if ("hot".equals(value) || "latest".equals(value) || "relevance".equals(value)) {
            return value;
        }
        return "relevance";
    }

    private void addName(Set<String> result, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isBlank() && !PublicContentFilter.isSyntheticText(text)) {
            result.add(text);
        }
    }

    private double hotScore(PostBriefDTO post) {
        PostCounterDTO counter = post.getCounter();
        double heat = 0D;
        if (counter != null) {
            heat += safe(counter.getLikeCount()) * 3D;
            heat += safe(counter.getFavoriteCount()) * 4D;
            heat += safe(counter.getCommentCount()) * 5D;
            heat += safe(counter.getViewCount()) * 0.2D;
        }
        double recency = post.getCreateTime() == null
                ? 0D
                : Math.max(0D, 72D - Duration.between(post.getCreateTime(), LocalDateTime.now()).toHours());
        return heat + recency;
    }

    private static long safe(Long value) {
        return value == null ? 0L : value;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static LocalDateTime toLocalDateTime(long epochMillis) {
        if (epochMillis <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
    }

    private static String summary(String content) {
        if (content == null) return "";
        String s = content.replaceAll("[#*`>\\[\\]()_!~\\-]+", " ").trim();
        return s.length() <= SUMMARY_LEN ? s : s.substring(0, SUMMARY_LEN) + "...";
    }

    private static String toCategory(Integer tagType) {
        if (tagType == null) return "custom";
        return switch (tagType) {
            case 1 -> "tech";
            case 2 -> "company";
            case 3 -> "position";
            default -> "custom";
        };
    }
}
