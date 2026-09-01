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
import com.offerlab.community.post.api.PublicPostExtensionSanitizer;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostTrustSignalsDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.post.domain.model.Post;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchFacadeImpl implements SearchFacade {

    private static final int SUMMARY_LEN = 120;
    private static final int MYSQL_FALLBACK_MAX_SCAN = 200;
    private static final String SEARCH_CURSOR_VERSION = "sr2";
    private static final String LEGACY_SEARCH_CURSOR_VERSION = "sr1";

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
        SearchQuerySpec querySpec = SearchQuerySpec.from(keyword);
        String normalizedKeyword = querySpec.normalized();
        String normalizedSort = normalizeSort(sort);
        SearchTrustFilter normalizedTrustFilter = trustFilter == null ? SearchTrustFilter.empty() : trustFilter;
        boolean trustedSort = "trusted".equals(normalizedSort);
        boolean trustConstrained = trustedSort || normalizedTrustFilter.active();
        SearchCursor searchCursor = trustedSort ? null : parseSearchCursor(cursor, normalizedSort);
        boolean firstPage = trustedSort
                ? trustedOffset(cursor) == 0
                : !searchCursor.present();
        if (trustConstrained && !migrationCheckService.trustedDistributionReady()) {
            PageResult<PostBriefDTO> unavailable = withSearchMetadata(
                    PageResult.empty(),
                    "mysql",
                    true,
                    "trusted_distribution_migration_pending",
                    0,
                    includeTestData,
                    normalizedKeyword,
                    type,
                    domain,
                    normalizedTrustFilter,
                    normalizedSort
            );
            searchAnalyticsService.recordSearch(normalizedKeyword, company, position, type, normalizedSort, 0, firstPage);
            return unavailable;
        }
        PageResult<PostBriefDTO> result;
        if (trustedSort) {
            result = searchTrustedByMysql(querySpec, company, position, type, domain, cursor, limit, includeTestData,
                    normalizedTrustFilter);
            result = withSearchMetadata(result, "mysql", false, null, MYSQL_FALLBACK_MAX_SCAN,
                    includeTestData, normalizedKeyword, type, domain, normalizedTrustFilter, normalizedSort);
            searchAnalyticsService.recordSearch(normalizedKeyword, company, position, type, normalizedSort,
                    result.getItems().size(), firstPage);
            return result;
        }
        if (!trustConstrained && !"hot".equals(normalizedSort)) {
            if (searchCursor.requiresMysqlContinuation(normalizedSort)) {
                result = searchByMysql(querySpec, company, position, type, domain, normalizedSort, cursor, limit,
                        includeTestData, normalizedTrustFilter);
                result = withSearchMetadata(result, "mysql", true, "mysql_fallback_continuation",
                        fallbackScanLimit(limit), includeTestData, normalizedKeyword, type, domain,
                        normalizedTrustFilter, normalizedSort);
                searchAnalyticsService.recordSearch(normalizedKeyword, company, position, type, normalizedSort,
                        result.getItems().size(), false);
                return result;
            }
            boolean elasticsearchReady = postSearchIndexer.ensurePostIndex();
            if (elasticsearchReady) {
                Optional<ElasticsearchSearchPage> esResult = searchByElasticsearch(
                        querySpec, company, position, type, domain, normalizedSort, cursor, limit, includeTestData);
                if (esResult.isPresent()) {
                    ElasticsearchSearchPage esPage = esResult.get();
                    if (firstPage && querySpec.hasMeaningfulTerms() && isEmptyPage(esPage.page())) {
                        PageResult<PostBriefDTO> consistencyFallback = searchByMysql(
                                querySpec, company, position, type, domain, normalizedSort, null, limit,
                                includeTestData, normalizedTrustFilter);
                        if (!isEmptyPage(consistencyFallback)) {
                            log.warn("search index returned no hits while public database fallback matched: keyword={}",
                                    normalizedKeyword);
                            result = withSearchMetadata(
                                    consistencyFallback,
                                    "mysql",
                                    true,
                                    "search_index_empty_consistency_fallback",
                                    fallbackScanLimit(limit),
                                    includeTestData,
                                    normalizedKeyword,
                                    type,
                                    domain,
                                    normalizedTrustFilter,
                                    normalizedSort
                            );
                            searchAnalyticsService.recordSearch(
                                    normalizedKeyword, company, position, type, normalizedSort,
                                    result.getItems().size(), true);
                            return result;
                        }
                    }
                    result = withSearchMetadata(esPage.page(), "elasticsearch", false, null, esPage.scanLimit(),
                            includeTestData, normalizedKeyword, type, domain, normalizedTrustFilter, normalizedSort);
                    searchAnalyticsService.recordSearch(normalizedKeyword, company, position, type, normalizedSort,
                            result.getItems().size(), firstPage);
                    return result;
                }
            }
            if (searchCursor.requiresElasticsearchContinuation(normalizedSort)) {
                throw new BizException(
                        ErrorCode.ELASTICSEARCH_ERROR.getCode(),
                        "elasticsearch relevance continuation unavailable; retry with the same cursor"
                );
            }
        }
        result = searchByMysql(querySpec, company, position, type, domain, normalizedSort, cursor, limit,
                includeTestData, normalizedTrustFilter);
        result = withSearchMetadata(result, "mysql", !("hot".equals(normalizedSort) || trustConstrained),
                "hot".equals(normalizedSort) ? "hot_sort_mysql"
                        : trustConstrained ? "trust_filter_mysql"
                        : "elasticsearch_unavailable",
                fallbackScanLimit(limit), includeTestData, normalizedKeyword, type, domain,
                normalizedTrustFilter, normalizedSort);
        searchAnalyticsService.recordSearch(normalizedKeyword, company, position, type, normalizedSort,
                result.getItems().size(), firstPage);
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

    private Optional<ElasticsearchSearchPage> searchByElasticsearch(SearchQuerySpec querySpec, String company, String position,
                                                                    Integer type, Integer domain, String sort, String cursor, int limit,
                                                                    boolean includeTestData) {
        SearchCursor parsedCursor = parseSearchCursor(cursor, sort);
        if (parsedCursor.present() && !parsedCursor.supportsElasticsearch(sort)) {
            return Optional.empty();
        }
        int scanLimit = elasticsearchScanLimit(limit);
        Map<String, Object> body = new HashMap<>();
        body.put("query", buildEsQuery(querySpec, company, position, type, domain));
        body.put("sort", buildEsSort(sort));
        if (parsedCursor.present()) {
            body.put("search_after", parsedCursor.elasticsearchSortValues(sort));
        }
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
                .map(json -> toElasticsearchPage(json, sort, limit, scanLimit, includeTestData, domain));
    }

    private List<Object> buildEsSort(String sort) {
        if ("relevance".equals(sort)) {
            return List.of(
                    Map.of("_score", Map.of("order", "desc")),
                    Map.of("createTime", Map.of("order", "desc")),
                    Map.of("postId", Map.of("order", "desc"))
            );
        }
        return List.of(
                Map.of("createTime", Map.of("order", "desc")),
                Map.of("postId", Map.of("order", "desc"))
        );
    }

    private Map<String, Object> buildEsQuery(SearchQuerySpec querySpec, String company, String position, Integer type,
                                             Integer domain) {
        List<Object> must = new ArrayList<>();
        List<Object> should = new ArrayList<>();
        List<Object> filter = new ArrayList<>();
        if (querySpec.emptyInput()) {
            must.add(Map.of("match_all", Map.of()));
        } else if (querySpec.requiresNoMatches()) {
            must.add(Map.of("match_none", Map.of()));
        } else {
            List<Object> termClauses = querySpec.terms().stream()
                    .map(term -> Map.of("multi_match", Map.of(
                            "query", term,
                            "fields", List.of("title^5", "summary^3", "company^3", "scenario^3",
                                    "techStacks^3", "tagNames^2", "tagSynonyms^2", "tagSearchTerms^2",
                                    "position^2", "content"),
                            "type", "best_fields",
                            "operator", "and"
                    )))
                    .map(clause -> (Object) clause)
                    .toList();
            if (!termClauses.isEmpty()) {
                must.add(Map.of("bool", Map.of(
                        "should", termClauses,
                        "minimum_should_match", querySpec.minimumTermMatches()
                )));
                should.add(Map.of("match_phrase", Map.of("title", Map.of("query", querySpec.normalized(), "boost", 8))));
                should.add(Map.of("match_phrase", Map.of("summary", Map.of("query", querySpec.normalized(), "boost", 5))));
                should.add(Map.of("match_phrase", Map.of("content", Map.of("query", querySpec.normalized(), "boost", 2))));
            }
            parsePostIdKeyword(querySpec.normalized()).ifPresent(postId -> must.add(Map.of("bool", Map.of(
                    "should", List.of(
                            Map.of("term", Map.of("id", postId)),
                            Map.of("term", Map.of("postId", postId))
                    ),
                    "minimum_should_match", 1
            ))));
        }
        filter.add(Map.of("term", Map.of("status", "published")));
        filter.add(Map.of("term", Map.of("visibility", 1)));
        filter.add(Map.of("term", Map.of(
                "contentEnvironment",
                Post.CONTENT_ENVIRONMENT_COMMUNITY
        )));
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
        Map<String, Object> bool = new HashMap<>();
        bool.put("must", must);
        bool.put("filter", filter);
        if (!should.isEmpty()) {
            bool.put("should", should);
        }
        return Map.of("bool", bool);
    }

    private ElasticsearchSearchPage toElasticsearchPage(JsonNode json, String sort, int limit, int scanLimit,
                                                         boolean includeTestData, Integer domain) {
        JsonNode hits = json.path("hits").path("hits");
        if (!hits.isArray() || hits.isEmpty()) {
            return new ElasticsearchSearchPage(PageResult.empty(), 0, scanLimit);
        }
        List<PostBriefDTO> items = new ArrayList<>();
        Map<Long, SearchSortValues> sortValuesByPostId = new HashMap<>();
        List<SearchSortValues> rawSortValues = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            String summary = source.path("summary").asText("");
            long postId = source.path("postId").asLong(source.path("id").asLong());
            PostBriefDTO item = PostBriefDTO.builder()
                    .id(postId)
                    .authorId(source.path("authorId").asLong())
                    .postType(source.path("type").asInt())
                    .domain(validDomain(source.path("domain")))
                    .title(source.path("title").asText(""))
                    .summary(summary)
                    .highlightTitle(firstHighlight(hit, "title").orElse(null))
                    .highlightSummary(firstHighlight(hit, "content").orElse(null))
                    .coverUrl(source.path("coverUrl").asText(null))
                    .extJson(PublicPostExtensionSanitizer.sanitize(source.path("extJson").asText(null)))
                    .tags(toTags(source.path("tags")))
                    .createTime(toLocalDateTime(source.path("createTime").asLong(0L)))
                    .build();
            SearchSortValues hitSortValues = SearchSortValues.fromElasticsearchHit(hit, sort, item);
            items.add(item);
            sortValuesByPostId.put(postId, hitSortValues);
            rawSortValues.add(hitSortValues);
        }
        List<PostBriefDTO> visibleItems = filterVisibleSearchResults(items, includeTestData, domain);
        int syntheticFiltered = includeTestData ? 0 : (int) items.stream()
                .filter(PublicContentFilter::isSyntheticPost)
                .count();
        boolean hasMore = visibleItems.size() > limit;
        boolean rawHasMore = items.size() >= scanLimit;
        List<PostBriefDTO> pageItems = hasMore ? visibleItems.subList(0, limit) : visibleItems;
        SearchSortValues cursorValues = null;
        if (hasMore && !pageItems.isEmpty()) {
            cursorValues = sortValuesByPostId.get(pageItems.get(pageItems.size() - 1).getId());
        } else if (rawHasMore && !rawSortValues.isEmpty()) {
            cursorValues = rawSortValues.get(rawSortValues.size() - 1);
        }
        hasMore = (hasMore || rawHasMore) && cursorValues != null;
        String next = hasMore ? searchCursor(sort, cursorValues) : null;
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
                                Map.of("term", Map.of("visibility", 1)),
                                Map.of("term", Map.of(
                                        "contentEnvironment",
                                        Post.CONTENT_ENVIRONMENT_COMMUNITY
                                ))
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

    private PageResult<PostBriefDTO> searchByMysql(SearchQuerySpec querySpec, String company, String position,
                                                   Integer type, Integer domain, String sort, String cursor, int limit,
                                                   boolean includeTestData, SearchTrustFilter trustFilter) {
        SearchCursor parsedCursor = parseSearchCursor(cursor, sort);
        Long keywordPostId = parsePostIdKeyword(querySpec.normalized()).orElse(null);
        if (querySpec.requiresNoMatches()) {
            return PageResult.empty();
        }
        if ("hot".equals(sort)) {
            return searchHotByMysql(
                    querySpec.terms(),
                    querySpec.minimumTermMatches(),
                    keywordPostId,
                    blankToNull(clean(company)),
                    blankToNull(clean(position)),
                    type,
                    domain,
                    parsedCursor,
                    limit,
                    includeTestData,
                    trustFilter
            );
        }
        int scanLimit = fallbackScanLimit(limit);
        List<PostPO> candidates = migrationCheckService.tagGovernanceReady()
                ? postMapper.searchPublicPostsFallback(
                        querySpec.terms(),
                        querySpec.minimumTermMatches(),
                        keywordPostId,
                        blankToNull(clean(company)),
                        blankToNull(clean(position)),
                        type,
                        domain,
                        parsedCursor.time(),
                        parsedCursor.id(),
                        scanLimit)
                : postMapper.searchPublicPostsFallbackCompat(
                        querySpec.terms(),
                        querySpec.minimumTermMatches(),
                        keywordPostId,
                        blankToNull(clean(company)),
                        blankToNull(clean(position)),
                        type,
                        domain,
                        parsedCursor.time(),
                        parsedCursor.id(),
                        scanLimit);
        if (candidates.isEmpty()) {
            return PageResult.empty();
        }
        Map<Long, PostPO> candidatesById = candidates.stream()
                .collect(Collectors.toMap(PostPO::getId, post -> post, (left, right) -> left));
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
                .extJson(PublicPostExtensionSanitizer.sanitize(extByPostId.get(p.getId())))
                .tags(tags.getOrDefault(p.getId(), List.of()))
                .createTime(p.getCreateTime())
                .build()).toList();
        int syntheticFiltered = includeTestData ? 0 : (int) items.stream()
                .filter(PublicContentFilter::isSyntheticPost)
                .count();
        items = filterVisibleSearchResults(items, includeTestData);
        items = filterTrust(items, trustFilter);
        int visibleHits = items.size();
        boolean visibleHasMore = visibleHits > limit;
        boolean scanWindowExhausted = candidates.size() >= scanLimit;
        List<PostBriefDTO> pageItems = items.stream().limit(limit).toList();
        PostPO cursorRow = null;
        if (visibleHasMore && !pageItems.isEmpty()) {
            cursorRow = candidatesById.get(pageItems.get(pageItems.size() - 1).getId());
        } else if (scanWindowExhausted) {
            cursorRow = candidates.get(candidates.size() - 1);
        }
        String next = cursorRow == null
                ? null
                : searchCursor(sort, SearchSortValues.fromPostRow(cursorRow));
        boolean hasMore = next != null && (visibleHasMore || scanWindowExhausted);
        return PageResult.of(pageItems, next, hasMore)
                .withDiagnostic("rawHits", candidates.size())
                .withDiagnostic("visibleHits", visibleHits)
                .withDiagnostic("syntheticFiltered", syntheticFiltered)
                .withDiagnostic("scanWindowExhausted", scanWindowExhausted);
    }

    private PageResult<PostBriefDTO> searchHotByMysql(List<String> keywordTerms, int minimumKeywordMatches,
                                                       Long keywordPostId,
                                                       String company, String position,
                                                       Integer type, Integer domain,
                                                       SearchCursor cursor, int limit,
                                                       boolean includeTestData,
                                                       SearchTrustFilter trustFilter) {
        int scanLimit = fallbackScanLimit(limit);
        long rankingMillis = cursor.rankingMillis() > 0
                ? cursor.rankingMillis()
                : Instant.now().toEpochMilli();
        LocalDateTime rankingTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(rankingMillis), ZoneOffset.UTC);
        List<PostMapper.SearchHotRow> candidates = postMapper.searchPublicPostsHotFallback(
                keywordTerms,
                minimumKeywordMatches,
                keywordPostId,
                company,
                position,
                type,
                domain,
                migrationCheckService.tagGovernanceReady(),
                rankingTime,
                cursor.hotScore(),
                cursor.time(),
                cursor.id(),
                scanLimit
        );
        if (candidates == null || candidates.isEmpty()) {
            return PageResult.empty();
        }
        Map<Long, PostMapper.SearchHotRow> rowsByPostId = candidates.stream()
                .collect(Collectors.toMap(
                        PostMapper.SearchHotRow::getPostId,
                        row -> row,
                        (left, right) -> left
                ));
        List<PostBriefDTO> stubs = candidates.stream()
                .map(row -> PostBriefDTO.builder()
                        .id(row.getPostId())
                        .createTime(row.getCreateTime())
                        .build())
                .toList();
        List<PostBriefDTO> currentItems = filterVisibleSearchResults(stubs, true, domain);
        int syntheticFiltered = includeTestData ? 0 : (int) currentItems.stream()
                .filter(PublicContentFilter::isSyntheticPost)
                .count();
        if (!includeTestData) {
            currentItems = currentItems.stream()
                    .filter(item -> !PublicContentFilter.isSyntheticPost(item))
                    .toList();
        }
        currentItems = filterTrust(currentItems, trustFilter);
        int visibleHits = currentItems.size();
        boolean visibleHasMore = visibleHits > limit;
        boolean rawHasMore = candidates.size() >= scanLimit;
        List<PostBriefDTO> pageItems = currentItems.stream().limit(limit).toList();
        PostMapper.SearchHotRow cursorRow = null;
        if (visibleHasMore && !pageItems.isEmpty()) {
            cursorRow = rowsByPostId.get(pageItems.get(pageItems.size() - 1).getId());
        } else if (rawHasMore) {
            cursorRow = candidates.get(candidates.size() - 1);
        }
        boolean hasMore = (visibleHasMore || rawHasMore) && cursorRow != null;
        String next = hasMore
                ? searchCursor("hot", SearchSortValues.fromHotRow(cursorRow, rankingMillis))
                : null;
        return PageResult.of(pageItems, next, hasMore)
                .withDiagnostic("rawHits", candidates.size())
                .withDiagnostic("visibleHits", visibleHits)
                .withDiagnostic("syntheticFiltered", syntheticFiltered);
    }

    private PageResult<PostBriefDTO> searchTrustedByMysql(SearchQuerySpec querySpec, String company, String position,
                                                          Integer type, Integer domain, String cursor, int limit,
                                                          boolean includeTestData, SearchTrustFilter trustFilter) {
        Long keywordPostId = parsePostIdKeyword(querySpec.normalized()).orElse(null);
        if (querySpec.requiresNoMatches()) {
            return PageResult.empty();
        }
        List<PostPO> candidates = migrationCheckService.tagGovernanceReady()
                ? postMapper.searchPublicPostsFallback(querySpec.terms(), querySpec.minimumTermMatches(), keywordPostId, blankToNull(clean(company)),
                blankToNull(clean(position)), type, domain, null, null, MYSQL_FALLBACK_MAX_SCAN)
                : postMapper.searchPublicPostsFallbackCompat(querySpec.terms(), querySpec.minimumTermMatches(), keywordPostId, blankToNull(clean(company)),
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
                .extJson(PublicPostExtensionSanitizer.sanitize(extByPostId.get(p.getId())))
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

    private SearchCursor parseSearchCursor(String cursor, String requestedSort) {
        if (cursor == null || cursor.isBlank() || "0".equals(cursor.trim())) {
            return SearchCursor.empty(requestedSort);
        }
        String value = cursor.trim();
        if (value.chars().allMatch(Character::isDigit)) {
            if ("hot".equals(requestedSort)) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "legacy hot search cursor is unsupported");
            }
            if ("relevance".equals(requestedSort)) {
                throw new BizException(
                        ErrorCode.PARAM_ERROR.getCode(),
                        "legacy relevance cursor is ambiguous; restart the search");
            }
            try {
                long millis = Long.parseLong(value);
                if (millis <= 0) {
                    return SearchCursor.empty(requestedSort);
                }
                return SearchCursor.legacy(requestedSort, LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(millis), ZoneOffset.UTC), Long.MAX_VALUE, millis);
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "invalid search cursor");
            }
        }
        try {
            return decodeSearchCursor(value, requestedSort);
        } catch (IllegalArgumentException currentVersionError) {
            if ("hot".equals(requestedSort)) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "invalid hot search cursor");
            }
            try {
                CursorUtils.TimeIdCursor decoded = CursorUtils.decodeTimeId(value, LEGACY_SEARCH_CURSOR_VERSION);
                if ("relevance".equals(requestedSort)) {
                    throw new BizException(
                            ErrorCode.PARAM_ERROR.getCode(),
                            "legacy relevance cursor is ambiguous; restart the search");
                }
                return SearchCursor.legacy(requestedSort, decoded.time(), decoded.id(), decoded.millis());
            } catch (BizException businessError) {
                throw businessError;
            } catch (IllegalArgumentException legacyError) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "invalid search cursor");
            }
        }
    }

    private SearchCursor decodeSearchCursor(String cursor, String requestedSort) {
        String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        String canonical = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        if (!canonical.equals(cursor)) {
            throw new IllegalArgumentException("non-canonical cursor");
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 6 || !SEARCH_CURSOR_VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("cursor version mismatch");
        }
        String encodedSort = parts[1];
        if (!encodedSort.equals(requestedSort)
                || !("latest".equals(encodedSort)
                || "relevance".equals(encodedSort)
                || "hot".equals(encodedSort))) {
            throw new IllegalArgumentException("cursor sort mismatch");
        }
        long millis = parsePositiveCursorLong(parts[3], "cursor time");
        long id = parsePositiveCursorLong(parts[4], "cursor id");
        long rankingMillis = parts[5].isBlank()
                ? 0L
                : parsePositiveCursorLong(parts[5], "cursor ranking time");
        Double relevanceScore = null;
        Long hotScore = null;
        if ("relevance".equals(encodedSort) && !parts[2].isBlank()) {
            relevanceScore = Double.parseDouble(parts[2]);
            if (!Double.isFinite(relevanceScore) || relevanceScore < 0D) {
                throw new IllegalArgumentException("invalid relevance score");
            }
        } else if ("hot".equals(encodedSort)) {
            hotScore = parseNonNegativeCursorLong(parts[2], "cursor hot score");
            if (rankingMillis <= 0) {
                throw new IllegalArgumentException("hot cursor ranking time is required");
            }
        } else if (!parts[2].isBlank()) {
            throw new IllegalArgumentException("unexpected cursor score");
        } else if (rankingMillis != 0L) {
            throw new IllegalArgumentException("unexpected cursor ranking time");
        }
        return new SearchCursor(
                encodedSort,
                relevanceScore,
                hotScore,
                LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC),
                id,
                millis,
                rankingMillis,
                false
        );
    }

    private static String searchCursor(String sort, SearchSortValues values) {
        if (values == null || values.millis() <= 0 || values.id() <= 0) {
            return null;
        }
        String score = "";
        if ("relevance".equals(sort)) {
            if (values.relevanceScore() != null) {
                if (!Double.isFinite(values.relevanceScore()) || values.relevanceScore() < 0D) {
                    return null;
                }
                score = Double.toString(values.relevanceScore());
            }
        } else if ("hot".equals(sort)) {
            if (values.hotScore() == null || values.hotScore() < 0L || values.rankingMillis() <= 0L) {
                return null;
            }
            score = Long.toString(values.hotScore());
        }
        String ranking = "hot".equals(sort) ? Long.toString(values.rankingMillis()) : "";
        String raw = SEARCH_CURSOR_VERSION + "|" + sort + "|" + score + "|"
                + values.millis() + "|" + values.id() + "|" + ranking;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static long parsePositiveCursorLong(String value, String label) {
        long parsed = parseNonNegativeCursorLong(value, label);
        if (parsed <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return parsed;
    }

    private static long parseNonNegativeCursorLong(String value, String label) {
        if (value == null || value.isBlank() || !value.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException(label + " must be digits");
        }
        long parsed = Long.parseLong(value);
        if (parsed < 0) {
            throw new IllegalArgumentException(label + " must not be negative");
        }
        return parsed;
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

    private record SearchCursor(String sort, Double relevanceScore, Long hotScore,
                                LocalDateTime time, Long id, long millis,
                                long rankingMillis, boolean legacy) {
        private static SearchCursor empty(String sort) {
            return new SearchCursor(sort, null, null, null, null, 0L, 0L, false);
        }

        private static SearchCursor legacy(String sort, LocalDateTime time, Long id, long millis) {
            return new SearchCursor(sort, null, null, time, id, millis, 0L, true);
        }

        private boolean present() {
            return time != null;
        }

        private boolean supportsElasticsearch(String requestedSort) {
            if (!present() || !Objects.equals(sort, requestedSort)) {
                return false;
            }
            return "latest".equals(requestedSort)
                    || ("relevance".equals(requestedSort) && relevanceScore != null);
        }

        private boolean requiresElasticsearchContinuation(String requestedSort) {
            return present()
                    && "relevance".equals(requestedSort)
                    && Objects.equals(sort, requestedSort)
                    && relevanceScore != null;
        }

        private boolean requiresMysqlContinuation(String requestedSort) {
            return present()
                    && "relevance".equals(requestedSort)
                    && Objects.equals(sort, requestedSort)
                    && relevanceScore == null;
        }

        private List<Object> elasticsearchSortValues(String requestedSort) {
            if (!supportsElasticsearch(requestedSort)) {
                throw new IllegalStateException("cursor does not contain the Elasticsearch sort tuple");
            }
            if ("relevance".equals(requestedSort)) {
                return List.of(relevanceScore, millis, id);
            }
            return List.of(millis, id);
        }
    }

    private record SearchSortValues(Double relevanceScore, Long hotScore,
                                    long millis, long id, long rankingMillis) {
        private static SearchSortValues fromElasticsearchHit(JsonNode hit, String sort, PostBriefDTO fallback) {
            JsonNode values = hit.path("sort");
            if ("relevance".equals(sort) && values.isArray() && values.size() == 3) {
                return new SearchSortValues(
                        values.get(0).asDouble(),
                        null,
                        values.get(1).asLong(),
                        values.get(2).asLong(),
                        0L
                );
            }
            if (!"relevance".equals(sort) && values.isArray() && values.size() == 2) {
                return new SearchSortValues(
                        null,
                        null,
                        values.get(0).asLong(),
                        values.get(1).asLong(),
                        0L
                );
            }
            SearchSortValues fallbackValues = fromPost(fallback);
            return "relevance".equals(sort)
                    ? new SearchSortValues(hit.path("_score").asDouble(), null,
                    fallbackValues.millis(), fallbackValues.id(), 0L)
                    : fallbackValues;
        }

        private static SearchSortValues fromPost(PostBriefDTO post) {
            long millis = post == null || post.getCreateTime() == null
                    ? 0L
                    : post.getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli();
            long id = post == null || post.getId() == null ? 0L : post.getId();
            return new SearchSortValues(null, null, millis, id, 0L);
        }

        private static SearchSortValues fromPostRow(PostPO post) {
            long millis = post == null || post.getCreateTime() == null
                    ? 0L
                    : post.getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli();
            long id = post == null || post.getId() == null ? 0L : post.getId();
            return new SearchSortValues(null, null, millis, id, 0L);
        }

        private static SearchSortValues fromHotRow(PostMapper.SearchHotRow row, long rankingMillis) {
            long millis = row == null || row.getCreateTime() == null
                    ? 0L
                    : row.getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli();
            long id = row == null || row.getPostId() == null ? 0L : row.getPostId();
            return new SearchSortValues(
                    null,
                    row == null ? null : row.getHotScore(),
                    millis,
                    id,
                    rankingMillis
            );
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
