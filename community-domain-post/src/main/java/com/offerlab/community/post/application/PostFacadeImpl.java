package com.offerlab.community.post.application;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.redis.cache.CacheKeyBuilder;
import com.offerlab.community.infra.redis.cache.MultiLevelCache;
import com.offerlab.community.infra.redis.cache.PostCounterRedis;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostCounterDTO;
import com.offerlab.community.post.api.dto.PostCreateCmd;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.PostUpdateCmd;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostCounterPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PostFacadeImpl implements PostFacade {

    private final PostRepository postRepo;
    private final PostCounterMapper counterMapper;
    private final PostCounterRedis postCounterRedis;
    private final MultiLevelCache<PostDTO> multiLevelCache;
    private final PostApplicationService postService;

    private static final int SUMMARY_LEN = 120;

    @Override
    public PostDTO getPost(Long postId) {
        String cacheKey = CacheKeyBuilder.postDetail(postId);
        return multiLevelCache.get(cacheKey, key -> {
            Post post = postRepo.findById(postId).orElse(null);
            return post != null ? toFullDto(post) : null;
        }, PostDTO.class);
    }

    @Override
    public Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) return Map.of();
        Map<Long, Post> posts = postRepo.batchFindByIds(postIds);
        Map<Long, PostBriefDTO> result = new HashMap<>(posts.size());
        for (Post p : posts.values()) {
            result.put(p.getId(), toBrief(p));
        }
        return result;
    }

    @Override
    public Map<Long, PostCounterDTO> batchGetCounters(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) return Map.of();

        // 先从 Redis 查
        Map<Long, PostCounterDTO> result = postCounterRedis.batchGet(postIds);

        // 找出 Redis 中缺失的 postId
        List<Long> missingIds = postIds.stream()
                .filter(id -> !result.containsKey(id))
                .toList();

        // 从 DB 加载缺失的数据并回填 Redis
        if (!missingIds.isEmpty()) {
            List<PostCounterPO> dbList = counterMapper.selectBatchIds(missingIds);
            for (PostCounterPO c : dbList) {
                PostCounterDTO dto = PostCounterDTO.builder()
                        .postId(c.getPostId())
                        .viewCount(c.getViewCount())
                        .likeCount(c.getLikeCount())
                        .commentCount(c.getCommentCount())
                        .favoriteCount(c.getFavoriteCount())
                        .build();
                result.put(c.getPostId(), dto);
                // 回填 Redis
                postCounterRedis.fillFromDb(c.getPostId(), c.getViewCount(), c.getLikeCount(),
                        c.getCommentCount(), c.getFavoriteCount(), 0L);
            }
        }

        return result;
    }

    @Override
    public Long publishPost(PostCreateCmd cmd) {
        return postService.publish(cmd);
    }

    @Override
    public void updatePost(PostUpdateCmd cmd) {
        postService.update(cmd);
    }

    @Override
    public void deletePost(Long postId, Long operatorUid) {
        postService.delete(postId, operatorUid);
    }

    @Override
    public PageResult<PostBriefDTO> getPostsByAuthor(Long authorId, long cursor, int size) {
        List<Post> list = postRepo.findByAuthor(authorId, cursor, size);
        return paged(list, size);
    }

    @Override
    public PageResult<PostBriefDTO> getLatest(long cursor, int size) {
        List<Post> list = postRepo.findLatest(cursor, size);
        return paged(list, size);
    }

    private PageResult<PostBriefDTO> paged(List<Post> list, int size) {
        if (list.isEmpty()) return PageResult.empty();
        List<PostBriefDTO> items = list.stream().map(this::toBrief).toList();
        boolean hasMore = list.size() == size;
        String next = hasMore
                ? String.valueOf(list.get(list.size() - 1).getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli())
                : null;
        return PageResult.of(items, next, hasMore);
    }

    private PostBriefDTO toBrief(Post p) {
        return PostBriefDTO.builder()
                .id(p.getId())
                .authorId(p.getAuthorId())
                .postType(p.getPostType())
                .title(p.getTitle())
                .summary(summary(p.getContent()))
                .coverUrl(p.getCoverUrl())
                .extJson(p.getExtJson())
                .createTime(p.getCreateTime())
                .build();
    }

    private PostDTO toFullDto(Post p) {
        return PostDTO.builder()
                .id(p.getId())
                .authorId(p.getAuthorId())
                .postType(p.getPostType())
                .title(p.getTitle())
                .content(p.getContent())
                .coverUrl(p.getCoverUrl())
                .visibility(p.getVisibility())
                .postStatus(p.getPostStatus())
                .extJson(p.getExtJson())
                .createTime(p.getCreateTime())
                .updateTime(p.getUpdateTime())
                .build();
    }

    private static String summary(String content) {
        if (content == null) return "";
        String s = content.replaceAll("[#*`>\\[\\]()_!~\\-]+", " ").trim();
        return s.length() <= SUMMARY_LEN ? s : s.substring(0, SUMMARY_LEN) + "...";
    }
}
