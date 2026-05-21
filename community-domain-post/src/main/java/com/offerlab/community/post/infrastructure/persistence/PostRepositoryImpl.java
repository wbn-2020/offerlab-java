package com.offerlab.community.post.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostExtensionMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
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
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepository {

    private final PostMapper postMapper;
    private final PostExtensionMapper extMapper;
    private final PostCounterMapper counterMapper;

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
        if (ids == null || ids.isEmpty()) return Map.of();
        List<PostPO> posts = postMapper.selectBatchIds(ids);
        if (posts.isEmpty()) return Map.of();
        Map<Long, PostExtensionPO> exts = extMapper.selectBatchIds(ids).stream()
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
        po.setVersion(null);
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
        LambdaQueryWrapper<PostPO> q = new LambdaQueryWrapper<PostPO>()
                .eq(PostPO::getAuthorId, authorId)
                .eq(PostPO::getPostStatus, Post.STATUS_PUBLISHED)
                .orderByDesc(PostPO::getCreateTime)
                .last("LIMIT " + size);
        if (cursor > 0) {
            q.lt(PostPO::getCreateTime, LocalDateTime.ofInstant(Instant.ofEpochMilli(cursor), ZoneOffset.UTC));
        }
        return postMapper.selectList(q).stream().map(p -> toDomain(p, null)).toList();
    }

    @Override
    public List<Post> findLatest(long cursor, int size) {
        LambdaQueryWrapper<PostPO> q = new LambdaQueryWrapper<PostPO>()
                .eq(PostPO::getPostStatus, Post.STATUS_PUBLISHED)
                .eq(PostPO::getVisibility, Post.VIS_PUBLIC)
                .orderByDesc(PostPO::getCreateTime)
                .last("LIMIT " + size);
        if (cursor > 0) {
            q.lt(PostPO::getCreateTime, LocalDateTime.ofInstant(Instant.ofEpochMilli(cursor), ZoneOffset.UTC));
        }
        return postMapper.selectList(q).stream().map(p -> toDomain(p, null)).toList();
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
                .build();
    }
}
