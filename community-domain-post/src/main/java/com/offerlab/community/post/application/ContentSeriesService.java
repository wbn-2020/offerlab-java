package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.post.api.dto.ContentSeriesAddPostCmd;
import com.offerlab.community.post.api.dto.ContentSeriesCreateCmd;
import com.offerlab.community.post.api.dto.ContentSeriesDTO;
import com.offerlab.community.post.api.dto.ContentSeriesProgressDTO;
import com.offerlab.community.post.api.dto.ContentSeriesUpdateCmd;
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

    private final ContentSeriesMapper contentSeriesMapper;
    private final ContentSeriesPostMapper contentSeriesPostMapper;
    private final PostMapper postMapper;
    private final SnowflakeIdGenerator idGenerator;

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
        series.setUpdateTime(LocalDateTime.now());
        contentSeriesMapper.updateById(series);
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
        ContentSeriesPostPO relation = new ContentSeriesPostPO();
        relation.setId(idGenerator.nextId());
        relation.setSeriesId(seriesId);
        relation.setPostId(postId);
        relation.setSortOrder(resolveSortOrder(seriesId, cmd.getSortOrder()));
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

    private Map<Long, ContentSeriesProgressDTO> progressBySeriesIds(Collection<Long> seriesIds) {
        if (seriesIds == null || seriesIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ContentSeriesProgressDTO> result = new HashMap<>();
        for (Map<String, Object> row : contentSeriesMapper.selectProgressBySeriesIds(seriesIds)) {
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
            return contentSeriesMapper.tableExists() > 0 && contentSeriesPostMapper.tableExists() > 0;
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
