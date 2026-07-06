package com.offerlab.community.post.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerlab.community.common.utils.SqlLimits;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.model.PostDomain;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostExtensionMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostTagRefMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostExtensionPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepository {

    private static final int MAX_BATCH_FIND_IDS = 500;

    private final PostMapper postMapper;
    private final PostExtensionMapper extMapper;
    private final PostCounterMapper counterMapper;
    private final PostTagRefMapper postTagRefMapper;

    @Override
    @Transactional
    public void save(Post post) {
        PostPO po = toPO(post);
        postMapper.insert(po);
        if (post.getExtJson() != null) {
            PostExtensionPO ext = new PostExtensionPO();
            ext.setPostId(post.getId());
            ext.setPostType(post.getPostType());
            ext.setExtJson(post.getExtJson());
            extMapper.insert(ext);
        }
        counterMapper.initIfAbsent(post.getId());
    }

    @Override
    public Optional<Post> findById(Long id) {
        PostPO po = postMapper.selectById(id);
        if (po == null) return Optional.empty();
        PostExtensionPO ext = extMapper.selectById(id);
        return Optional.of(toDomain(po, ext));
    }

    @Override
    public Map<Long, Post> batchFindByIds(Collection<Long> ids) {
        List<Long> normalizedIds = normalizeBatchIds(ids);
        if (normalizedIds.isEmpty()) return Map.of();
        List<PostPO> posts = postMapper.selectBatchIds(normalizedIds);
        if (posts.isEmpty()) return Map.of();
        Map<Long, PostExtensionPO> exts = extMapper.selectBatchIds(normalizedIds).stream()
                .collect(Collectors.toMap(PostExtensionPO::getPostId, e -> e));
        Map<Long, Post> result = new HashMap<>(posts.size());
        for (PostPO po : posts) {
            result.put(po.getId(), toDomain(po, exts.get(po.getId())));
        }
        return result;
    }

    @Override
    @Transactional
    public void update(Post post) {
        PostPO po = toPO(post);
        po.setVersion(post.getVersion());
        postMapper.updateById(po);
        if (post.getExtJson() != null) {
            PostExtensionPO existing = extMapper.selectById(post.getId());
            PostExtensionPO ext = new PostExtensionPO();
            ext.setPostId(post.getId());
            ext.setPostType(post.getPostType());
            ext.setExtJson(post.getExtJson());
            if (existing == null) {
                extMapper.insert(ext);
            } else {
                extMapper.updateById(ext);
            }
        }
    }

    @Override
    public void softDelete(Long id) {
        postMapper.deleteById(id);
    }

    @Override
    public List<Post> findByAuthor(Long authorId, long cursor, int size) {
        int limit = listLimit(size);
        LambdaQueryWrapper<PostPO> q = new LambdaQueryWrapper<PostPO>()
                .eq(PostPO::getAuthorId, authorId)
                .eq(PostPO::getPostStatus, Post.STATUS_PUBLISHED)
                .eq(PostPO::getVisibility, Post.VIS_PUBLIC)
                .orderByDesc(PostPO::getCreateTime)
                .last(SqlLimits.limit(limit, 1, 101));
        if (cursor > 0) {
            q.lt(PostPO::getCreateTime, LocalDateTime.ofInstant(Instant.ofEpochMilli(cursor), ZoneOffset.UTC));
        }
        return toDomainListWithExt(postMapper.selectList(q));
    }

    @Override
    public List<Post> findLatest(long cursor, int size) {
        LambdaQueryWrapper<PostPO> q = baseListQuery(cursor, size);
        return toDomainListWithExt(postMapper.selectList(q));
    }

    @Override
    public List<Post> findPosts(Long authorId, Long tagId, Integer postType, Boolean featured, Integer domain, long cursor, int size) {
        List<PostPO> posts = postMapper.selectPublicPosts(authorId, tagId != null && tagId > 0 ? tagId : null, postType,
                featured, domain, cursorTime(cursor), cursorId(cursor), listLimit(size));
        return toDomainListWithExt(posts);
    }

    private List<Post> toDomainListWithExt(List<PostPO> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<Long> ids = posts.stream().map(PostPO::getId).toList();
        Map<Long, PostExtensionPO> exts = extMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(PostExtensionPO::getPostId, e -> e, (left, right) -> left));
        return posts.stream()
                .map(p -> toDomain(p, exts.get(p.getId())))
                .toList();
    }

    private static LambdaQueryWrapper<PostPO> baseListQuery(long cursor, int size) {
        int limit = listLimit(size);
        LambdaQueryWrapper<PostPO> q = new LambdaQueryWrapper<PostPO>()
                .eq(PostPO::getPostStatus, Post.STATUS_PUBLISHED)
                .eq(PostPO::getVisibility, Post.VIS_PUBLIC)
                .orderByDesc(PostPO::getCreateTime)
                .last(SqlLimits.limit(limit, 1, 101));
        if (cursor > 0) {
            q.lt(PostPO::getCreateTime, LocalDateTime.ofInstant(Instant.ofEpochMilli(cursor), ZoneOffset.UTC));
        }
        return q;
    }

    private static int listLimit(int size) {
        return Math.max(1, Math.min(size, 101));
    }

    private static List<Long> normalizeBatchIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .limit(MAX_BATCH_FIND_IDS)
                .toList();
    }

    private static LocalDateTime cursorTime(long cursor) {
        if (cursor <= 0) {
            return null;
        }
        long time = cursor > 10_000_000_000_000L ? cursor / 1_000_000L : cursor;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneOffset.UTC);
    }

    private static Long cursorId(long cursor) {
        if (cursor <= 0 || cursor <= 10_000_000_000_000L) {
            return Long.MAX_VALUE;
        }
        return cursor % 1_000_000L;
    }

    private static PostPO toPO(Post p) {
        PostPO po = new PostPO();
        po.setId(p.getId());
        po.setAuthorId(p.getAuthorId());
        po.setPostType(p.getPostType());
        po.setTitle(p.getTitle());
        po.setContent(p.getContent());
        po.setCoverUrl(p.getCoverUrl());
        po.setVisibility(p.getVisibility());
        po.setPostStatus(p.getPostStatus());
        return po;
    }

    private static Post toDomain(PostPO po, PostExtensionPO ext) {
        Integer domain = null;
        if (ext != null && ext.getExtJson() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(ext.getExtJson());
                if (root.has("domain") && root.get("domain").canConvertToInt()) {
                    domain = root.get("domain").asInt();
                }
            } catch (Exception e) {
                // ignore parse errors
            }
        }
        return Post.builder()
                .id(po.getId())
                .authorId(po.getAuthorId())
                .postType(po.getPostType())
                .title(po.getTitle())
                .content(po.getContent())
                .coverUrl(po.getCoverUrl())
                .visibility(po.getVisibility())
                .postStatus(po.getPostStatus())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .extJson(ext == null ? null : ext.getExtJson())
                .version(po.getVersion())
                .domain(PostDomain.fromCode(domain).getCode())
                .build();
    }
}
