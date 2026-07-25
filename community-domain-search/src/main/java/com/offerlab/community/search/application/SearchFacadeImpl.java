package com.offerlab.community.search.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.utils.CursorUtils;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.post.api.PublicContentFilter;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostCounterDTO;
import com.offerlab.community.post.api.dto.PostTrustSignalsDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostExtensionMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostExtensionPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import com.offerlab.community.post.infrastructure.persistence.po.TagPO;
import com.offerlab.community.post.infrastructure.persistence.projection.PostTagView;
import com.offerlab.community.search.api.SearchFacade;
import com.offerlab.community.search.api.dto.SearchTrustFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    private static final String SEARCH_CURSOR_VERSION = "sr1";

    private final PostMapper postMapper;
    private final PostExtensionMapper extensionMapper;
    private final TagMapper tagMapper;
    private final ObjectMapper objectMapper;
    private final ElasticsearchHttpClient elasticsearch;
    private final PostSearchIndexer postSearchIndexer;
    private final PostFacade postFacade;
    private final SearchAnalyticsService searchAnalyticsService;
    private final MigrationCheckService migrationCheckService;

    @Override
    public PageResult<PostBriefDTO> searchPosts(String keyword, String company, String position,
                                                Integer type, String sort, String cursor, int size) {
        return searchPosts(keyword, company, position, type, null, sort, cursor, size, false);
    }

    @Override
    public PageResult<PostBriefDTO> searchPosts(String keyword, String company, String position,
                                                Integer type, String sort, String cursor, int size,
                                                boolean includeTestData) {
        return searchPosts(keyword, company, position, type, null, sort, cursor, size, includeTestData);
    }

    @Override
    public PageResult<PostBriefDTO> searchPosts(String keyword, String company, String position,
                                                Integer type, Integer domain, String sort, String cursor, int size) {
        return searchPosts(keyword, company, position, type, domain, sort, cursor, size, false);
    }

    @Override
    public PageResult<PostBriefDTO> searchPosts(String keyword, String company, String position,
                                                Integer type, Integer domain, String sort, String cursor, int size,
                                                boolean includeTestData) {
        return searchPosts(keyword, company, position, type, domain, sort, cursor, size, includeTestData,
                SearchTrustFilter.empty());
    }

    @Override
    public PageResult<PostBriefDTO> searchPosts(String keyword, String company, String position,
                                                Integer type, Integer domain, String sort, String cursor, int size,
                                                boolean includeTestData, SearchTrustFilter trustFilter) {
        int limit = Math.min(size <= 0 ? 20 : size, 50);
        String normalizedSort = normalizeSort(sort);
        SearchTrustFilter normalizedTrustFilter = trustFilter == null ? SearchTrustFilter.empty() : trustFilter;
        boolean trustedSort = "trusted".equals(normalizedSort);
        boolean trustConstrained = trustedSort || normalizedTrustFilter.active();
        boolean firstPage = trustedSort ? trustedOffset(cursor) == 0 : !parseSearchCursor(cursor).present();
        if (trustConstrained && !migrationCheckService.trustedDistributionReady()) {
            PageResult<PostBriefDTO> unavailable = withSearchMetadata(
                    PageResult.empty(),
                    "mysql",
                    true,
                    "trusted_distribution_migration_pending",
                    0,
                    includeTestData,
                    keyword,
                    type,
                    domain,
                    normalizedTrustFilter,
                    normalizedSort
            );
            searchAnalyticsService.recordSearch(keyword, company, position, type, normalizedSort, 0, firstPage);
            return unavailable;
        }
        PageResult<PostBriefDTO> result;
        if (trustedSort) {
            result = searchTrustedByMysql(keyword, company, position, type, domain, cursor, limit, includeTestData,
                    normalizedTrustFilter);
            result = withSearchMetadata(result, "mysql", false, null, MYSQL_FALLBACK_MAX_SCAN,
                    includeTestData, keyword, type, domain, normalizedTrustFilter, normalizedSort);
            searchAnalyticsService.recordSearch(keyword, company, position, type, normalizedSort,
                    result.getItems().size(), firstPage);
            return result;
        }
        if (!trustConstrained && !"hot".equals(normalizedSort) && postSearchIndexer.ensurePostIndex()) {
            Optional<ElasticsearchSearchPage> esResult = searchByElasticsearch(keyword, company, position, type, domain,
                    normalizedSort, cursor, limit, includeTestData);
            if (esResult.isPresent()) {
                ElasticsearchSearchPage esPage = esResult.get();
                result = withSearchMetadata(esPage.page(), "elasticsearch", false, null, esPage.scanLimit(),
                        includeTestData, keyword, type, domain, normalizedTrustFilter, normalizedSort);
                boolean emptyFirstPage = firstPage && isEmptyPage(result);
                boolean sparseAfterVisibilityFilter = isSparseAfterVisibilityFiltering(esPage, limit);
                if (emptyFirstPage || sparseAfterVisibilityFilter) {
                    PageResult<PostBriefDTO> mysqlFallback = searchByMysql(keyword, company, position, type, domain,
                            normalizedSort, cursor, limit, includeTestData, normalizedTrustFilter);
                    if (shouldUseMysqlFallback(result, mysqlFallback)) {
                        result = withSearchMetadata(mysqlFallback, "mysql", true,
                                emptyFirstPage ? "elasticsearch_empty" : "elasticsearch_visibility_filtered",
                                fallbackScanLimit(limit), includeTestData, keyword, type, domain,
                                normalizedTrustFilter, normalizedSort);
                    }
                }
                searchAnalyticsService.recordSearch(keyword, company, position, type, normalizedSort, result.getItems().size(), firstPage);
                return result;
            }
        }
        result = searchByMysql(keyword, company, position, type, domain, normalizedSort, cursor, limit,
                includeTestData, normalizedTrustFilter);
        result = withSearchMetadata(result, "mysql", !("hot".equals(normalizedSort) || trustConstrained),
                "hot".equals(normalizedSort) ? "hot_sort_mysql"
                        : trustConstrained ? "trust_filter_mysql"
                        : "elasticsearch_unavailable",
                fallbackScanLimit(limit), includeTestData, keyword, type, domain,
                normalizedTrustFilter, normalizedSort);
        searchAnalyticsService.recordSearch(keyword, company, position, type, normalizedSort, result.getItems().size(), firstPage);
        return result;
    }

    private PageResult<PostBriefDTO> withSearchMetadata(PageResult<PostBriefDTO> result, String source,
                                                        boolean degraded, String fallbackReason, int scanLimit,
                                                        boolean includeTestData, String keyword, Integer type,
                                                        Integer domain, SearchTrustFilter trustFilter, String sort) {
        boolean syntheticQuery = PublicContentFilter.isSyntheticText(keyword);
        attachHitReasons(result, keyword);
        result.withMetadata(source, degraded, fallbackReason, scanLimit)
                .withDiagnostic("includeTestData", includeTestData)
                .withDiagnostic("testDataFilterActive", !includeTestData)
                .withDiagnostic("syntheticQuery", syntheticQuery)
                .withDiagnostic("type", type)
                .withDiagnostic("domain", domain)
                .withDiagnostic("trustProfile", trustFilter == null ? null : trustFilter.trustProfile())
                .withDiagnostic("freshnessStatus", trustFilter == null ? null : trustFilter.freshnessStatus())
                .withDiagnostic("resolved", trustFilter == null ? null : trustFilter.resolved())
                .withDiagnostic("sourceComplete", trustFilter == null ? null : trustFilter.sourceComplete())
                .withDiagnostic("sort", sort)
                .withDiagnostic("hitExplanation", hitExplanation(source));
        if (isEmptyPage(result) && syntheticQuery && !includeTestData) {
            result.withDiagnostic("emptyReason", "test_data_filtered_unless_includeTestData");
        } else if (isEmptyPage(result) && type != null) {
            result.withDiagnostic("emptyReason", "type_or_filter_no_match");
        } else if (isEmptyPage(result)) {
            result.withDiagnostic("emptyReason", "no_public_results_after_visibility_and_governance_filters");
        }
        if (isEmptyPage(result)) {
            result.withDiagnostic("emptyHints", emptyHints(degraded, fallbackReason, includeTestData));
        }
        return result;
    }

    private List<String> emptyHints(boolean degraded, String fallbackReason, boolean includeTestData) {
        List<String> hints = new ArrayList<>();
        hints.add("broaden_keyword_or_filters");
        hints.add("only_public_published_content_is_returned");
        if (!includeTestData) {
            hints.add("test_data_is_filtered");
        }
        if (degraded) {
            hints.add("search_backend_degraded:" + clean(fallbackReason));
        }
        return hints;
    }

    private String hitExplanation(String source) {
        if ("elasticsearch".equals(source)) {
            return "elasticsearch_highlight_only";
        }
        if ("mysql".equals(source)) {
            return "mysql_fallback_no_hit_explanation";
        }
        return "unavailable";
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
        return List.of();
    }

    @Override
    public List<String> getHotKeywords(int size) {
        int limit = Math.min(size <= 0 ? 10 : size, 20);
        Set<String> result = new LinkedHashSet<>();
        hotTags(limit).stream()
                .map(TagPO::getTagName)
                .filter(name -> name != null && !name.isBlank()
                        && !PublicContentFilter.isSyntheticText(name)
                        && !PublicContentFilter.isUnsafeSuggestionText(name))
                .limit(limit)
                .forEach(result::add);
        LocalDateTime since = LocalDateTime.now().minusDays(90);
        postMapper.countCompanies(since, limit).forEach(row -> addName(result, row.get("name")));
        postMapper.countPositions(since, limit).forEach(row -> addName(result, row.get("name")));
        return result.stream().limit(limit).toList();
    }

    private Optional<ElasticsearchSearchPage> searchByElasticsearch(String keyword, String company, String position,
                                                                    Integer type, Integer domain, String sort, String cursor, int limit,
                                                                    boolean includeTestData) {
        int scanLimit = elasticsearchScanLimit(limit);
        Map<String, Object> body = new HashMap<>();
        body.put("query", buildEsQuery(keyword, company, position, type, domain, parseSearchCursor(cursor)));
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
                .map(json -> toElasticsearchPage(json, limit, scanLimit, includeTestData, domain));
    }

    private List<Object> buildEsSort(String sort) {
        if ("relevance".equals(sort)) {
            return List.of(
                    Map.of("_score", Map.of("order", "desc")),
                    Map.of("createTime", Map.of("order", "desc")),
                    Map.of("id", Map.of("order", "desc"))
            );
        }
        return List.of(Map.of("createTime", Map.of("order", "desc")), Map.of("id", Map.of("order", "desc")));
    }

    private Map<String, Object> buildEsQuery(String keyword, String company, String position, Integer type,
                                             Integer domain, SearchCursor cursor) {
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
        if (domain != null) {
            filter.add(Map.of("term", Map.of("domain", domain)));
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
        if (cursor.present()) {
            filter.add(Map.of("bool", Map.of(
                    "should", List.of(
                            Map.of("range", Map.of("createTime", Map.of("lt", cursor.millis()))),
                            Map.of("bool", Map.of("filter", List.of(
                                    Map.of("term", Map.of("createTime", cursor.millis())),
                                    Map.of("range", Map.of("id", Map.of("lt", cursor.id())))
                            )))
                    ),
                    "minimum_should_match", 1
            )));
        }
        return Map.of("bool", Map.of("must", must, "filter", filter));
    }

    private ElasticsearchSearchPage toElasticsearchPage(JsonNode json, int limit, int scanLimit,
                                                        boolean includeTestData, Integer domain) {
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
                    .domain(validDomain(source.path("domain")))
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
        List<PostBriefDTO> visibleItems = filterVisibleSearchResults(items, includeTestData, domain);
        int syntheticFiltered = includeTestData ? 0 : (int) items.stream()
                .filter(PublicContentFilter::isSyntheticPost)
                .count();
        boolean hasMore = visibleItems.size() > limit;
        List<PostBriefDTO> pageItems = hasMore ? visibleItems.subList(0, limit) : visibleItems;
        PostBriefDTO cursorItem = pageItems.isEmpty() ? null : pageItems.get(pageItems.size() - 1);
        String next = hasMore ? searchCursor(cursorItem) : null;
        PageResult<PostBriefDTO> page = PageResult.of(pageItems, next, hasMore)
                .withDiagnostic("rawHits", items.size())
                .withDiagnostic("visibleHits", visibleItems.size())
                .withDiagnostic("syntheticFiltered", syntheticFiltered);
        return new ElasticsearchSearchPage(page, items.size(), scanLimit);
    }

    private List<PostBriefDTO> filterVisibleSearchResults(List<PostBriefDTO> esItems, boolean includeTestData) {
        return filterVisibleSearchResults(esItems, includeTestData, null);
    }

    private List<PostBriefDTO> filterVisibleSearchResults(List<PostBriefDTO> esItems, boolean includeTestData,
                                                          Integer domain) {
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
            if (domain != null && !domain.equals(current.getDomain())) {
                log.debug("stale elasticsearch domain filtered: postId={} indexedDomain={} currentDomain={}",
                        esItem.getId(), esItem.getDomain(), current.getDomain());
                continue;
            }
            current.setHighlightTitle(verifiedHighlight(esItem.getHighlightTitle(), current.getTitle()).orElse(null));
            current.setHighlightSummary(verifiedHighlight(esItem.getHighlightSummary(), current.getSummary()).orElse(null));
            visible.add(current);
        }
        return visible;
    }

    private Optional<String> verifiedHighlight(String highlight, String currentText) {
        if (!StringUtils.hasText(highlight)) {
            return Optional.empty();
        }
        String plainHighlight = clean(highlight.replaceAll("<[^>]+>", ""));
        if (!StringUtils.hasText(plainHighlight) || !containsIgnoreCase(currentText, plainHighlight)) {
            return Optional.empty();
        }
        return Optional.of(highlight);
    }

    private void attachHitReasons(PageResult<PostBriefDTO> result, String keyword) {
        if (result == null || result.getItems() == null || result.getItems().isEmpty()) {
            return;
        }
        for (PostBriefDTO post : result.getItems()) {
            List<String> reasons = hitReasons(post, keyword);
            if (!reasons.isEmpty()) {
                post.setRecommendationReasons(reasons);
            }
            post.setRankingReasons(rankingReasons(post));
        }
    }

    private List<String> rankingReasons(PostBriefDTO post) {
        if (post == null || post.getTrustSignals() == null) {
            return List.of();
        }
        PostTrustSignalsDTO trust = post.getTrustSignals();
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        if (Boolean.TRUE.equals(trust.getProfileAvailable())) {
            reasons.add("trust_profile_available");
        }
        if ((trust.getCompletenessScore() == null ? 0 : trust.getCompletenessScore()) >= 60) {
            reasons.add("experience_context_complete");
        }
        if (trust.getLastConfirmedAt() != null) {
            reasons.add("author_recently_confirmed");
        }
        if (Boolean.TRUE.equals(trust.getSourceComplete())) {
            reasons.add("source_or_disclosure_provided");
        }
        if (Boolean.TRUE.equals(trust.getHasAcceptedAnswer())) {
            reasons.add("accepted_public_answer");
        }
        if ((trust.getPublicCorrectionCount() == null ? 0 : trust.getPublicCorrectionCount()) > 0) {
            reasons.add("public_correction_history");
        }
        return reasons.stream().limit(3).toList();
    }

    private List<String> hitReasons(PostBriefDTO post, String keyword) {
        if (post == null) {
            return List.of();
        }
        String kw = clean(keyword);
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        if (StringUtils.hasText(post.getHighlightTitle())) {
            reasons.add("标题高亮命中");
        }
        if (StringUtils.hasText(post.getHighlightSummary())) {
            reasons.add("摘要高亮命中");
        }
        if (!kw.isBlank()) {
            if (containsIgnoreCase(post.getTitle(), kw)) {
                reasons.add("标题包含搜索词");
            }
            if (containsIgnoreCase(post.getSummary(), kw)) {
                reasons.add("摘要包含搜索词");
            }
            if (post.getTags() != null && post.getTags().stream().anyMatch(tag ->
                    containsIgnoreCase(tag.getName(), kw)
                            || (tag.getSynonyms() != null
                            && tag.getSynonyms().stream().anyMatch(value -> containsIgnoreCase(value, kw))))) {
                reasons.add("标签匹配搜索词");
            }
            JsonNode ext = parseExt(post.getExtJson());
            if (containsIgnoreCase(ext.path("company").asText(null), kw)
                    || containsIgnoreCase(ext.path("position").asText(null), kw)
                    || containsIgnoreCase(ext.path("scenario").asText(null), kw)
                    || containsIgnoreCase(ext.path("techStacks").asText(null), kw)) {
                reasons.add("结构化字段匹配");
            }
        }
        return reasons.stream()
                .filter(reason -> !PublicContentFilter.isUnsafeSuggestionText(reason))
                .limit(3)
                .toList();
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(keyword)) {
            return false;
        }
        return value.toLowerCase().contains(keyword.toLowerCase());
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
                        || PublicContentFilter.isSyntheticText(source.path("position").asText(null))
                        || PublicContentFilter.isUnsafeSuggestionText(source.path("title").asText(null))
                        || PublicContentFilter.isUnsafeSuggestionText(source.path("company").asText(null))
                        || PublicContentFilter.isUnsafeSuggestionText(source.path("position").asText(null))) {
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
                    || PublicContentFilter.isSyntheticText(extByPostId.get(post.getId()))
                    || PublicContentFilter.isUnsafeSuggestionText(post.getTitle())
                    || PublicContentFilter.isUnsafeSuggestionText(post.getContent())
                    || PublicContentFilter.isUnsafeSuggestionText(extByPostId.get(post.getId()))) {
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
                                                   Integer type, Integer domain, String sort, String cursor, int limit,
                                                   boolean includeTestData, SearchTrustFilter trustFilter) {
        SearchCursor parsedCursor = parseSearchCursor(cursor);
        String kw = clean(keyword);
        Long keywordPostId = parsePostIdKeyword(kw).orElse(null);
        List<PostPO> candidates = migrationCheckService.tagGovernanceReady()
                ? postMapper.searchPublicPostsFallback(
                        blankToNull(kw),
                        keywordPostId,
                        blankToNull(clean(company)),
                        blankToNull(clean(position)),
                        type,
                        domain,
                        parsedCursor.time(),
                        parsedCursor.id(),
                        fallbackScanLimit(limit))
                : postMapper.searchPublicPostsFallbackCompat(
                        blankToNull(kw),
                        keywordPostId,
                        blankToNull(clean(company)),
                        blankToNull(clean(position)),
                        type,
                        domain,
                        parsedCursor.time(),
                        parsedCursor.id(),
                        fallbackScanLimit(limit));
        if (candidates.isEmpty()) {
            return PageResult.empty();
        }
        Map<Long, String> extByPostId = loadExtJson(candidates.stream().map(PostPO::getId).toList());
        List<PostPO> filtered = candidates;
        if (filtered.isEmpty()) {
            return PageResult.empty();
        }
        Map<Long, List<TagDTO>> tags = tagsByPostIds(filtered.stream().map(PostPO::getId).toList());
        List<PostBriefDTO> items = filtered.stream().map(p -> PostBriefDTO.builder()
                .id(p.getId())
                .authorId(p.getAuthorId())
                .postType(p.getPostType())
                .domain(domainOf(extByPostId.get(p.getId())))
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
        items = filterTrust(items, trustFilter);
        if ("hot".equals(sort)) {
            items = items.stream()
                    .sorted(Comparator.comparingDouble(this::hotScore).reversed()
                            .thenComparing(PostBriefDTO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        }
        boolean hasMore = items.size() > limit;
        items = items.stream().limit(limit).toList();
        String next = hasMore && !items.isEmpty() ? searchCursor(items.get(items.size() - 1)) : null;
        return PageResult.of(items, next, hasMore)
                .withDiagnostic("rawHits", candidates.size())
                .withDiagnostic("visibleHits", items.size())
                .withDiagnostic("syntheticFiltered", syntheticFiltered);
    }

    private PageResult<PostBriefDTO> searchTrustedByMysql(String keyword, String company, String position,
                                                          Integer type, Integer domain, String cursor, int limit,
                                                          boolean includeTestData, SearchTrustFilter trustFilter) {
        String kw = clean(keyword);
        Long keywordPostId = parsePostIdKeyword(kw).orElse(null);
        List<PostPO> candidates = migrationCheckService.tagGovernanceReady()
                ? postMapper.searchPublicPostsFallback(blankToNull(kw), keywordPostId, blankToNull(clean(company)),
                blankToNull(clean(position)), type, domain, null, null, MYSQL_FALLBACK_MAX_SCAN)
                : postMapper.searchPublicPostsFallbackCompat(blankToNull(kw), keywordPostId, blankToNull(clean(company)),
                blankToNull(clean(position)), type, domain, null, null, MYSQL_FALLBACK_MAX_SCAN);
        if (candidates.isEmpty()) {
            return PageResult.empty();
        }
        Map<Long, String> extByPostId = loadExtJson(candidates.stream().map(PostPO::getId).toList());
        Map<Long, List<TagDTO>> tags = tagsByPostIds(candidates.stream().map(PostPO::getId).toList());
        List<PostBriefDTO> candidatesBriefs = candidates.stream().map(p -> PostBriefDTO.builder()
                .id(p.getId())
                .authorId(p.getAuthorId())
                .postType(p.getPostType())
                .domain(domainOf(extByPostId.get(p.getId())))
                .title(p.getTitle())
                .summary(summary(p.getContent()))
                .coverUrl(p.getCoverUrl())
                .extJson(extByPostId.get(p.getId()))
                .tags(tags.getOrDefault(p.getId(), List.of()))
                .createTime(p.getCreateTime())
                .build()).toList();
        List<PostBriefDTO> visible = filterTrust(filterVisibleSearchResults(candidatesBriefs, includeTestData), trustFilter)
                .stream()
                .sorted(trustedComparator())
                .toList();
        int offset = trustedOffset(cursor);
        if (offset >= visible.size()) {
            return PageResult.empty();
        }
        int toIndex = Math.min(offset + limit, visible.size());
        List<PostBriefDTO> page = visible.subList(offset, toIndex);
        boolean hasMore = toIndex < visible.size();
        String next = hasMore ? "trusted:" + toIndex : null;
        return PageResult.of(page, next, hasMore)
                .withDiagnostic("trustedCandidateBound", MYSQL_FALLBACK_MAX_SCAN)
                .withDiagnostic("rawHits", candidates.size())
                .withDiagnostic("visibleHits", visible.size())
                .withDiagnostic("trustedOffset", offset);
    }

    private List<PostBriefDTO> filterTrust(List<PostBriefDTO> posts, SearchTrustFilter trustFilter) {
        if (posts == null || posts.isEmpty() || trustFilter == null || !trustFilter.active()) {
            return posts == null ? List.of() : posts;
        }
        return posts.stream().filter(post -> {
            PostTrustSignalsDTO signals = post.getTrustSignals();
            if (signals == null) {
                return false;
            }
            if (trustFilter.trustProfile() != null
                    && !trustFilter.trustProfile().equals(Boolean.TRUE.equals(signals.getProfileAvailable()))) {
                return false;
            }
            if (trustFilter.freshnessStatus() != null
                    && !trustFilter.freshnessStatus().equals(signals.getFreshnessStatus())) {
                return false;
            }
            if (trustFilter.resolved() != null
                    && !trustFilter.resolved().equals(Boolean.TRUE.equals(signals.getResolved()))) {
                return false;
            }
            return trustFilter.sourceComplete() == null
                    || trustFilter.sourceComplete().equals(Boolean.TRUE.equals(signals.getSourceComplete()));
        }).toList();
    }

    private Comparator<PostBriefDTO> trustedComparator() {
        return Comparator.comparing((PostBriefDTO post) -> Boolean.TRUE.equals(post.getTrustSignals() == null
                        ? null : post.getTrustSignals().getProfileAvailable()))
                .reversed()
                .thenComparing(post -> Boolean.TRUE.equals(post.getTrustSignals() == null
                        ? null : post.getTrustSignals().getSourceComplete()), Comparator.reverseOrder())
                .thenComparing(post -> post.getTrustSignals() == null || post.getTrustSignals().getCompletenessScore() == null
                        ? 0 : post.getTrustSignals().getCompletenessScore(), Comparator.reverseOrder())
                .thenComparing(post -> post.getTrustSignals() == null ? null : post.getTrustSignals().getLastConfirmedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(post -> Boolean.TRUE.equals(post.getTrustSignals() == null
                        ? null : post.getTrustSignals().getResolved()), Comparator.reverseOrder())
                .thenComparing(PostBriefDTO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PostBriefDTO::getId, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private int trustedOffset(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        String value = cursor.trim();
        if (!value.startsWith("trusted:")) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "invalid trusted search cursor");
        }
        try {
            int offset = Integer.parseInt(value.substring("trusted:".length()));
            if (offset < 0 || offset > MYSQL_FALLBACK_MAX_SCAN) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "invalid trusted search cursor");
            }
            return offset;
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "invalid trusted search cursor");
        }
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

    private List<TagPO> hotTags(int limit) {
        return migrationCheckService.tagGovernanceReady()
                ? tagMapper.selectHotTags(limit)
                : tagMapper.selectHotTagsCompat(limit);
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
        if (value != null
                && !value.isBlank()
                && value.toLowerCase().contains(prefix.toLowerCase())
                && !PublicContentFilter.isUnsafeSuggestionText(value)) {
            result.add(value);
        }
    }

    private Integer domainOf(String extJson) {
        JsonNode domain = parseExt(extJson).path("domain");
        if (!domain.canConvertToInt()) {
            return null;
        }
        int value = domain.asInt();
        return value >= 1 && value <= 5 ? value : null;
    }

    private Integer validDomain(JsonNode domain) {
        if (domain == null || !domain.canConvertToInt()) {
            return null;
        }
        int value = domain.asInt();
        return value >= 1 && value <= 5 ? value : null;
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

    private SearchCursor parseSearchCursor(String cursor) {
        if (cursor == null || cursor.isBlank() || "0".equals(cursor.trim())) {
            return SearchCursor.empty();
        }
        String value = cursor.trim();
        if (value.chars().allMatch(Character::isDigit)) {
            long millis = Long.parseLong(value);
            if (millis <= 0) {
                return SearchCursor.empty();
            }
            return new SearchCursor(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC), 0L, millis);
        }
        try {
            CursorUtils.TimeIdCursor decoded = CursorUtils.decodeTimeId(value, SEARCH_CURSOR_VERSION);
            return new SearchCursor(decoded.time(), decoded.id(), decoded.millis());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "invalid search cursor");
        }
    }

    private static String searchCursor(PostBriefDTO post) {
        if (post == null || post.getCreateTime() == null || post.getId() == null || post.getId() <= 0) {
            return null;
        }
        return CursorUtils.encodeTimeId(SEARCH_CURSOR_VERSION, post.getCreateTime(), post.getId());
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

    private record SearchCursor(LocalDateTime time, Long id, long millis) {
        private static SearchCursor empty() {
            return new SearchCursor(null, null, 0L);
        }

        private boolean present() {
            return time != null;
        }
    }

    private String normalizeSort(String sort) {
        String value = clean(sort).toLowerCase();
        if ("hot".equals(value) || "latest".equals(value) || "relevance".equals(value) || "trusted".equals(value)) {
            return value;
        }
        return "relevance";
    }

    private void addName(Set<String> result, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        addSafeKeyword(result, text);
    }

    private void addSafeKeyword(Set<String> result, String text) {
        if (isSafeSuggestionText(text)) {
            result.add(text);
        }
    }

    private boolean isSafeSuggestionText(String text) {
        return text != null
                && !text.isBlank()
                && !PublicContentFilter.isSyntheticText(text)
                && !PublicContentFilter.isUnsafeSuggestionText(text);
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
