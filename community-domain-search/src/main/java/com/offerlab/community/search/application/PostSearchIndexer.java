package com.offerlab.community.search.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostExtensionMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostCounterPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostExtensionPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import com.offerlab.community.post.infrastructure.persistence.projection.PostTagView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostSearchIndexer {

    private static final int REBUILD_BATCH_SIZE = 500;
    private static final int MYSQL_FALLBACK_MAX_SCAN = 200;

    private final ElasticsearchHttpClient elasticsearch;
    private final PostMapper postMapper;
    private final PostExtensionMapper extensionMapper;
    private final PostCounterMapper counterMapper;
    private final TagMapper tagMapper;
    private final ObjectMapper objectMapper;
    private final MigrationCheckService migrationCheckService;

    private final AtomicBoolean indexReady = new AtomicBoolean(false);

    public boolean ensurePostIndex() {
        if (!elasticsearch.enabled() || !elasticsearch.available()) {
            indexReady.set(false);
            return false;
        }
        if (indexReady.get() && elasticsearch.indexExists(elasticsearch.postIndex())) {
            return true;
        }
        if (elasticsearch.indexExists(elasticsearch.postIndex())) {
            boolean mapped = ensureCommunityFieldMapping();
            indexReady.set(mapped);
            return mapped;
        }
        boolean created = elasticsearch.createIndex(elasticsearch.postIndex(), postIndexMapping());
        indexReady.set(created);
        return created;
    }

    public boolean indexPost(Long postId) {
        if (postId == null || !ensurePostIndex()) {
            return false;
        }
        PostPO post = postMapper.selectById(postId);
        if (post == null) {
            return deletePostDocument(postId);
        }
        if (!Integer.valueOf(Post.STATUS_PUBLISHED).equals(post.getPostStatus())
                || !Integer.valueOf(Post.VIS_PUBLIC).equals(post.getVisibility())) {
            return deletePostDocument(postId);
        }
        boolean ok = elasticsearch.indexDocument(elasticsearch.postIndex(), String.valueOf(postId), toDocument(post));
        if (ok) {
            log.debug("post indexed to elasticsearch: postId={}", postId);
        }
        return ok;
    }

    public boolean deletePost(Long postId) {
        if (postId == null || !ensurePostIndex()) {
            return false;
        }
        return deletePostDocument(postId);
    }

    private boolean deletePostDocument(Long postId) {
        boolean ok = elasticsearch.deleteDocument(elasticsearch.postIndex(), String.valueOf(postId));
        if (ok) {
            log.debug("post deleted from elasticsearch: postId={}", postId);
        }
        return ok;
    }

    public Map<String, Object> status() {
        boolean enabled = elasticsearch.enabled();
        boolean available = elasticsearch.available();
        boolean ensured = enabled && available && ensurePostIndex();
        boolean exists = available && elasticsearch.indexExists(elasticsearch.postIndex());
        if (!exists) {
            indexReady.set(false);
        }
        boolean indexUsable = ensured && indexReady.get() && exists;
        DbFallbackStatus fallback = dbFallbackStatus();
        boolean publicSearchAvailable = indexUsable || fallback.available();
        boolean publicSearchDegraded = publicSearchAvailable && !indexUsable;
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", indexUsable ? "UP" : fallback.available() ? "DEGRADED" : "DOWN");
        status.put("enabled", enabled);
        status.put("available", available);
        status.put("indexName", elasticsearch.postIndex());
        status.put("indexExists", exists);
        status.put("indexReady", indexReady.get() && exists);
        status.put("publicSearchAvailable", publicSearchAvailable);
        status.put("publicSearchDegraded", publicSearchDegraded);
        status.put("publicSearchSource", indexUsable ? "elasticsearch" : fallback.available() ? "mysql" : "unavailable");
        status.put("dbFallbackAvailable", fallback.available());
        status.put("fallbackSource", "mysql");
        status.put("fallbackMode", fallback.mode());
        status.put("fallbackScanLimit", MYSQL_FALLBACK_MAX_SCAN);
        status.put("fallbackSchemaReady", fallback.schemaReady());
        status.put("message", searchStatusMessage(indexUsable, fallback));
        status.put("diagnosticMessage", searchStatusDiagnostic(indexUsable, fallback));
        if (!indexUsable) {
            status.put("action", searchStatusAction(fallback));
        }
        return status;
    }

    private DbFallbackStatus dbFallbackStatus() {
        try {
            boolean tagGovernanceReady = migrationCheckService.tagGovernanceReady();
            return new DbFallbackStatus(true, tagGovernanceReady,
                    tagGovernanceReady ? "tag_governance" : "compat");
        } catch (Exception ex) {
            return new DbFallbackStatus(false, false, "unavailable");
        }
    }

    private String searchStatusMessage(boolean indexUsable, DbFallbackStatus fallback) {
        if (indexUsable) {
            return "搜索索引已就绪，新发布内容会优先进入实时搜索。";
        }
        if (fallback.available()) {
            return "搜索暂时使用数据库降级结果，召回完整性和排序可能受限。";
        }
        return "搜索服务暂不可用，请稍后重试或从发现页、问答页继续浏览。";
    }

    private String searchStatusDiagnostic(boolean indexUsable, DbFallbackStatus fallback) {
        if (indexUsable) {
            return "Public search is using Elasticsearch index.";
        }
        if (fallback.available()) {
            return "Public search is using MySQL fallback because Elasticsearch is unavailable or index is not ready.";
        }
        return "Public search is unavailable because Elasticsearch and MySQL fallback are unavailable.";
    }

    private String searchStatusAction(DbFallbackStatus fallback) {
        if (!fallback.available()) {
            return "Check database connectivity and Elasticsearch readiness before signing off search.";
        }
        if (!fallback.schemaReady()) {
            return "Restore Elasticsearch and apply tag governance migration to enable full tag synonym recall.";
        }
        return "Restore Elasticsearch and replay search index retry tasks after the index is healthy.";
    }

    private record DbFallbackStatus(boolean available, boolean schemaReady, String mode) {
    }

    public Map<String, Object> rebuildAll() {
        if (!ensurePostIndex()) {
            return Map.of(
                    "accepted", false,
                    "indexed", 0,
                    "failed", 0,
                    "message", "Elasticsearch is unavailable or index creation failed"
            );
        }
        int indexed = 0;
        int failed = 0;
        int total = 0;
        long lastId = 0L;
        while (true) {
            List<PostPO> posts = postMapper.selectPublicPostsForIndexAfterId(lastId, REBUILD_BATCH_SIZE);
            if (posts == null || posts.isEmpty()) {
                break;
            }
            List<Long> postIds = posts.stream()
                    .map(PostPO::getId)
                    .filter(id -> id != null && id > 0)
                    .toList();
            Map<Long, PostExtensionPO> extensions = extensionMapper.selectBatchIds(postIds).stream()
                    .collect(Collectors.toMap(PostExtensionPO::getPostId, extension -> extension, (left, right) -> left));
            Map<Long, PostCounterPO> counters = counterMapper.selectBatchIds(postIds).stream()
                    .collect(Collectors.toMap(PostCounterPO::getPostId, counter -> counter, (left, right) -> left));
            Map<Long, List<TagDTO>> tags = selectTagsByPostIds(postIds).stream()
                    .collect(Collectors.groupingBy(PostTagView::getPostId,
                            Collectors.mapping(this::toTagDto, Collectors.toList())));
            total += posts.size();
            for (PostPO post : posts) {
                if (post.getId() != null && post.getId() > lastId) {
                    lastId = post.getId();
                }
                if (elasticsearch.indexDocument(elasticsearch.postIndex(), String.valueOf(post.getId()),
                        toDocument(post,
                                extensions.get(post.getId()),
                                counters.get(post.getId()),
                                tags.getOrDefault(post.getId(), List.of())))) {
                    indexed++;
                } else {
                    failed++;
                }
            }
            if (posts.size() < REBUILD_BATCH_SIZE) {
                break;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", failed == 0);
        result.put("indexed", indexed);
        result.put("failed", failed);
        result.put("total", total);
        result.put("indexName", elasticsearch.postIndex());
        if (failed > 0) {
            result.put("message", failed + " post documents failed to index");
        }
        return result;
    }

    private Map<String, Object> toDocument(PostPO post) {
        PostExtensionPO extension = extensionMapper.selectById(post.getId());
        PostCounterPO counter = counterMapper.selectById(post.getId());
        List<TagDTO> tags = selectTagsByPostIds(List.of(post.getId())).stream()
                .map(this::toTagDto)
                .toList();
        return toDocument(post, extension, counter, tags);
    }

    private Map<String, Object> toDocument(PostPO post, PostExtensionPO extension, PostCounterPO counter, List<TagDTO> tags) {
        JsonNode ext = parseExt(extension == null ? null : extension.getExtJson());
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", String.valueOf(post.getId()));
        doc.put("postId", post.getId());
        doc.put("authorId", String.valueOf(post.getAuthorId()));
        doc.put("type", post.getPostType());
        doc.put("title", nullToEmpty(post.getTitle()));
        doc.put("content", nullToEmpty(post.getContent()));
        doc.put("summary", summary(post.getContent()));
        doc.put("coverUrl", post.getCoverUrl());
        doc.put("extJson", extension == null ? null : extension.getExtJson());
        doc.put("company", ext.path("company").asText(""));
        doc.put("position", ext.path("position").asText(""));
        doc.put("difficulty", ext.path("difficulty").asText(""));
        doc.put("scenario", ext.path("scenario").asText(""));
        doc.put("contentType", ext.path("contentType").asText(""));
        doc.put("techStacks", textArray(ext.path("techStacks")));
        doc.put("yearsOfExp", ext.path("yearsOfExp").isNumber() ? ext.path("yearsOfExp").asInt() : null);
        doc.put("interviewResult", ext.path("interviewResult").asText(""));
        doc.put("tags", tags.stream().map(this::toTagDocument).toList());
        List<String> tagNames = uniqueText(tags.stream().map(TagDTO::getName).collect(Collectors.toList()));
        List<String> tagSynonyms = uniqueText(tags.stream()
                .flatMap(tag -> tag.getSynonyms() == null ? List.<String>of().stream() : tag.getSynonyms().stream())
                .collect(Collectors.toList()));
        List<String> tagSearchTerms = new ArrayList<>();
        tagSearchTerms.addAll(tagNames);
        tagSearchTerms.addAll(tagSynonyms);
        doc.put("tagNames", tagNames);
        doc.put("tagSynonyms", tagSynonyms);
        doc.put("tagSearchTerms", uniqueText(tagSearchTerms));
        doc.put("createTime", post.getCreateTime() == null ? 0L : post.getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli());
        doc.put("updateTime", post.getUpdateTime() == null ? 0L : post.getUpdateTime().toInstant(ZoneOffset.UTC).toEpochMilli());
        doc.put("likeCount", counter == null || counter.getLikeCount() == null ? 0L : counter.getLikeCount());
        doc.put("commentCount", counter == null || counter.getCommentCount() == null ? 0L : counter.getCommentCount());
        doc.put("viewCount", counter == null || counter.getViewCount() == null ? 0L : counter.getViewCount());
        doc.put("favoriteCount", counter == null || counter.getFavoriteCount() == null ? 0L : counter.getFavoriteCount());
        doc.put("status", "published");
        doc.put("visibility", post.getVisibility());
        return doc;
    }

    private Map<String, Object> toTagDocument(TagDTO tag) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", tag.getId());
        doc.put("name", tag.getName());
        doc.put("slug", tag.getSlug());
        doc.put("category", tag.getCategory());
        doc.put("tagType", tag.getTagType());
        doc.put("useCount", tag.getUseCount());
        doc.put("official", Boolean.TRUE.equals(tag.getOfficial()));
        doc.put("synonyms", uniqueText(tag.getSynonyms()));
        return doc;
    }

    private Map<String, Object> postIndexMapping() {
        Map<String, Object> keyword = Map.of("type", "keyword");
        Map<String, Object> text = Map.of("type", "text", "fields", Map.of("keyword", keyword));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", keyword);
        props.put("postId", Map.of("type", "long"));
        props.put("authorId", keyword);
        props.put("type", Map.of("type", "integer"));
        props.put("title", text);
        props.put("content", Map.of("type", "text"));
        props.put("summary", Map.of("type", "text"));
        props.put("coverUrl", keyword);
        props.put("extJson", Map.of("type", "keyword", "index", false));
        props.put("company", text);
        props.put("position", keyword);
        props.put("difficulty", keyword);
        props.put("scenario", text);
        props.put("contentType", keyword);
        props.put("techStacks", text);
        props.put("yearsOfExp", Map.of("type", "integer"));
        props.put("interviewResult", keyword);
        props.put("tagNames", keyword);
        props.put("tagSynonyms", text);
        props.put("tagSearchTerms", text);
        props.put("createTime", Map.of("type", "date", "format", "epoch_millis"));
        props.put("updateTime", Map.of("type", "date", "format", "epoch_millis"));
        props.put("likeCount", Map.of("type", "long"));
        props.put("commentCount", Map.of("type", "long"));
        props.put("viewCount", Map.of("type", "long"));
        props.put("favoriteCount", Map.of("type", "long"));
        props.put("status", keyword);
        props.put("visibility", Map.of("type", "integer"));
        props.put("tags", Map.of("type", "nested", "properties", Map.of(
                "id", Map.of("type", "long"),
                "name", keyword,
                "slug", keyword,
                "category", keyword,
                "tagType", Map.of("type", "integer"),
                "useCount", Map.of("type", "long"),
                "official", Map.of("type", "boolean"),
                "synonyms", text
        )));

        return Map.of(
                "settings", Map.of(
                        "number_of_shards", 1,
                        "number_of_replicas", 0,
                        "refresh_interval", "1s"
                ),
                "mappings", Map.of("dynamic", "strict", "properties", props)
        );
    }

    private boolean ensureCommunityFieldMapping() {
        return elasticsearch.updateMapping(elasticsearch.postIndex(), communityFieldProperties());
    }

    private Map<String, Object> communityFieldProperties() {
        Map<String, Object> keyword = Map.of("type", "keyword");
        Map<String, Object> text = Map.of("type", "text", "fields", Map.of("keyword", keyword));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("difficulty", keyword);
        props.put("scenario", text);
        props.put("contentType", keyword);
        props.put("techStacks", text);
        props.put("tagSynonyms", text);
        props.put("tagSearchTerms", text);
        props.put("tags", Map.of("type", "nested", "properties", Map.of("synonyms", text)));
        return props;
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

    private List<PostTagView> selectTagsByPostIds(List<Long> postIds) {
        return migrationCheckService.tagGovernanceReady()
                ? tagMapper.selectTagsByPostIds(postIds)
                : tagMapper.selectTagsByPostIdsCompat(postIds);
    }

    private static List<String> parseSynonyms(String synonyms) {
        if (synonyms == null || synonyms.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String raw : synonyms.split("[,\\uFF0C\\u3001/;\\uFF1B\\r\\n]+")) {
            String value = raw == null ? "" : raw.trim();
            if (!value.isBlank()) {
                result.add(value);
            }
        }
        return uniqueText(result);
    }

    private static List<String> uniqueText(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String raw : values) {
            String value = raw == null ? "" : raw.trim();
            if (!value.isBlank() && !result.contains(value)) {
                result.add(value);
            }
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
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String summary(String content) {
        if (content == null) return "";
        String s = content.replaceAll("[#*`>\\[\\]()_!~\\-]+", " ").trim();
        return s.length() <= 120 ? s : s.substring(0, 120) + "...";
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
