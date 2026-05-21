package com.offerlab.community.search.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import com.offerlab.community.search.api.SearchFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * MVP 阶段：MySQL LIKE 简化搜索
 * 二期切到 Elasticsearch（详见 06-ES设计.md）
 */
@Service
@RequiredArgsConstructor
public class SearchFacadeImpl implements SearchFacade {

    private final PostMapper postMapper;

    @Override
    public PageResult<PostBriefDTO> searchPosts(String keyword, String cursor, int size) {
        if (keyword == null || keyword.isBlank()) return PageResult.empty();
        long c = parseCursor(cursor);
        int limit = Math.min(size <= 0 ? 20 : size, 50);

        LambdaQueryWrapper<PostPO> q = new LambdaQueryWrapper<PostPO>()
                .eq(PostPO::getPostStatus, Post.STATUS_PUBLISHED)
                .eq(PostPO::getVisibility, Post.VIS_PUBLIC)
                .and(w -> w.like(PostPO::getTitle, keyword)
                        .or().like(PostPO::getContent, keyword))
                .orderByDesc(PostPO::getCreateTime)
                .last("LIMIT " + limit);
        if (c > 0) {
            q.lt(PostPO::getCreateTime, LocalDateTime.ofInstant(Instant.ofEpochMilli(c), ZoneOffset.UTC));
        }
        List<PostPO> list = postMapper.selectList(q);
        if (list.isEmpty()) return PageResult.empty();

        List<PostBriefDTO> items = list.stream().map(p -> PostBriefDTO.builder()
                .id(p.getId())
                .authorId(p.getAuthorId())
                .postType(p.getPostType())
                .title(p.getTitle())
                .summary(summary(p.getContent()))
                .coverUrl(p.getCoverUrl())
                .createTime(p.getCreateTime())
                .build()).toList();

        boolean hasMore = list.size() == limit;
        String next = hasMore
                ? String.valueOf(list.get(list.size() - 1).getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli())
                : null;
        return PageResult.of(items, next, hasMore);
    }

    @Override
    public List<String> suggest(String prefix, int size) {
        return List.of();
    }

    @Override
    public List<String> getHotKeywords(int size) {
        return List.of("Java", "字节跳动", "面经", "Spring", "Redis", "Kafka");
    }

    private long parseCursor(String c) {
        if (c == null || c.isBlank()) return 0L;
        try { return Long.parseLong(c); } catch (Exception e) { return 0L; }
    }

    private static String summary(String content) {
        if (content == null) return "";
        String s = content.replaceAll("[#*`>\\[\\]()_!~\\-]+", " ").trim();
        return s.length() <= 120 ? s : s.substring(0, 120) + "...";
    }
}
