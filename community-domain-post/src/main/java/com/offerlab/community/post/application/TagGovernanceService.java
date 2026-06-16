package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.utils.RiskConfirmation;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.post.api.dto.TagGovernanceCmd;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import com.offerlab.community.post.infrastructure.persistence.po.TagPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagGovernanceService {

    private static final int MAX_LIMIT = 100;
    private static final int MAX_SYNONYMS = 12;

    private final TagMapper tagMapper;
    private final AdminAuditService auditService;
    private final MigrationCheckService migrationCheckService;

    public List<TagDTO> list(Integer status, Boolean recommended, String keyword, int limit) {
        requireSchemaReady();
        Integer normalizedStatus = status == null ? null : normalizeStatus(status);
        Integer recommendedFlag = recommended == null ? null : (recommended ? 1 : 0);
        return tagMapper.selectGovernanceTags(normalizedStatus, recommendedFlag, clean(keyword), safeLimit(limit)).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public TagDTO update(Long tagId, TagGovernanceCmd cmd, Long operatorUid) {
        requireSchemaReady();
        requireOperator(operatorUid);
        String note = RiskConfirmation.requireHigh(cmd.getNote());
        TagPO tag = requireTag(tagId);
        Map<String, Object> before = toAudit(tag);
        String name = StringUtils.hasText(cmd.getName()) ? requireName(cmd.getName()) : tag.getTagName();
        int tagType = normalizeType(cmd.getTagType(), tag.getTagType());
        int status = normalizeStatus(cmd.getStatus(), valueOrDefault(tag.getTagStatus(), 1));
        int recommended = cmd.getRecommended() == null
                ? valueOrDefault(tag.getRecommended(), 0)
                : (cmd.getRecommended() ? 1 : 0);
        String synonyms = encodeSynonyms(cmd.getSynonyms());
        tagMapper.updateGovernance(tagId, name, tagType, status, recommended, synonyms);
        TagPO updated = requireTag(tagId);
        auditService.recordRequired(operatorUid, "TAG_GOVERNANCE_UPDATE", "TAG", tagId,
                before, toAudit(updated), note);
        return toDto(updated);
    }

    @Transactional
    public TagDTO updateStatus(Long tagId, Integer status, Long operatorUid, String note) {
        requireSchemaReady();
        requireOperator(operatorUid);
        String auditNote = RiskConfirmation.requireHigh(note);
        TagPO tag = requireTag(tagId);
        int normalized = normalizeStatus(status, valueOrDefault(tag.getTagStatus(), 1));
        tagMapper.updateStatus(tagId, normalized);
        TagPO updated = requireTag(tagId);
        auditService.recordRequired(operatorUid,
                normalized == 1 ? "TAG_ENABLE" : "TAG_DISABLE",
                "TAG", tagId,
                toAudit(tag), toAudit(updated), auditNote);
        return toDto(updated);
    }

    @Transactional
    public TagDTO updateRecommended(Long tagId, Boolean recommended, Long operatorUid, String note) {
        requireSchemaReady();
        requireOperator(operatorUid);
        String auditNote = RiskConfirmation.requireHigh(note);
        TagPO tag = requireTag(tagId);
        int flag = Boolean.TRUE.equals(recommended) ? 1 : 0;
        tagMapper.updateRecommended(tagId, flag);
        TagPO updated = requireTag(tagId);
        auditService.recordRequired(operatorUid,
                flag == 1 ? "TAG_RECOMMEND" : "TAG_UNRECOMMEND",
                "TAG", tagId,
                toAudit(tag), toAudit(updated), auditNote);
        return toDto(updated);
    }

    @Transactional
    public TagDTO updateSynonyms(Long tagId, List<String> synonyms, Long operatorUid, String note) {
        requireSchemaReady();
        requireOperator(operatorUid);
        String auditNote = RiskConfirmation.requireHigh(note);
        TagPO tag = requireTag(tagId);
        String encoded = encodeSynonyms(synonyms);
        tagMapper.updateSynonyms(tagId, encoded);
        TagPO updated = requireTag(tagId);
        auditService.recordRequired(operatorUid, "TAG_SYNONYMS_UPDATE", "TAG", tagId,
                toAudit(tag), toAudit(updated), auditNote);
        return toDto(updated);
    }

    @Transactional
    public TagDTO merge(Long sourceTagId, Long targetTagId, Long operatorUid, String note, String confirmationPhrase) {
        requireSchemaReady();
        requireOperator(operatorUid);
        String auditNote = RiskConfirmation.requireCritical(note, confirmationPhrase);
        if (sourceTagId == null || targetTagId == null || sourceTagId <= 0 || targetTagId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (Objects.equals(sourceTagId, targetTagId)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "不能合并到同一个标签");
        }
        TagPO source = requireTag(sourceTagId);
        TagPO target = requireTag(targetTagId);
        if (valueOrDefault(target.getTagStatus(), 1) != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "目标标签必须处于启用状态");
        }

        int removedPostDuplicates = tagMapper.deleteDuplicatePostTagRefsForMerge(sourceTagId, targetTagId);
        int movedPostRefs = tagMapper.updatePostTagRefsToTarget(sourceTagId, targetTagId);
        boolean topicSchemaReady = migrationCheckService.communityTopicReady();
        int removedTopicDuplicates = 0;
        int movedTopicRefs = 0;
        if (topicSchemaReady) {
            removedTopicDuplicates = tagMapper.deleteDuplicateTopicTagRefsForMerge(sourceTagId, targetTagId);
            movedTopicRefs = tagMapper.updateTopicTagRefsToTarget(sourceTagId, targetTagId);
        }
        tagMapper.markMerged(sourceTagId, targetTagId);

        TagPO merged = requireTag(sourceTagId);
        Map<String, Object> after = new java.util.LinkedHashMap<>();
        after.put("source", toAudit(merged));
        after.put("target", toAudit(target));
        after.put("removedPostDuplicates", removedPostDuplicates);
        after.put("movedPostRefs", movedPostRefs);
        after.put("removedTopicDuplicates", removedTopicDuplicates);
        after.put("movedTopicRefs", movedTopicRefs);
        after.put("topicMigrationReady", topicSchemaReady);
        after.put("topicMergeSkipped", !topicSchemaReady);
        auditService.recordRequired(operatorUid, "TAG_MERGE", "TAG", sourceTagId,
                toAudit(source),
                after,
                auditNote);
        return toDto(merged);
    }

    private TagPO requireTag(Long tagId) {
        if (tagId == null || tagId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        TagPO tag = tagMapper.selectById(tagId);
        if (tag == null || Objects.equals(tag.getIsDeleted(), 1)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return tag;
    }

    private void requireOperator(Long operatorUid) {
        if (operatorUid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private void requireSchemaReady() {
        if (!migrationCheckService.tagGovernanceReady()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "标签治理数据库迁移未完成，请先执行 db/migration/20260608_tag_governance.sql");
        }
    }

    private TagDTO toDto(TagPO tag) {
        return TagDTO.builder()
                .id(tag.getId())
                .name(tag.getTagName())
                .slug(String.valueOf(tag.getId()))
                .category(toCategory(tag.getTagType()))
                .tagType(tag.getTagType())
                .useCount(tag.getUseCount())
                .official(tag.getIsOfficial() != null && tag.getIsOfficial() == 1)
                .status(valueOrDefault(tag.getTagStatus(), 1))
                .recommended(tag.getRecommended() != null && tag.getRecommended() == 1)
                .mergeTargetId(tag.getMergeTargetId())
                .synonyms(decodeSynonyms(tag.getSynonyms()))
                .build();
    }

    private Map<String, Object> toAudit(TagPO tag) {
        return Map.of(
                "id", tag.getId(),
                "name", valueOrEmpty(tag.getTagName()),
                "tagType", valueOrDefault(tag.getTagType(), 4),
                "status", valueOrDefault(tag.getTagStatus(), 1),
                "recommended", valueOrDefault(tag.getRecommended(), 0),
                "mergeTargetId", tag.getMergeTargetId() == null ? "" : tag.getMergeTargetId(),
                "synonyms", valueOrEmpty(tag.getSynonyms())
        );
    }

    private String requireName(String value) {
        String name = clean(value);
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "标签名称不能为空");
        }
        if (name.length() > 64) {
            return name.substring(0, 64);
        }
        return name;
    }

    private int normalizeType(Integer value, Integer fallback) {
        int type = value == null ? valueOrDefault(fallback, 4) : value;
        return switch (type) {
            case 1, 2, 3, 4 -> type;
            default -> 4;
        };
    }

    private int normalizeStatus(Integer value) {
        return normalizeStatus(value, 1);
    }

    private int normalizeStatus(Integer value, Integer fallback) {
        int status = value == null ? valueOrDefault(fallback, 1) : value;
        return status == 1 ? 1 : 0;
    }

    private String encodeSynonyms(List<String> values) {
        List<String> normalized = normalizeSynonyms(values);
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }

    private List<String> decodeSynonyms(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return normalizeSynonyms(Arrays.asList(value.split(",")));
    }

    private List<String> normalizeSynonyms(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> dedup = new LinkedHashSet<>();
        for (String value : values) {
            String item = clean(value);
            if (!StringUtils.hasText(item)) {
                continue;
            }
            dedup.add(item.length() <= 64 ? item : item.substring(0, 64));
            if (dedup.size() >= MAX_SYNONYMS) {
                break;
            }
        }
        return List.copyOf(dedup);
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String cleanNote(String value) {
        String note = clean(value);
        if (!StringUtils.hasText(note)) {
            return null;
        }
        return note.length() <= 500 ? note : note.substring(0, 500);
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 50 : limit, MAX_LIMIT));
    }

    private int valueOrDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
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
