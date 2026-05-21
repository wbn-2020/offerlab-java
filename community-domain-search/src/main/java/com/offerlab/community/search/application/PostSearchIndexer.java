package com.offerlab.community.search.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ElasticsearchHttpClient elasticsearch;
    private final PostMapper postMapper;
    private final PostExtensionMapper extensionMapper;
    private final PostCounterMapper counterMapper;
    private final TagMapper tagMapper;
    private final ObjectMapper objectMapper;

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
            indexReady.set(true);
            return true;
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
            return false;
        }
        if (!Integer.valueOf(Post.STATUS_PUBLISHED).equals(post.getPostStatus())
                || !Integer.valueOf(Post.VIS_PUBLIC).equals(post.getVisibility())) {
            return false;
        }
        boolean ok = elasticsearch.indexDocument(elasticsearch.postIndex(), String.valueOf(postId), toDocument(post));
        if (ok) {
            log.debug("post indexed to elasticsearch: postId={}", postId);
        }
        return ok;
    }

    public Map<String, Object> status() {
        boolean enabled = elasticsearch.enabled();
        boolean available = elasticsearch.available();
        boolean exists = available && elasticsearch.indexExists(elasticsearch.postIndex());
        if (!exists) {
            indexReady.set(false);
        }
        return Map.of(
                "enabled", enabled,
                "available", available,
                "indexName", elasticsearch.postIndex(),
                "indexExists", exists,
                "indexReady", indexReady.get() && exists
        );
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
        List<PostPO> posts = postMapper.selectList(new LambdaQueryWrapper<PostPO>()
                .eq(PostPO::getPostStatus, Post.STATUS_PUBLISHED)
                .eq(PostPO::getVisibility, Post.VIS_PUBLIC)
                .eq(PostPO::getIsDeleted, 0)
                .orderByAsc(PostPO::getId));
        int indexed = 0;
        int failed = 0;
        for (PostPO post : posts) {
            if (elasticsearch.indexDocument(elasticsearch.postIndex(), String.valueOf(post.getId()), toDocument(post))) {
                indexed++;
            } else {
                failed++;
            }
        }
        return Map.of(
                "accepted", true,
                "indexed", indexed,
                "failed", failed,
                "total", posts.size(),
                "indexName", elasticsearch.postIndex()
        );
    }

    private Map<String, Object> toDocument(PostPO post) {
        PostExtensionPO extension = extensionMapper.selectById(post.getId());
        PostCounterPO counter = counterMapper.selectById(post.getId());
        JsonNode ext = parseExt(extension == null ? null : extension.getExtJson());
        List<TagDTO> tags = tagMapper.selectTagsByPostIds(List.of(post.getId())).stream()
                .map(this::toTagDto)
                .toList();

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
        doc.put("yearsOfExp", ext.path("yearsOfExp").isNumber() ? ext.path("yearsOfExp").asInt() : null);
        doc.put("interviewResult", ext.path("interviewResult").asText(""));
        doc.put("tags", tags.stream().map(this::toTagDocument).toList());
        doc.put("tagNames", tags.stream().map(TagDTO::getName).collect(Collectors.toList()));
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
        props.put("yearsOfExp", Map.of("type", "integer"));
        props.put("interviewResult", keyword);
        props.put("tagNames", keyword);
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
                "official", Map.of("type", "boolean")
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
