package com.offerlab.community.search.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostCounterDTO;
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
    private static final List<String> FALLBACK_HOT = List.of("Java", "字节跳动", "面经", "Spring", "Redis", "Kafka");

    private final PostMapper postMapper;
    private final PostExtensionMapper extensionMapper;
    private final TagMapper tagMapper;
    private final ObjectMapper objectMapper;
    private final ElasticsearchHttpClient elasticsearch;
    private final PostSearchIndexer postSearchIndexer;
    private final PostFacade postFacade;
    private final UserFacade userFacade;

    @Override
    public PageResult<PostBriefDTO> searchPosts(String keyword, String company, String position,
                                                Integer type, String sort, String cursor, int size) {
        int limit = Math.min(size <= 0 ? 20 : size, 50);
        String normalizedSort = normalizeSort(sort);
        if (!"hot".equals(normalizedSort) && postSearchIndexer.ensurePostIndex()) {
            Optional<PageResult<PostBriefDTO>> esResult = searchByElasticsearch(keyword, company, position, type, normalizedSort, cursor, limit);
            if (esResult.isPresent()) {
                return esResult.get();
            }
        }
        return searchByMysql(keyword, company, position, type, normalizedSort, cursor, limit);
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
        tagMapper.selectActiveTags().stream()
                .sorted(Comparator.comparing(TagPO::getUseCount, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(TagPO::getTagName)
                .filter(name -> name != null && !name.isBlank())
                .limit(limit)
                .forEach(result::add);
        LocalDateTime since = LocalDateTime.now().minusDays(90);
        postMapper.countCompanies(since, limit).forEach(row -> addName(result, row.get("name")));
        postMapper.countPositions(since, limit).forEach(row -> addName(result, row.get("name")));
        FALLBACK_HOT.forEach(result::add);
        return result.stream().limit(limit).toList();
    }

    private Optional<PageResult<PostBriefDTO>> searchByElasticsearch(String keyword, String company, String position,
                                                                     Integer type, String sort, String cursor, int limit) {
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
        body.put("size", limit);
        return elasticsearch.search(elasticsearch.postIndex(), body)
                .map(json -> toPageResult(json, limit));
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
            must.add(Map.of("multi_match", Map.of(
                    "query", kw,
                    "fields", List.of("title^3", "content", "company^2", "position"),
                    "type", "best_fields",
                    "operator", "or"
            )));
        }
        filter.add(Map.of("term", Map.of("status", "published")));
        filter.add(Map.of("term", Map.of("visibility", 1)));
        if (type != null) {
            filter.add(Map.of("term", Map.of("type", type)));
        }
        if (!clean(company).isBlank()) {
            filter.add(Map.of("match_phrase", Map.of("company", clean(company))));
        }
        if (!clean(position).isBlank()) {
            filter.add(Map.of("term", Map.of("position", clean(position))));
        }
        long c = parseCursor(cursor);
        if (c > 0) {
            filter.add(Map.of("range", Map.of("createTime", Map.of("lt", c))));
        }
        return Map.of("bool", Map.of("must", must, "filter", filter));
    }

    private PageResult<PostBriefDTO> toPageResult(JsonNode json, int limit) {
        JsonNode hits = json.path("hits").path("hits");
        if (!hits.isArray() || hits.isEmpty()) {
            return PageResult.empty();
        }
        List<PostBriefDTO> items = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            items.add(PostBriefDTO.builder()
                    .id(source.path("id").asLong())
                    .authorId(source.path("authorId").asLong())
                    .postType(source.path("type").asInt())
                    .title(firstHighlight(hit, "title").orElse(source.path("title").asText("")))
                    .summary(firstHighlight(hit, "content").orElse(source.path("summary").asText("")))
                    .coverUrl(source.path("coverUrl").asText(null))
                    .extJson(source.path("extJson").asText(null))
                    .tags(toTags(source.path("tags")))
                    .createTime(toLocalDateTime(source.path("createTime").asLong(0L)))
                    .build());
        }
        boolean hasMore = items.size() == limit;
        String next = hasMore
                ? String.valueOf(items.get(items.size() - 1).getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli())
                : null;
        return PageResult.of(enrich(items), next, hasMore);
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
                        "filter", List.of(Map.of("term", Map.of("status", "published"))),
                        "should", List.of(
                                Map.of("match_phrase_prefix", Map.of("title", prefix)),
                                Map.of("match_phrase_prefix", Map.of("company", prefix)),
                                Map.of("prefix", Map.of("position", prefix))
                        ),
                        "minimum_should_match", 1
                )),
                "_source", List.of("title", "company", "position"),
                "size", limit
        );
        return elasticsearch.search(elasticsearch.postIndex(), body).map(json -> {
            Set<String> result = new LinkedHashSet<>();
            JsonNode hits = json.path("hits").path("hits");
            for (JsonNode hit : hits) {
                JsonNode source = hit.path("_source");
                addIfMatches(result, source.path("company").asText(null), prefix);
                addIfMatches(result, source.path("position").asText(null), prefix);
                addIfMatches(result, source.path("title").asText(null), prefix);
            }
            return result.stream().limit(limit).toList();
        });
    }

    private List<String> suggestByMysql(String prefix, int limit) {
        String p = clean(prefix);
        if (p.isBlank()) {
            return List.of();
        }
        LambdaQueryWrapper<PostPO> q = new LambdaQueryWrapper<PostPO>()
                .eq(PostPO::getPostStatus, Post.STATUS_PUBLISHED)
                .eq(PostPO::getVisibility, Post.VIS_PUBLIC)
                .and(w -> w.like(PostPO::getTitle, p).or().like(PostPO::getContent, p))
                .orderByDesc(PostPO::getCreateTime)
                .last("LIMIT " + Math.max(limit * 5, limit));
        List<PostPO> candidates = postMapper.selectList(q);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<Long, String> extByPostId = loadExtJson(candidates.stream().map(PostPO::getId).toList());
        Set<String> result = new LinkedHashSet<>();
        for (PostPO post : candidates) {
            JsonNode ext = parseExt(extByPostId.get(post.getId()));
            addIfMatches(result, ext.path("company").asText(null), p);
            addIfMatches(result, ext.path("position").asText(null), p);
            addIfMatches(result, post.getTitle(), p);
            if (result.size() >= limit) {
                break;
            }
        }
        return result.stream().limit(limit).toList();
    }

    private PageResult<PostBriefDTO> searchByMysql(String keyword, String company, String position,
                                                   Integer type, String sort, String cursor, int limit) {
        long c = parseCursor(cursor);
        LambdaQueryWrapper<PostPO> q = new LambdaQueryWrapper<PostPO>()
                .eq(PostPO::getPostStatus, Post.STATUS_PUBLISHED)
                .eq(PostPO::getVisibility, Post.VIS_PUBLIC)
                .orderByDesc(PostPO::getCreateTime)
                .last("LIMIT " + Math.max(limit * 5, limit));
        String kw = clean(keyword);
        if (!kw.isBlank()) {
            q.and(w -> w.like(PostPO::getTitle, kw).or().like(PostPO::getContent, kw));
        }
        if (type != null) {
            q.eq(PostPO::getPostType, type);
        }
        if (c > 0) {
            q.lt(PostPO::getCreateTime, LocalDateTime.ofInstant(Instant.ofEpochMilli(c), ZoneOffset.UTC));
        }

        List<PostPO> candidates = postMapper.selectList(q);
        if (candidates.isEmpty()) {
            return PageResult.empty();
        }
        Map<Long, String> extByPostId = loadExtJson(candidates.stream().map(PostPO::getId).toList());
        List<PostPO> filtered = candidates.stream()
                .filter(p -> matchExt(extByPostId.get(p.getId()), company, position))
                .toList();
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
        items = enrich(items);
        if ("hot".equals(sort)) {
            items = items.stream()
                    .sorted(Comparator.comparingDouble(this::hotScore).reversed()
                            .thenComparing(PostBriefDTO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        }
        items = items.stream().limit(limit).toList();
        String next = hasMore
                ? String.valueOf(items.get(items.size() - 1).getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli())
                : null;
        return PageResult.of(items, next, hasMore);
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

    private boolean matchExt(String extJson, String company, String position) {
        String companyFilter = clean(company);
        String positionFilter = clean(position);
        if (companyFilter.isBlank() && positionFilter.isBlank()) {
            return true;
        }
        JsonNode ext = parseExt(extJson);
        String companyValue = clean(ext.path("company").asText(""));
        String positionValue = clean(ext.path("position").asText(""));
        return (companyFilter.isBlank() || companyValue.contains(companyFilter))
                && (positionFilter.isBlank() || positionValue.equals(positionFilter));
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
        return tagMapper.selectTagsByPostIds(postIds).stream()
                .collect(Collectors.groupingBy(PostTagView::getPostId,
                        Collectors.mapping(this::toTagDto, Collectors.toList())));
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

    private long parseCursor(String c) {
        if (c == null || c.isBlank()) return 0L;
        try {
            return Long.parseLong(c);
        } catch (Exception e) {
            return 0L;
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
        if (!text.isBlank()) {
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
