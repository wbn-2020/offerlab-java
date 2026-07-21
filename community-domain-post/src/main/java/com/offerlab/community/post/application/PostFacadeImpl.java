package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.redis.cache.CacheKeyBuilder;
import com.offerlab.community.infra.redis.cache.MultiLevelCache;
import com.offerlab.community.infra.redis.cache.PostCounterRedis;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.post.api.PublicContentFilter;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostCounterDTO;
import com.offerlab.community.post.api.dto.PostCreateCmd;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.PostTrustSignalsDTO;
import com.offerlab.community.post.api.dto.PostUpdateCmd;
import com.offerlab.community.post.api.dto.PostVersionHistoryDTO;
import com.offerlab.community.post.api.dto.PublicPostUpdateDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.model.PostDomain;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostExtensionMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostTrustSignalsMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostCounterPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostExtensionPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import com.offerlab.community.post.infrastructure.persistence.po.TagPO;
import com.offerlab.community.post.infrastructure.persistence.projection.PostTagView;
import com.offerlab.community.post.infrastructure.persistence.projection.PostTrustSignalsRow;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.ContactRequestPolicyCheckDTO;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.ZoneOffset;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final PostTrustSignalsMapper trustSignalsMapper;
    private final PostCounterRedis postCounterRedis;
    private final PostVersionHistoryService versionHistoryService;
    private final MultiLevelCache<PostDTO> multiLevelCache;
    private final PostApplicationService postService;
    private final UserFacade userFacade;
    private final MigrationCheckService migrationCheckService;
    private final AdminPermissionService adminPermissionService;

    private static final int SUMMARY_LEN = 120;
    private static final int MAX_SYNTHETIC_SCAN_ROWS = 1000;
    private static final int SYNTHETIC_SCAN_MULTIPLIER = 6;
    private static final int MAX_BATCH_LOOKUP_IDS = 500;
    private static final long ANONYMOUS_AUTHOR_ID = 0L;
    private static final ObjectMapper EXT_JSON_MAPPER = new ObjectMapper();

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
        return enrichFull(dto, viewerUid);
    }

    @Override
    public PostDTO getPostMetadata(Long postId) {
        if (postId == null || postId <= 0) {
            return null;
        }
        return postRepo.findById(postId).map(this::toFullDto).orElse(null);
    }

    @Override
    public PostDTO getPostForAuthor(Long postId, Long authorUid) {
        if (postId == null || postId <= 0 || authorUid == null || authorUid <= 0) {
            return null;
        }
        PostDTO dto = postRepo.findById(postId)
                .filter(post -> Objects.equals(post.getAuthorId(), authorUid))
                .map(this::toFullDto)
                .orElse(null);
        return dto == null ? null : enrichFull(dto, authorUid);
    }

    @Override
    public Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds) {
        return batchGetPosts(postIds, null);
    }

    @Override
    public Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds, Long viewerUid) {
        return batchGetPosts(postIds, viewerUid, false);
    }

    @Override
    public Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds, Long viewerUid, boolean includeTestData) {
        List<Long> normalizedIds = normalizeBatchIds(postIds, MAX_BATCH_LOOKUP_IDS);
        if (normalizedIds.isEmpty()) return Map.of();
        Map<Long, Post> posts = postRepo.batchFindByIds(normalizedIds);
        Map<Long, List<TagDTO>> tags = tagsByPostIds(posts.keySet());
        Map<Long, Boolean> followingByAuthor = viewerUid == null
                ? Map.of()
                : userFacade.batchIsFollowing(viewerUid, posts.values().stream()
                        .map(Post::getAuthorId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList());
        Map<Long, PostBriefDTO> result = new HashMap<>(posts.size());
        for (Post p : posts.values()) {
            boolean following = Boolean.TRUE.equals(followingByAuthor.get(p.getAuthorId()));
            if (p.isVisibleTo(viewerUid, following)) {
                result.put(p.getId(), toBrief(p, tags.getOrDefault(p.getId(), List.of())));
            }
        }
        enrichBriefs(result.values(), viewerUid);
        if (!includeTestData) {
            result.entrySet().removeIf(entry -> PublicContentFilter.isSyntheticPost(entry.getValue()));
        }
        return result;
    }

    @Override
    public Map<Long, PostCounterDTO> batchGetCounters(Collection<Long> postIds) {
        List<Long> normalizedIds = normalizeBatchIds(postIds, MAX_BATCH_LOOKUP_IDS);
        if (normalizedIds.isEmpty()) return Map.of();

        // First read Redis, then load only missing counters from DB.
        Map<Long, PostCounterDTO> result = new HashMap<>(normalizedIds.size());
        Map<Long, PostCounterRedis.CounterValue> redisCounters = postCounterRedis.batchGet(normalizedIds);
        for (PostCounterRedis.CounterValue value : redisCounters.values()) {
            result.put(value.postId(), toCounterDto(value));
        }

        List<Long> missingIds = normalizedIds.stream()
                .filter(id -> !result.containsKey(id))
                .toList();

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
                postCounterRedis.fillFromDb(c.getPostId(), c.getViewCount(), c.getLikeCount(),
                        c.getCommentCount(), c.getFavoriteCount(), 0L);
            }
        }

        return result;
    }

    @Override
    public Map<Long, Long> batchCountPublicPublishedPostsByAuthors(Collection<Long> authorIds) {
        List<Long> normalizedIds = normalizeBatchIds(authorIds, MAX_BATCH_LOOKUP_IDS);
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>(normalizedIds.size());
        for (Map<String, Object> row : postMapper.countPublicPublishedPostsByAuthors(normalizedIds)) {
            Long authorId = asLong(row.get("authorId"));
            Long postCount = asLong(row.get("postCount"));
            if (authorId != null && authorId > 0 && postCount != null) {
                result.put(authorId, Math.max(postCount, 0L));
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
    public List<PublicPostUpdateDTO> listPublicUpdates(Long postId, int limit) {
        if (postId == null || postId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Post post = postRepo.findById(postId).orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        if (!isPubliclyVisible(post)) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        return versionHistoryService.listPublicUpdates(postId, limit);
    }


    @Override
    public PageResult<PostBriefDTO> getLatest(long cursor, int size) {
        return listPosts(null, null, null, cursor, size);
    }

    @Override
    public PageResult<PostBriefDTO> getHot(String cursor, int size) {
        return getHot(cursor, size, null);
    }

    @Override
    public PageResult<PostBriefDTO> getHot(String cursor, int size, Integer domain) {
        int limit = pageSize(size);
        Integer activeDomain = requireOptionalDomain(domain);
        HotCursor hotCursor = HotCursor.parse(cursor);
        return pagedHotPo(postMapper.selectHotPosts(hotCursor.score(), hotCursor.time(), hotCursor.id(), activeDomain, scanSize(limit)), limit);
    }

    @Override
    public PageResult<PostBriefDTO> listPosts(Long authorId, Long tagId, Integer postType, Boolean featured, Integer domain, long cursor, int size) {
        return listPosts(authorId, tagId, postType, featured, domain, cursor, size, false);
    }

    @Override
    public PageResult<PostBriefDTO> listPosts(Long authorId, Long tagId, Integer postType, Boolean featured, Integer domain,
                                             long cursor, int size, boolean includeTestData) {
        Integer activeDomain = requireOptionalDomain(domain);
        int limit = pageSize(size);
        List<Post> list = scanPublicPosts(authorId, tagId, postType, featured, activeDomain, cursor, limit);

        return paged(list, limit, includeTestData);
    }

    @Override
    public List<TagDTO> listTags() {
        return activeTags().stream()
                .map(this::toTagDto)
                .filter(tag -> !PublicContentFilter.isSyntheticText(tag.getName()))
                .toList();
    }

    @Override
    public PageResult<PostBriefDTO> getPostsByTag(Long tagId, Integer postType, Boolean featured, long cursor, int size) {
        return listPosts(null, tagId, postType, featured, null, cursor, size);
    }

    private PageResult<PostBriefDTO> paged(List<Post> list, int size) {
        return paged(list, size, false);
    }

    private PageResult<PostBriefDTO> paged(List<Post> list, int size, boolean includeTestData) {
        if (list.isEmpty()) return PageResult.empty();
        Map<Long, Post> postById = list.stream().collect(Collectors.toMap(Post::getId, post -> post, (left, right) -> left));
        Map<Long, List<TagDTO>> tags = tagsByPostIds(list.stream().map(Post::getId).toList());
        List<PostBriefDTO> visible = list.stream().map(p -> toBrief(p, tags.getOrDefault(p.getId(), List.of()))).toList();
        enrichBriefs(visible, null);
        int syntheticFiltered = 0;
        if (!includeTestData) {
            int beforeFilter = visible.size();
            visible = visible.stream()
                    .filter(post -> !PublicContentFilter.isSyntheticPost(post))
                    .toList();
            syntheticFiltered = beforeFilter - visible.size();
        }
        boolean visibleHasMore = visible.size() > size;
        boolean rawHasMore = list.size() > size;
        List<PostBriefDTO> items = visibleHasMore ? visible.subList(0, size) : visible;
        Post cursorPost = null;
        if (visibleHasMore && !items.isEmpty()) {
            cursorPost = postById.get(items.get(items.size() - 1).getId());
        } else if (rawHasMore) {
            cursorPost = list.get(list.size() - 1);
        }
        // 普通列表使用 createTime 毫秒时间戳作为游标，前端需原样传回。
        boolean hasMore = visibleHasMore || rawHasMore;
        String next = hasMore && cursorPost != null
                ? listCursor(cursorPost.getCreateTime(), cursorPost.getId())
                : null;
        return PageResult.of(items, next, hasMore)
                .withDiagnostic("includeTestData", includeTestData)
                .withDiagnostic("syntheticFiltered", syntheticFiltered)
                .withDiagnostic("testDataFilterActive", !includeTestData);
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
                        .domain(domainOf(extJson.get(p.getId())))
                        .anonymous(isAnonymousPost(extJson.get(p.getId())))
                        .tags(tags.getOrDefault(p.getId(), List.of()))
                        .createTime(p.getCreateTime())
                        .build())
                .toList();
        enrichBriefs(items, null);
        items = items.stream()
                .filter(post -> !PublicContentFilter.isSyntheticPost(post))
                .toList();
        String next = hasMore && !pageList.isEmpty()
                ? String.valueOf(pageList.get(pageList.size() - 1).getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli())
                : null;
        return PageResult.of(items, next, hasMore);
    }

    private PageResult<PostBriefDTO> pagedHotPo(List<PostPO> list, int size) {
        if (list.isEmpty()) return PageResult.empty();
        Map<Long, PostPO> postById = list.stream().collect(Collectors.toMap(PostPO::getId, post -> post, (left, right) -> left));
        List<Long> postIds = list.stream().map(PostPO::getId).toList();
        Map<Long, List<TagDTO>> tags = tagsByPostIds(postIds);
        Map<Long, String> extJson = extensionMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(PostExtensionPO::getPostId, PostExtensionPO::getExtJson, (a, b) -> a));
        List<PostBriefDTO> visible = list.stream()
                .map(p -> PostBriefDTO.builder()
                        .id(p.getId())
                        .authorId(p.getAuthorId())
                        .postType(p.getPostType())
                        .title(p.getTitle())
                        .summary(summary(p.getContent()))
                        .coverUrl(p.getCoverUrl())
                        .extJson(extJson.get(p.getId()))
                        .domain(domainOf(extJson.get(p.getId())))
                        .anonymous(isAnonymousPost(extJson.get(p.getId())))
                        .tags(tags.getOrDefault(p.getId(), List.of()))
                        .createTime(p.getCreateTime())
                        .build())
                .toList();
        enrichBriefs(visible, null);
        visible = visible.stream()
                .filter(post -> !PublicContentFilter.isSyntheticPost(post))
                .toList();
        boolean visibleHasMore = visible.size() > size;
        boolean rawHasMore = list.size() > size;
        List<PostBriefDTO> items = visibleHasMore ? visible.subList(0, size) : visible;
        PostPO cursorPost = null;
        if (visibleHasMore && !items.isEmpty()) {
            cursorPost = postById.get(items.get(items.size() - 1).getId());
        } else if (rawHasMore) {
            cursorPost = list.get(list.size() - 1);
        }
        boolean hasMore = visibleHasMore || rawHasMore;
        String next = hasMore && cursorPost != null
                ? HotCursor.of(cursorPost,
                batchGetCounters(List.of(cursorPost.getId())).get(cursorPost.getId()))
                : null;
        return PageResult.of(items, next, hasMore);
    }

    private int pageSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }

    private int scanSize(int pageSize) {
        return Math.min(pageSize * 3 + 1, 200);
    }

    private static List<Long> normalizeBatchIds(Collection<Long> ids, int maxSize) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .limit(maxSize)
                .toList();
    }

    private List<Post> scanPublicPosts(Long authorId, Long tagId, Integer postType, Boolean featured,
                                       Integer domain, long cursor, int pageSize) {
        int scanLimit = scanSize(pageSize);
        int maxRows = Math.min(MAX_SYNTHETIC_SCAN_ROWS,
                Math.max(scanLimit, scanLimit * SYNTHETIC_SCAN_MULTIPLIER));
        List<Post> scanned = new ArrayList<>();
        long scanCursor = cursor;
        while (scanned.size() < maxRows) {
            int remaining = Math.min(scanLimit, maxRows - scanned.size());
            List<Post> batch = postRepo.findPosts(authorId, tagId, postType, featured, domain, scanCursor, remaining);
            if (batch.isEmpty()) {
                break;
            }
            scanned.addAll(batch);
            if (batch.size() < remaining || hasVisiblePageAfterSyntheticFiltering(scanned, pageSize)) {
                break;
            }
            Long nextCursor = nextScanCursor(batch.get(batch.size() - 1));
            if (nextCursor == null || nextCursor == scanCursor) {
                break;
            }
            scanCursor = nextCursor;
        }
        return scanned;
    }

    private boolean hasVisiblePageAfterSyntheticFiltering(List<Post> posts, int pageSize) {
        if (posts == null || posts.isEmpty()) {
            return false;
        }
        Map<Long, List<TagDTO>> tags = tagsByPostIds(posts.stream().map(Post::getId).toList());
        Map<Long, UserBriefDTO> authors = userFacade.batchGetUserBriefs(
                posts.stream()
                        .map(Post::getAuthorId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()));
        int visible = 0;
        for (Post post : posts) {
            PostBriefDTO brief = toBrief(post, tags.getOrDefault(post.getId(), List.of()));
            brief.setAuthor(sanitizeAuthor(null, post.getAuthorId(), authors.get(post.getAuthorId())));
            if (!PublicContentFilter.isSyntheticPost(brief) && ++visible >= pageSize) {
                return true;
            }
        }
        return false;
    }

    private static Long nextScanCursor(Post post) {
        if (post == null) {
            return null;
        }
        String cursor = listCursor(post.getCreateTime(), post.getId());
        if (cursor == null) {
            return null;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException ignored) {
            return null;
        }
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
                .domain(effectiveDomain(p.getDomain()))
                .anonymous(isAnonymousPost(p))
                .tags(tags)
                .createTime(p.getCreateTime())
                .build();
    }

    private void enrichBriefs(Collection<PostBriefDTO> posts) {
        enrichBriefs(posts, null);
    }

    private void enrichBriefs(Collection<PostBriefDTO> posts, Long viewerUid) {
        if (posts == null || posts.isEmpty()) {
            return;
        }
        List<Long> postIds = posts.stream().map(PostBriefDTO::getId).toList();
        Map<Long, PostCounterDTO> counters = batchGetCounters(postIds);
        Map<Long, UserBriefDTO> authors = userFacade.batchGetUserBriefs(
                posts.stream().map(PostBriefDTO::getAuthorId).collect(Collectors.toSet()));
        Map<Long, PostTrustSignalsDTO> trustSignals = trustSignalsByPostIds(postIds);
        posts.forEach(p -> {
            p.setCounter(counters.getOrDefault(p.getId(), emptyCounter(p.getId())));
            p.setTrustSignals(trustSignals.getOrDefault(p.getId(), emptyTrustSignals()));
            boolean revealAuthor = !isAnonymousPost(p) || canViewRealAuthor(viewerUid, p.getAuthorId());
            p.setAuthor(revealAuthor ? sanitizeAuthor(viewerUid, p.getAuthorId(), authors.get(p.getAuthorId())) : anonymousAuthor());
            if (!revealAuthor) {
                p.setAuthorId(ANONYMOUS_AUTHOR_ID);
            }
        });
    }

    private PostDTO enrichFull(PostDTO dto, Long viewerUid) {
        if (dto == null) {
            return null;
        }
        boolean revealAuthor = !isAnonymousPost(dto) || canViewRealAuthor(viewerUid, dto.getAuthorId());
        return PostDTO.builder()
                .id(dto.getId())
                .authorId(revealAuthor ? dto.getAuthorId() : ANONYMOUS_AUTHOR_ID)
                .author(revealAuthor ? sanitizeAuthor(viewerUid, dto.getAuthorId(), userFacade.getUserBrief(dto.getAuthorId())) : anonymousAuthor())
                .postType(dto.getPostType())
                .title(dto.getTitle())
                .content(dto.getContent())
                .coverUrl(dto.getCoverUrl())
                .visibility(dto.getVisibility())
                .postStatus(dto.getPostStatus())
                .extJson(dto.getExtJson())
                .domain(effectiveDomain(dto.getDomain()))
                .anonymous(isAnonymousPost(dto))
                .tags(dto.getTags())
                .counter(batchGetCounters(List.of(dto.getId())).getOrDefault(dto.getId(), emptyCounter(dto.getId())))
                .trustSignals(trustSignalsByPostIds(List.of(dto.getId())).getOrDefault(dto.getId(), emptyTrustSignals()))
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

    private boolean isPubliclyVisible(Post post) {
        return post != null
                && Objects.equals(post.getPostStatus(), Post.STATUS_PUBLISHED)
                && (post.getVisibility() == null || Objects.equals(post.getVisibility(), Post.VIS_PUBLIC));
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
                .domain(effectiveDomain(p.getDomain()))
                .anonymous(isAnonymousPost(p))
                .tags(tags)
                .createTime(p.getCreateTime())
                .updateTime(p.getUpdateTime())
                .build();
    }

    private boolean isAnonymousPost(Post post) {
        return post != null && Objects.equals(effectiveDomain(post.getDomain()), Post.DOMAIN_CAREER) && isAnonymousPost(post.getExtJson());
    }

    private boolean isAnonymousPost(PostBriefDTO post) {
        return post != null && Objects.equals(effectiveDomain(post.getDomain()), Post.DOMAIN_CAREER) && Boolean.TRUE.equals(post.getAnonymous());
    }

    private boolean isAnonymousPost(PostDTO post) {
        return post != null && Objects.equals(effectiveDomain(post.getDomain()), Post.DOMAIN_CAREER) && Boolean.TRUE.equals(post.getAnonymous());
    }

    private boolean isAnonymousPost(String extJson) {
        try {
            JsonNode root = EXT_JSON_MAPPER.readTree(extJson);
            return root != null && root.path("anonymous").asBoolean(false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private Integer domainOf(String extJson) {
        try {
            JsonNode root = EXT_JSON_MAPPER.readTree(extJson);
            Integer domain = root != null && root.has("domain") && root.get("domain").canConvertToInt()
                    ? root.get("domain").asInt()
                    : null;
            return effectiveDomain(domain);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer requireOptionalDomain(Integer domain) {
        if (domain == null) {
            return null;
        }
        if (PostDomain.isValid(domain)) {
            return domain;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private Integer effectiveDomain(Integer domain) {
        return PostDomain.isValid(domain) ? domain : null;
    }

    private boolean canViewRealAuthor(Long viewerUid, Long authorId) {
        if (viewerUid == null) {
            return false;
        }
        if (Objects.equals(viewerUid, authorId)) {
            return true;
        }
        return adminPermissionService.isAdmin(viewerUid)
                || adminPermissionService.hasRole(viewerUid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
    }

    private UserBriefDTO anonymousAuthor() {
        return UserBriefDTO.builder()
                .uid(ANONYMOUS_AUTHOR_ID)
                .nickname("匿名用户")
                .avatarUrl("")
                .bio("")
                .followerCount(0L)
                .followingCount(0L)
                .postCount(0L)
                .profileVisible(false)
                .intentVisible(false)
                .privacyReason("匿名发布")
                .build();
    }

    private Map<Long, PostTrustSignalsDTO> trustSignalsByPostIds(Collection<Long> postIds) {
        List<Long> ids = normalizeBatchIds(postIds, MAX_BATCH_LOOKUP_IDS);
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            return trustSignalsMapper.selectByPostIds(ids).stream()
                    .filter(row -> row.getPostId() != null)
                    .collect(Collectors.toMap(PostTrustSignalsRow::getPostId, this::toTrustSignals,
                            (left, right) -> left));
        } catch (RuntimeException ignored) {
            // Stage 6 migration may be absent during a rolling upgrade. Public post reads must stay available.
            return Map.of();
        }
    }

    private PostTrustSignalsDTO toTrustSignals(PostTrustSignalsRow row) {
        boolean profileAvailable = Objects.equals(row.getProfileAvailable(), 1);
        boolean acceptedAnswer = row.getAcceptedCommentId() != null && row.getAcceptedCommentId() > 0;
        String freshness = row.getFreshnessStatus();
        boolean resolved = acceptedAnswer
                || "ACCEPTED".equals(freshness)
                || "UPDATED".equals(freshness)
                || "CURRENT".equals(freshness);
        return PostTrustSignalsDTO.builder()
                .profileAvailable(profileAvailable)
                .completenessScore(Math.max(0, Math.min(row.getCompletenessScore() == null ? 0 : row.getCompletenessScore(), 100)))
                .lastConfirmedAt(row.getLastConfirmedAt())
                .freshnessStatus(freshness)
                .hasAcceptedAnswer(acceptedAnswer)
                .acceptedSuggestionCount(Math.max(0, row.getAcceptedSuggestionCount() == null ? 0 : row.getAcceptedSuggestionCount()))
                .publicCorrectionCount(Math.max(0, row.getPublicCorrectionCount() == null ? 0 : row.getPublicCorrectionCount()))
                .sourceComplete(Objects.equals(row.getSourceComplete(), 1))
                .resolved(resolved)
                .build();
    }

    private PostTrustSignalsDTO emptyTrustSignals() {
        return PostTrustSignalsDTO.builder()
                .profileAvailable(false)
                .completenessScore(0)
                .hasAcceptedAnswer(false)
                .acceptedSuggestionCount(0)
                .publicCorrectionCount(0)
                .sourceComplete(false)
                .resolved(false)
                .build();
    }

    private UserBriefDTO sanitizeAuthor(Long viewerUid, Long targetUid, UserBriefDTO dto) {
        UserBriefDTO copy = copyUserBrief(dto);
        if (copy == null) {
            return null;
        }
        Long effectiveTargetUid = targetUid != null ? targetUid : copy.getUid();
        if (effectiveTargetUid == null) {
            copy.setProfileVisible(false);
            copy.setIntentVisible(false);
            copy.setIsFollowing(false);
            return copy;
        }
        boolean profileVisible = userFacade.isProfileVisible(viewerUid, effectiveTargetUid);
        copy.setProfileVisible(profileVisible);
        copy.setIntentVisible(userFacade.isIntentVisible(viewerUid, effectiveTargetUid));
        if (viewerUid != null && effectiveTargetUid != null && !viewerUid.equals(effectiveTargetUid)) {
            copy.setIsFollowing(userFacade.isFollowing(viewerUid, effectiveTargetUid));
        }
        if (!profileVisible) {
            copy.setNickname("");
            copy.setAvatarUrl("");
            copy.setBio("");
            copy.setFollowerCount(0L);
            copy.setFollowingCount(0L);
            copy.setPostCount(0L);
            copy.setPrivacyReason("PROFILE_RESTRICTED");
            copy.setAcceptContactRequest(false);
            copy.setContactRequestPolicy("off");
            copy.setCanStartContactRequest(false);
            copy.setContactRequestReasonCode("PROFILE_RESTRICTED");
            copy.setContactRequestReasonMessage("PROFILE_RESTRICTED");
        } else {
            applyContactRequestPolicy(copy, viewerUid, effectiveTargetUid);
        }
        return copy;
    }

    private void applyContactRequestPolicy(UserBriefDTO dto, Long viewerUid, Long targetUid) {
        if (dto == null || targetUid == null) {
            return;
        }
        if (viewerUid == null) {
            dto.setCanStartContactRequest(false);
            dto.setContactRequestReasonCode("LOGIN_REQUIRED");
            dto.setContactRequestReasonMessage("LOGIN_REQUIRED");
            return;
        }
        if (viewerUid.equals(targetUid)) {
            dto.setCanStartContactRequest(false);
            dto.setContactRequestReasonCode("SELF_CONTACT");
            dto.setContactRequestReasonMessage("SELF_CONTACT");
            return;
        }
        ContactRequestPolicyCheckDTO policy = userFacade.checkContactRequestPolicy(viewerUid, targetUid);
        dto.setCanStartContactRequest(Boolean.TRUE.equals(policy.getAllowed()));
        dto.setContactRequestReasonCode(policy.getReasonCode());
        dto.setContactRequestReasonMessage(policy.getReasonMessage());
        dto.setContactRequestPolicy(policy.getContactRequestPolicy());
        dto.setAcceptContactRequest(Boolean.TRUE.equals(policy.getAllowed())
                || !"CONTACT_REQUEST_CLOSED".equals(policy.getReasonCode()));
    }

    private static UserBriefDTO copyUserBrief(UserBriefDTO dto) {
        if (dto == null) {
            return null;
        }
        return UserBriefDTO.builder()
                .uid(dto.getUid())
                .nickname(dto.getNickname())
                .avatarUrl(dto.getAvatarUrl())
                .bio(dto.getBio())
                .followerCount(dto.getFollowerCount())
                .followingCount(dto.getFollowingCount())
                .postCount(dto.getPostCount())
                .isFollowing(dto.getIsFollowing())
                .profileVisible(dto.getProfileVisible())
                .intentVisible(dto.getIntentVisible())
                .privacyReason(dto.getPrivacyReason())
                .acceptContactRequest(dto.getAcceptContactRequest())
                .contactRequestPolicy(dto.getContactRequestPolicy())
                .canStartContactRequest(dto.getCanStartContactRequest())
                .contactRequestReasonCode(dto.getContactRequestReasonCode())
                .contactRequestReasonMessage(dto.getContactRequestReasonMessage())
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
        return selectTagsByPostIds(postIds).stream()
                .collect(Collectors.groupingBy(PostTagView::getPostId,
                        Collectors.mapping(this::toTagDto, Collectors.toList())));
    }

    private List<TagPO> activeTags() {
        return migrationCheckService.tagGovernanceReady()
                ? tagMapper.selectActiveTags()
                : tagMapper.selectActiveTagsCompat();
    }

    private List<PostTagView> selectTagsByPostIds(Collection<Long> postIds) {
        return migrationCheckService.tagGovernanceReady()
                ? tagMapper.selectTagsByPostIds(postIds)
                : tagMapper.selectTagsByPostIdsCompat(postIds);
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
                .recommended(tag.getRecommended() != null && tag.getRecommended() == 1)
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
                .recommended(tag.getRecommended() != null && tag.getRecommended() == 1)
                .build();
    }

    private static List<String> parseSynonyms(String synonyms) {
        if (synonyms == null || synonyms.isBlank()) {
            return List.of();
        }
        return Arrays.stream(synonyms.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
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

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String summary(String content) {
        if (content == null) return "";
        String s = content.replaceAll("[#*`>\\[\\]()_!~\\-]+", " ").trim();
        return s.length() <= SUMMARY_LEN ? s : s.substring(0, SUMMARY_LEN) + "...";
    }

    private static String listCursor(LocalDateTime time, Long id) {
        if (time == null) {
            return null;
        }
        long millis = time.toInstant(ZoneOffset.UTC).toEpochMilli();
        long suffix = id == null ? 0L : Math.floorMod(id, 1_000_000L);
        return String.valueOf(millis * 1_000_000L + suffix);
    }

    private record HotCursor(Double score, LocalDateTime time, Long id) {
        static HotCursor parse(String cursor) {
            if (cursor == null || cursor.isBlank() || !cursor.contains(":")) {
                return new HotCursor(null, null, null);
            }
            try {
                String[] parts = cursor.split(":");
                double score = Double.parseDouble(parts[0]) / 10_000D;
                LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(parts[1])), ZoneOffset.UTC);
                Long id = Long.parseLong(parts[2]);
                return new HotCursor(score, time, id);
            } catch (RuntimeException ignored) {
                return new HotCursor(null, null, null);
            }
        }

        static String of(PostPO post, PostCounterDTO counter) {
            double score = 0D;
            if (counter != null) {
                score += safe(counter.getLikeCount()) * 3D;
                score += safe(counter.getFavoriteCount()) * 4D;
                score += safe(counter.getCommentCount()) * 5D;
                score += safe(counter.getViewCount()) * 0.2D;
            }
            if (post.getCreateTime() != null) {
                score += Math.max(0D, 72D - java.time.Duration.between(post.getCreateTime(), LocalDateTime.now()).toHours());
            }
            long time = post.getCreateTime() == null ? 0L : post.getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli();
            return Math.round(score * 10_000D) + ":" + time + ":" + post.getId();
        }
    }

    private static long safe(Long value) {
        return value == null ? 0L : value;
    }
}
