package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.post.api.dto.ContentSeriesAddPostCmd;
import com.offerlab.community.post.api.dto.ContentSeriesCreateCmd;
import com.offerlab.community.post.api.dto.ContentSeriesDTO;
import com.offerlab.community.post.api.dto.ContentSeriesProgressDTO;
import com.offerlab.community.post.api.dto.ContentSeriesUpdateCmd;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.domain.model.PostDomain;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentSeriesMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentSeriesPostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.po.ContentSeriesPO;
import com.offerlab.community.post.infrastructure.persistence.po.ContentSeriesPostPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ContentSeriesService {

    private static final String MIGRATION_HINT = "db/migration/20260624_content_series.sql";
    private static final int VISIBILITY_PUBLIC = 1;
    private static final int VISIBILITY_PRIVATE = 2;
    private static final int MAX_PAGE_SIZE = 30;

    private final ContentSeriesMapper contentSeriesMapper;
    private final ContentSeriesPostMapper contentSeriesPostMapper;
    private final PostMapper postMapper;
    private final PostFacade postFacade;
    private final SnowflakeIdGenerator idGenerator;
    private final MigrationCheckService migrationCheckService;

    public List<ContentSeriesDTO> listMine(Long creatorUid) {
        requireUser(creatorUid);
        if (!schemaReady()) {
            return List.of();
        }
        List<ContentSeriesPO> series = contentSeriesMapper.selectMine(creatorUid);
        if (series == null || series.isEmpty()) {
            return List.of();
        }
        Map<Long, ContentSeriesProgressDTO> progressBySeriesId = progressBySeriesIds(
                series.stream().map(ContentSeriesPO::getId).toList());
        return series.stream()
                .map(item -> toDto(item, progressBySeriesId.get(item.getId())))
                .toList();
    }

    @Transactional
    public ContentSeriesDTO create(ContentSeriesCreateCmd cmd, Long creatorUid) {
        requireUser(creatorUid);
        requireSchemaReady();
        ContentSeriesPO series = new ContentSeriesPO();
        series.setId(idGenerator.nextId());
        series.setCreatorUid(creatorUid);
        series.setTitle(requireTitle(cmd == null ? null : cmd.getTitle()));
        series.setDescription(clean(cmd == null ? null : cmd.getDescription(), 1000));
        series.setDomain(requireDomain(cmd == null ? null : cmd.getDomain()));
        series.setCoverUrl(clean(cmd == null ? null : cmd.getCoverUrl(), 512));
        series.setVisibility(normalizeVisibility(cmd == null ? null : cmd.getVisibility()));
        series.setCreateTime(LocalDateTime.now());
        series.setUpdateTime(LocalDateTime.now());
        contentSeriesMapper.insert(series);
        return getOwnedSeries(series.getId(), creatorUid);
    }

    @Transactional
    public ContentSeriesDTO update(Long seriesId, ContentSeriesUpdateCmd cmd, Long operatorUid) {
        requireUser(operatorUid);
        requireSchemaReady();
        ContentSeriesPO series = requireOwnedSeries(seriesId, operatorUid);
        series.setTitle(requireTitle(cmd == null ? null : cmd.getTitle()));
        series.setDescription(clean(cmd == null ? null : cmd.getDescription(), 1000));
        series.setDomain(requireDomain(cmd == null ? null : cmd.getDomain()));
        series.setCoverUrl(clean(cmd == null ? null : cmd.getCoverUrl(), 512));
        series.setVisibility(normalizeVisibility(cmd == null ? null : cmd.getVisibility()));
        series.setUpdateTime(LocalDateTime.now());
        contentSeriesMapper.updateById(series);
        return getOwnedSeries(seriesId, operatorUid);
    }

    public ContentSeriesDTO getPublicDetail(Long seriesId) {
        requireSchemaReady();
        ContentSeriesPO series = requirePublicSeries(seriesId);
        return toDto(series, publicProgressBySeriesIds(List.of(seriesId)).get(seriesId));
    }

    public List<ContentSeriesDTO> listPublicByUser(Long creatorUid, long cursor, int size) {
        if (creatorUid == null || creatorUid <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (!schemaReady()) {
            return List.of();
        }
        int pageSize = normalizePageSize(size);
        List<ContentSeriesPO> series = contentSeriesMapper.selectPublicByCreatorUid(creatorUid, Math.max(0, cursor), pageSize);
        if (series == null || series.isEmpty()) {
            return List.of();
        }
        Map<Long, ContentSeriesProgressDTO> progressBySeriesId = publicProgressBySeriesIds(
                series.stream().map(ContentSeriesPO::getId).toList());
        return series.stream()
                .map(item -> toDto(item, progressBySeriesId.get(item.getId())))
                .toList();
    }

    public PageResult<PostBriefDTO> listPublicPosts(Long seriesId, long cursor, int size) {
        requireSchemaReady();
        requirePublicSeries(seriesId);
        int pageSize = normalizePageSize(size);
        List<PostPO> posts = postMapper.selectPublicPostsByContentSeries(seriesId, Math.max(0, cursor), pageSize + 1);
        if (posts == null || posts.isEmpty()) {
            return PageResult.empty();
        }
        boolean hasMore = posts.size() > pageSize;
        List<PostPO> pagePosts = posts.stream()
                .limit(pageSize)
                .toList();
        Map<Long, PostBriefDTO> briefById = postFacade.batchGetPosts(
                pagePosts.stream().map(PostPO::getId).toList(), null, false);
        List<PostBriefDTO> items = pagePosts.stream()
                .map(post -> briefById.get(post.getId()))
                .filter(Objects::nonNull)
                .toList();
        String nextCursor = hasMore && !pagePosts.isEmpty() ? String.valueOf(pagePosts.get(pagePosts.size() - 1).getId()) : null;
        return PageResult.of(items, nextCursor, hasMore);
    }

    @Transactional
    public ContentSeriesDTO removePost(Long seriesId, Long postId, Long operatorUid) {
        requireUser(operatorUid);
        requireSchemaReady();
        requireOwnedSeries(seriesId, operatorUid);
        if (postId == null || postId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        contentSeriesPostMapper.softDeleteRelation(seriesId, postId);
        contentSeriesMapper.touchSeries(seriesId);
        return getOwnedSeries(seriesId, operatorUid);
    }

    @Transactional
    public ContentSeriesDTO addPost(Long seriesId, ContentSeriesAddPostCmd cmd, Long operatorUid) {
        requireUser(operatorUid);
        requireSchemaReady();
        requireOwnedSeries(seriesId, operatorUid);
        Long postId = requirePostId(cmd);
        PostPO post = postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        if (!Objects.equals(post.getAuthorId(), operatorUid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (contentSeriesPostMapper.existsActiveRelation(seriesId, postId) > 0) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(), "Post already belongs to this series");
        }
        int sortOrder = resolveSortOrder(seriesId, cmd.getSortOrder());
        if (contentSeriesPostMapper.restoreDeletedRelation(seriesId, postId, sortOrder) > 0) {
            contentSeriesMapper.touchSeries(seriesId);
            return getOwnedSeries(seriesId, operatorUid);
        }
        ContentSeriesPostPO relation = new ContentSeriesPostPO();
        relation.setId(idGenerator.nextId());
        relation.setSeriesId(seriesId);
        relation.setPostId(postId);
        relation.setSortOrder(sortOrder);
        relation.setCreateTime(LocalDateTime.now());
        relation.setUpdateTime(LocalDateTime.now());
        contentSeriesPostMapper.insert(relation);
        contentSeriesMapper.touchSeries(seriesId);
        return getOwnedSeries(seriesId, operatorUid);
    }

    private ContentSeriesDTO getOwnedSeries(Long seriesId, Long operatorUid) {
        ContentSeriesPO series = requireOwnedSeries(seriesId, operatorUid);
        return toDto(series, progressBySeriesIds(List.of(seriesId)).get(seriesId));
    }

    private ContentSeriesPO requireOwnedSeries(Long seriesId, Long operatorUid) {
        if (seriesId == null || seriesId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        ContentSeriesPO series = contentSeriesMapper.selectById(seriesId);
        if (series == null || Objects.equals(series.getIsDeleted(), 1)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!Objects.equals(series.getCreatorUid(), operatorUid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return series;
    }

    private ContentSeriesPO requirePublicSeries(Long seriesId) {
        if (seriesId == null || seriesId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        ContentSeriesPO series = contentSeriesMapper.selectPublicById(seriesId);
        if (series == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return series;
    }

    private Map<Long, ContentSeriesProgressDTO> progressBySeriesIds(Collection<Long> seriesIds) {
        return progressByRows(seriesIds, contentSeriesMapper.selectProgressBySeriesIds(seriesIds));
    }

    private Map<Long, ContentSeriesProgressDTO> publicProgressBySeriesIds(Collection<Long> seriesIds) {
        return progressByRows(seriesIds, contentSeriesMapper.selectPublicProgressBySeriesIds(seriesIds));
    }

    private Map<Long, ContentSeriesProgressDTO> progressByRows(Collection<Long> seriesIds, List<Map<String, Object>> rows) {
        if (seriesIds == null || seriesIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ContentSeriesProgressDTO> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long seriesId = asLong(row.get("seriesId"));
            long published = safeLong(row.get("publishedPostCount"));
            long total = safeLong(row.get("totalPostCount"));
            if (seriesId != null) {
                result.put(seriesId, ContentSeriesProgressDTO.builder()
                        .publishedPostCount(published)
                        .totalPostCount(total)
                        .completionRate(total <= 0 ? 0 : (int) Math.round(published * 100D / total))
                        .build());
            }
        }
        for (Long seriesId : seriesIds) {
            result.putIfAbsent(seriesId, emptyProgress());
        }
        return result;
    }

    private boolean schemaReady() {
        try {
            return migrationCheckService.contentSeriesReady();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void requireSchemaReady() {
        if (!schemaReady()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "Content series migration is required: " + MIGRATION_HINT);
        }
    }

    private static ContentSeriesDTO toDto(ContentSeriesPO series, ContentSeriesProgressDTO progress) {
        return ContentSeriesDTO.builder()
                .id(series.getId())
                .creatorUid(series.getCreatorUid())
                .title(series.getTitle())
                .description(series.getDescription())
                .domain(series.getDomain())
                .coverUrl(series.getCoverUrl())
                .visibility(series.getVisibility() == null ? VISIBILITY_PRIVATE : series.getVisibility())
                .progress(progress == null ? emptyProgress() : progress)
                .createTime(series.getCreateTime())
                .updateTime(series.getUpdateTime())
                .build();
    }

    private static ContentSeriesProgressDTO emptyProgress() {
        return ContentSeriesProgressDTO.builder()
                .publishedPostCount(0L)
                .totalPostCount(0L)
                .completionRate(0)
                .build();
    }

    private static void requireUser(Long uid) {
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static String requireTitle(String title) {
        String normalized = clean(title, 120);
        if (!StringUtils.hasText(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static Integer requireDomain(Integer domain) {
        if (PostDomain.isValid(domain)) {
            return domain;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private static Integer normalizeVisibility(Integer visibility) {
        if (visibility == null) {
            return VISIBILITY_PRIVATE;
        }
        if (visibility == VISIBILITY_PUBLIC || visibility == VISIBILITY_PRIVATE) {
            return visibility;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private static int normalizePageSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(MAX_PAGE_SIZE, size);
    }

    private static Long requirePostId(ContentSeriesAddPostCmd cmd) {
        if (cmd == null || cmd.getPostId() == null || cmd.getPostId() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return cmd.getPostId();
    }

    private Integer resolveSortOrder(Long seriesId, Integer requestedSortOrder) {
        if (requestedSortOrder != null) {
            return Math.max(requestedSortOrder, 0);
        }
        Integer maxSortOrder = contentSeriesPostMapper.selectMaxSortOrder(seriesId);
        return maxSortOrder == null ? 0 : maxSortOrder + 1;
    }

    private static String clean(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static long safeLong(Object value) {
        Long number = asLong(value);
        return number == null ? 0L : number;
    }

}
