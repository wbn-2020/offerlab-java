package com.offerlab.community.post.application;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.post.api.dto.PostDraftCmd;
import com.offerlab.community.post.api.dto.PostDraftDTO;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostDraftMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostDraftPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostDraftService {
    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final PostDraftMapper draftMapper;
    private final SnowflakeIdGenerator idGen;
    private final ObjectMapper objectMapper;

    public List<PostDraftDTO> listRecent(Long uid, int limit) {
        if (!tableReady()) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 10 : limit, 30));
        try {
            return draftMapper.selectRecentByUser(uid, safeLimit).stream().map(this::toDto).toList();
        } catch (RuntimeException e) {
            log.warn("post draft list unavailable, returning empty list: {}", e.getMessage());
            return List.of();
        }
    }

    public PostDraftDTO get(Long uid, Long id) {
        if (!tableReady()) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        PostDraftPO po = draftMapper.selectByUser(id, uid);
        if (po == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return toDto(po);
    }

    public PostDraftDTO latestBySourcePost(Long uid, Long sourcePostId) {
        if (sourcePostId == null || sourcePostId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (!tableReady()) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        PostDraftPO po = draftMapper.selectLatestBySourcePost(uid, sourcePostId);
        if (po == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return toDto(po);
    }

    @Transactional
    public PostDraftDTO save(PostDraftCmd cmd) {
        if (cmd == null || cmd.getUid() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (!tableReady()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        Long id = cmd.getId();
        PostDraftPO existing = null;
        if (id != null) {
            existing = draftMapper.selectByUser(id, cmd.getUid());
            if (existing == null) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
            }
        } else if (cmd.getSourcePostId() != null && cmd.getSourcePostId() > 0) {
            existing = draftMapper.selectLatestBySourcePost(cmd.getUid(), cmd.getSourcePostId());
            id = existing == null ? null : existing.getId();
        }
        if (id == null) {
            id = idGen.nextId();
        }
        PostDraftPO po = toPo(cmd, id);
        if (existing == null) {
            draftMapper.insert(po);
        } else {
            po.setCreateTime(null);
            draftMapper.updateById(po);
        }
        return get(cmd.getUid(), id);
    }

    @Transactional
    public boolean delete(Long uid, Long id) {
        if (!tableReady()) {
            return false;
        }
        int updated = draftMapper.delete(new LambdaUpdateWrapper<PostDraftPO>()
                .eq(PostDraftPO::getId, id)
                .eq(PostDraftPO::getUid, uid));
        return updated > 0;
    }

    @Transactional
    public void deleteIfOwned(Long uid, Long id) {
        if (id != null && id > 0) {
            delete(uid, id);
        }
    }

    private PostDraftPO toPo(PostDraftCmd cmd, Long id) {
        PostDraftPO po = new PostDraftPO();
        po.setId(id);
        po.setUid(cmd.getUid());
        po.setSourcePostId(cmd.getSourcePostId());
        po.setPostType(cmd.getPostType() == null ? 1 : cmd.getPostType());
        po.setTitle(limit(cmd.getTitle(), 255));
        po.setContent(limit(cmd.getContent(), 20000));
        po.setCoverUrl(limit(cmd.getCoverUrl(), 512));
        po.setVisibility(cmd.getVisibility() == null ? 1 : cmd.getVisibility());
        po.setExtJson(StringUtils.hasText(cmd.getExtJson()) ? limit(cmd.getExtJson(), 20000) : null);
        po.setTagIdsJson(writeJson(cmd.getTagIds()));
        po.setTagNamesJson(writeJson(normalizeTagNames(cmd.getTagNames())));
        return po;
    }

    private PostDraftDTO toDto(PostDraftPO po) {
        return PostDraftDTO.builder()
                .id(po.getId())
                .uid(po.getUid())
                .sourcePostId(po.getSourcePostId())
                .postType(po.getPostType())
                .title(po.getTitle())
                .content(po.getContent())
                .coverUrl(po.getCoverUrl())
                .visibility(po.getVisibility())
                .extJson(po.getExtJson())
                .tagIds(readJson(po.getTagIdsJson(), LONG_LIST_TYPE))
                .tagNames(readJson(po.getTagNamesJson(), STRING_LIST_TYPE))
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private List<String> normalizeTagNames(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(20)
                .toList();
    }

    private <T> List<T> readJson(String json, TypeReference<List<T>> type) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("post draft json parse failed", e);
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private boolean tableReady() {
        try {
            return draftMapper.tableExists() > 0;
        } catch (RuntimeException e) {
            log.warn("post draft table check failed: {}", e.getMessage());
            return false;
        }
    }
}
