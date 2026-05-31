package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.redis.cache.CacheKeyBuilder;
import com.offerlab.community.infra.redis.cache.MultiLevelCache;
import com.offerlab.community.infra.redis.cache.PostCounterRedis;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostCounterDTO;
import com.offerlab.community.post.api.dto.PostCreateCmd;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.PostUpdateCmd;
import com.offerlab.community.post.api.dto.PostVersionHistoryDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostExtensionMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostCounterPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostExtensionPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import com.offerlab.community.post.infrastructure.persistence.po.TagPO;
import com.offerlab.community.post.infrastructure.persistence.projection.PostTagView;
import com.offerlab.community.user.api.UserFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostFacadeImpl implements PostFacade {

    private final PostRepository postRepo;
    private final PostMapper postMapper;
    private final PostExtensionMapper extensionMapper;
    private final PostCounterMapper counterMapper;
    private final TagMapper tagMapper;
    private final PostCounterRedis postCounterRedis;
    private final PostVersionHistoryService versionHistoryService;
    private final MultiLevelCache<PostDTO> multiLevelCache;
    private final PostApplicationService postService;
    private final UserFacade userFacade;

    private static final int SUMMARY_LEN = 120;

    @Override
    public PostDTO getPost(Long postId) {
        return getPost(postId, UserContext.get());
    }

    @Override
    public PostDTO getPost(Long postId, Long viewerUid) {
        String cacheKey = CacheKeyBuilder.postDetailRaw(postId);
        PostDTO dto = multiLevelCache.get(cacheKey, key -> {
            Post post = postRepo.findById(postId).orElse(null);
            return post == null ? null : toFullDto(post);
        }, PostDTO.class);
        if (!isVisible(dto, viewerUid)) {
            return null;
        }
        return enrichFull(dto);
    }

    @Override
    public Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) return Map.of();
        Map<Long, Post> posts = postRepo.batchFindByIds(postIds);
        Map<Long, List<TagDTO>> tags = tagsByPostIds(posts.keySet());
        Map<Long, PostBriefDTO> result = new HashMap<>(posts.size());
        for (Post p : posts.values()) {
            if (p.isVisibleTo(null, false)) {
                result.put(p.getId(), toBrief(p, tags.getOrDefault(p.getId(), List.of())));
            }
        }
        enrichBriefs(result.values());
        return result;
    }

    @Override
    public Map<Long, PostCounterDTO> batchGetCounters(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) return Map.of();

        // 先从 Redis 查
        Map<Long, PostCounterDTO> result = new HashMap<>(postIds.size());
        Map<Long, PostCounterRedis.CounterValue> redisCounters = postCounterRedis.batchGet(postIds);
        for (PostCounterRedis.CounterValue value : redisCounters.values()) {
            result.put(value.postId(), toCounterDto(value));
        }

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
        // 帖子正文、可见性和标签都可能变化，更新成功后必须清理详情缓存。


        evictPostDetail(cmd.getPostId());
    }

    @Override
    public void deletePost(Long postId, Long operatorUid) {
        postService.delete(postId, operatorUid);
        // 删除走逻辑删除，仍需清详情缓存，避免旧内容继续对外可见。
        evictPostDetail(postId);
    }

    @Override
    public PageResult<PostBriefDTO> getPostsByAuthor(Long authorId, long cursor, int size) {
        return listPosts(authorId, null, null, cursor, size);
    }

    @Override
    public List<PostVersionHistoryDTO> listPostVersions(Long postId, Long viewerUid, boolean moderator, int limit) {
        if (postId == null || postId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Post post = postRepo.findById(postId).orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        if (!moderator && !post.getAuthorId().equals(viewerUid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return versionHistoryService.listRecent(postId, limit);
    }


    @Override
    public PageResult<PostBriefDTO> getLatest(long cursor, int size) {
        return listPosts(null, null, null, cursor, size);
    }

    @Override
    public PageResult<PostBriefDTO> getHot(long cursor, int size) {
        int limit = pageSize(size);
        LocalDateTime cursorTime = cursor > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(cursor), ZoneOffset.UTC)
                : null;
        return pagedPo(postMapper.selectHotPosts(cursorTime, limit + 1), limit);
    }

    @Override
    public PageResult<PostBriefDTO> listPosts(Long authorId, Long tagId, Integer postType, long cursor, int size) {
        int limit = pageSize(size);
        List<Post> list = postRepo.findPosts(authorId, tagId, postType, cursor, limit + 1);
        return paged(list, limit);
    }

    @Override
    public List<TagDTO> listTags() {
        return tagMapper.selectActiveTags().stream().map(this::toTagDto).toList();
    }

    @Override
    public PageResult<PostBriefDTO> getPostsByTag(Long tagId, long cursor, int size) {
        return listPosts(null, tagId, null, cursor, size);
    }

    private PageResult<PostBriefDTO> paged(List<Post> list, int size) {
        if (list.isEmpty()) return PageResult.empty();
        boolean hasMore = list.size() > size;
        List<Post> pageList = hasMore ? list.subList(0, size) : list;
        Map<Long, List<TagDTO>> tags = tagsByPostIds(pageList.stream().map(Post::getId).toList());
        List<PostBriefDTO> items = pageList.stream().map(p -> toBrief(p, tags.getOrDefault(p.getId(), List.of()))).toList();
        enrichBriefs(items);
        // 普通列表使用 createTime 毫秒时间戳作为游标，前端需原样传回。
        String next = hasMore && !pageList.isEmpty()
                ? String.valueOf(pageList.get(pageList.size() - 1).getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli())
                : null;
        return PageResult.of(items, next, hasMore);
    }

    private PageResult<PostBriefDTO> pagedPo(List<PostPO> list, int size) {
        if (list.isEmpty()) return PageResult.empty();
        boolean hasMore = list.size() > size;
        List<PostPO> pageList = hasMore ? list.subList(0, size) : list;
        List<Long> postIds = pageList.stream().map(PostPO::getId).toList();
        Map<Long, List<TagDTO>> tags = tagsByPostIds(postIds);
        // 热榜直接查 PO，需要额外批量取扩展字段，避免逐条查询扩展表。
        Map<Long, String> extJson = extensionMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(PostExtensionPO::getPostId, PostExtensionPO::getExtJson, (a, b) -> a));
        List<PostBriefDTO> items = pageList.stream()
                .map(p -> PostBriefDTO.builder()
                        .id(p.getId())
                        .authorId(p.getAuthorId())
                        .postType(p.getPostType())
                        .title(p.getTitle())
                        .summary(summary(p.getContent()))
                        .coverUrl(p.getCoverUrl())
                        .extJson(extJson.get(p.getId()))
                        .tags(tags.getOrDefault(p.getId(), List.of()))
                        .createTime(p.getCreateTime())
                        .build())
                .toList();
        enrichBriefs(items);
        String next = hasMore && !pageList.isEmpty()
                ? String.valueOf(pageList.get(pageList.size() - 1).getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli())
                : null;
        return PageResult.of(items, next, hasMore);
    }

    private int pageSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }

    private PostBriefDTO toBrief(Post p) {
        return toBrief(p, List.of());
    }

    private PostBriefDTO toBrief(Post p, List<TagDTO> tags) {
        return PostBriefDTO.builder()
                .id(p.getId())
                .authorId(p.getAuthorId())
                .postType(p.getPostType())
                .title(p.getTitle())
                .summary(summary(p.getContent()))
                .coverUrl(p.getCoverUrl())
                .extJson(p.getExtJson())
                .tags(tags)
                .createTime(p.getCreateTime())
                .build();
    }

    private void enrichBriefs(Collection<PostBriefDTO> posts) {
        if (posts == null || posts.isEmpty()) {
            return;
        }
        List<Long> postIds = posts.stream().map(PostBriefDTO::getId).toList();
        Map<Long, PostCounterDTO> counters = batchGetCounters(postIds);
        Map<Long, com.offerlab.community.user.api.dto.UserBriefDTO> authors = userFacade.batchGetUserBriefs(
                posts.stream().map(PostBriefDTO::getAuthorId).collect(Collectors.toSet()));
        posts.forEach(p -> {
            p.setCounter(counters.getOrDefault(p.getId(), emptyCounter(p.getId())));
            p.setAuthor(authors.get(p.getAuthorId()));
        });
    }

    private PostDTO enrichFull(PostDTO dto) {
        if (dto == null) {
            return null;
        }
        return PostDTO.builder()
                .id(dto.getId())
                .authorId(dto.getAuthorId())
                .author(userFacade.getUserBrief(dto.getAuthorId()))
                .postType(dto.getPostType())
                .title(dto.getTitle())
                .content(dto.getContent())
                .coverUrl(dto.getCoverUrl())
                .visibility(dto.getVisibility())
                .postStatus(dto.getPostStatus())
                .extJson(dto.getExtJson())
                .tags(dto.getTags())
                .counter(batchGetCounters(List.of(dto.getId())).getOrDefault(dto.getId(), emptyCounter(dto.getId())))
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    private boolean isVisible(PostDTO dto, Long viewerUid) {
        if (dto == null || dto.getPostStatus() == null || dto.getPostStatus() != Post.STATUS_PUBLISHED) {
            return false;
        }
        Integer visibility = dto.getVisibility();
        if (visibility == null || visibility == Post.VIS_PUBLIC) {
            return true;
        }
        if (viewerUid == null) {
            return false;
        }
        if (Objects.equals(dto.getAuthorId(), viewerUid)) {
            return true;
        }
        return visibility == Post.VIS_FOLLOWER && userFacade.isFollowing(viewerUid, dto.getAuthorId());
    }

    private void evictPostDetail(Long postId) {
        multiLevelCache.evict(CacheKeyBuilder.postDetail(postId));
        multiLevelCache.evict(CacheKeyBuilder.postDetailRaw(postId));
    }

    private PostDTO toFullDto(Post p) {
        List<TagDTO> tags = tagsByPostIds(List.of(p.getId())).getOrDefault(p.getId(), List.of());
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
                .tags(tags)
                .createTime(p.getCreateTime())
                .updateTime(p.getUpdateTime())
                .build();
    }

    private PostCounterDTO toCounterDto(PostCounterRedis.CounterValue value) {
        return PostCounterDTO.builder()
                .postId(value.postId())
                .viewCount(value.viewCount())
                .likeCount(value.likeCount())
                .commentCount(value.commentCount())
                .favoriteCount(value.favoriteCount())
                .build();
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

    private Map<Long, List<TagDTO>> tagsByPostIds(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return tagMapper.selectTagsByPostIds(postIds).stream()
                .collect(Collectors.groupingBy(PostTagView::getPostId,
                        Collectors.mapping(this::toTagDto, Collectors.toList())));
    }

    private TagDTO toTagDto(TagPO tag) {
        return TagDTO.builder()
                .id(tag.getId())
                .name(tag.getTagName())
                .slug(toSlug(tag.getId(), tag.getTagName()))
                .category(toCategory(tag.getTagType()))
                .tagType(tag.getTagType())
                .useCount(tag.getUseCount())
                .official(tag.getIsOfficial() != null && tag.getIsOfficial() == 1)
                .build();
    }

    private TagDTO toTagDto(PostTagView tag) {
        return TagDTO.builder()
                .id(tag.getId())
                .name(tag.getTagName())
                .slug(toSlug(tag.getId(), tag.getTagName()))
                .category(toCategory(tag.getTagType()))
                .tagType(tag.getTagType())
                .useCount(tag.getUseCount())
                .official(tag.getIsOfficial() != null && tag.getIsOfficial() == 1)
                .build();
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

    private static String toSlug(Long id, String name) {
        if (name == null || name.isBlank()) {
            return String.valueOf(id);
        }
        String slug = name.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? String.valueOf(id) : slug;
    }

    private static String summary(String content) {
        if (content == null) return "";
        String s = content.replaceAll("[#*`>\\[\\]()_!~\\-]+", " ").trim();
        return s.length() <= SUMMARY_LEN ? s : s.substring(0, SUMMARY_LEN) + "...";
    }
}
