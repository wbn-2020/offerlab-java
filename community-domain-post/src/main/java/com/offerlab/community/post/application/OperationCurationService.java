package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.PublicContentFilter;
import com.offerlab.community.post.api.dto.OperationCandidateDTO;
import com.offerlab.community.post.api.dto.OperationCurationItemCmd;
import com.offerlab.community.post.api.dto.OperationCurationItemDTO;
import com.offerlab.community.post.api.dto.OperationSlotCmd;
import com.offerlab.community.post.api.dto.OperationSlotDTO;
import com.offerlab.community.post.api.dto.OperationSlotItemCmd;
import com.offerlab.community.post.api.dto.OperationSlotItemDTO;
import com.offerlab.community.post.api.dto.OperationTopicCmd;
import com.offerlab.community.post.api.dto.OperationTopicDTO;
import com.offerlab.community.post.api.dto.OperationTopicSectionCmd;
import com.offerlab.community.post.api.dto.OperationTopicSectionDTO;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.domain.model.PostDomain;
import com.offerlab.community.post.infrastructure.persistence.mapper.OperationCurationItemMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.OperationSlotItemMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.OperationSlotMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.OperationTopicMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.OperationTopicSectionMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.po.OperationCurationItemPO;
import com.offerlab.community.post.infrastructure.persistence.po.OperationSlotItemPO;
import com.offerlab.community.post.infrastructure.persistence.po.OperationSlotPO;
import com.offerlab.community.post.infrastructure.persistence.po.OperationTopicPO;
import com.offerlab.community.post.infrastructure.persistence.po.OperationTopicSectionPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OperationCurationService {
    public static final String SOURCE_POST = "POST";
    public static final String SOURCE_OPERATION_TOPIC = "OPERATION_TOPIC";
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PREVIEW = "PREVIEW";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_OFFLINE = "OFFLINE";
    public static final String ITEM_ACTIVE = "ACTIVE";
    public static final String ITEM_PAUSED = "PAUSED";
    public static final String TYPE_TOPIC = "TOPIC";
    public static final String TYPE_EVENT = "EVENT";

    private static final int MAX_OPERATION_SLOTS = 2;
    private static final int MIN_SLOT_LIMIT = 3;
    private static final int MAX_SLOT_LIMIT = 5;
    private static final int MAX_ADMIN_LIST = 100;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PostMapper postMapper;
    private final OperationCurationItemMapper curationItemMapper;
    private final OperationSlotMapper slotMapper;
    private final OperationSlotItemMapper slotItemMapper;
    private final OperationTopicMapper topicMapper;
    private final OperationTopicSectionMapper topicSectionMapper;
    private final PostFacade postFacade;
    private final AdminAuditService adminAuditService;
    private final SnowflakeIdGenerator idGen;
    private final ObjectMapper objectMapper;

    public List<OperationCandidateDTO> listCandidates(String keyword, Integer domain, Integer postType, int limit) {
        Integer activeDomain = requireOptionalDomain(domain);
        List<Long> ids = postMapper.selectOperationCandidates(clean(keyword), activeDomain, postType, adminLimit(limit)).stream()
                .map(PostPO::getId)
                .toList();
        Map<Long, PostBriefDTO> posts = postFacade.batchGetPosts(ids, null, false);
        return ids.stream()
                .map(posts::get)
                .filter(PublicContentFilter::isDistributablePost)
                .map(post -> OperationCandidateDTO.builder()
                        .sourceType(SOURCE_POST)
                        .sourceId(post.getId())
                        .reason("PUBLIC_VISIBLE_GOVERNED")
                        .operable(true)
                        .post(post)
                        .build())
                .toList();
    }

    public List<OperationCurationItemDTO> listCurationItems(String status, String sourceType, int limit) {
        List<OperationCurationItemPO> items = curationItemMapper.listItems(normalizeItemStatus(status, true),
                normalizeNullableSourceType(sourceType), adminLimit(limit));
        Map<Long, PostBriefDTO> posts = loadPosts(items.stream()
                .filter(item -> SOURCE_POST.equals(item.getSourceType()))
                .map(OperationCurationItemPO::getSourceId)
                .toList());
        return items.stream()
                .map(item -> toCurationDto(item, posts.get(item.getSourceId())))
                .filter(item -> !SOURCE_POST.equals(item.getSourceType()) || PublicContentFilter.isDistributablePost(item.getPost()))
                .toList();
    }

    @Transactional
    public OperationCurationItemDTO upsertCurationItem(OperationCurationItemCmd cmd, Long operatorUid) {
        String sourceType = normalizeSourceType(cmd == null ? null : cmd.getSourceType(), true);
        Long sourceId = requireId(cmd == null ? null : cmd.getSourceId());
        validateSourceOperable(sourceType, sourceId, false);
        OperationCurationItemPO existing = curationItemMapper.selectBySource(sourceType, sourceId);
        OperationCurationItemPO po = existing == null ? new OperationCurationItemPO() : existing;
        Object before = existing == null ? null : existing;
        if (existing == null) {
            po.setId(idGen.nextId());
            po.setSourceType(sourceType);
            po.setSourceId(sourceId);
            po.setCreatedBy(operatorUid);
        }
        po.setItemStatus(normalizeItemStatus(cmd.getStatus(), false));
        po.setSortOrder(cmd.getSortOrder() == null ? 100 : cmd.getSortOrder());
        po.setNote(limit(clean(cmd.getNote()), 500));
        po.setUpdatedBy(operatorUid);
        if (existing == null) {
            curationItemMapper.insert(po);
        } else {
            curationItemMapper.updateById(po);
        }
        OperationCurationItemDTO dto = listCurationItems(null, sourceType, MAX_ADMIN_LIST).stream()
                .filter(item -> Objects.equals(item.getSourceId(), sourceId))
                .findFirst()
                .orElse(toCurationDto(po, null));
        adminAuditService.recordRequired(operatorUid,
                existing == null ? "OPERATION_CURATION_ADD" : "OPERATION_CURATION_UPDATE",
                "OPERATION_CURATION_ITEM", po.getId(), before, dto, po.getNote());
        return dto;
    }

    @Transactional
    public void deleteCurationItem(Long itemId, Long operatorUid, String note) {
        OperationCurationItemPO existing = requireCurationItem(itemId);
        curationItemMapper.deleteById(itemId);
        adminAuditService.recordRequired(operatorUid, "OPERATION_CURATION_DELETE",
                "OPERATION_CURATION_ITEM", itemId, existing, Map.of("deleted", true), limit(clean(note), 500));
    }

    public List<OperationSlotDTO> listAdminSlots(String status, int limit) {
        return slotMapper.listSlots(normalizeWorkflowStatus(status, true), adminLimit(limit)).stream()
                .map(slot -> toSlotDto(slot, slotItemMapper.listBySlot(slot.getId(), null, MAX_ADMIN_LIST), false))
                .toList();
    }

    public OperationSlotDTO getPublicSlot(String slotCode, int limit) {
        OperationSlotPO slot = slotMapper.selectByCode(requireCode(slotCode, 64));
        if (slot == null || !STATUS_PUBLISHED.equals(slot.getSlotStatus()) || !inWindow(slot.getStartsAt(), slot.getEndsAt())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        int displayLimit = slotLimit(limit <= 0 ? slot.getDefaultLimit() : limit);
        List<OperationSlotItemPO> items = slotItemMapper.listBySlot(slot.getId(), ITEM_ACTIVE, MAX_ADMIN_LIST);
        OperationSlotDTO dto = toSlotDto(slot, items, true);
        dto.setItems(dto.getItems().stream().limit(displayLimit).toList());
        return dto;
    }

    @Transactional
    public OperationSlotDTO upsertSlot(OperationSlotCmd cmd, Long operatorUid) {
        String code = requireCode(cmd == null ? null : cmd.getSlotCode(), 64);
        OperationSlotPO existing = slotMapper.selectByCode(code);
        if (existing == null && slotMapper.countActiveRows() >= MAX_OPERATION_SLOTS) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "operation slots are limited to two in P0");
        }
        OperationSlotPO po = existing == null ? new OperationSlotPO() : existing;
        Object before = existing == null ? null : existing;
        if (existing == null) {
            po.setId(idGen.nextId());
            po.setSlotCode(code);
            po.setCreatedBy(operatorUid);
        }
        po.setSlotName(requireCleanText(cmd.getName(), 64, "slot name required"));
        po.setDescription(limit(clean(cmd.getDescription()), 500));
        po.setSlotStatus(normalizeWorkflowStatus(cmd.getStatus(), false));
        po.setSortOrder(cmd.getSortOrder() == null ? 100 : cmd.getSortOrder());
        po.setDefaultLimit(slotLimit(cmd.getDefaultLimit()));
        po.setStartsAt(cmd.getStartsAt());
        po.setEndsAt(cmd.getEndsAt());
        po.setUpdatedBy(operatorUid);
        if (!StringUtils.hasText(po.getPreviewToken())) {
            po.setPreviewToken(token());
        }
        validateDisplayText(po.getSlotName(), po.getDescription());
        if (existing == null) {
            slotMapper.insert(po);
        } else {
            slotMapper.updateById(po);
        }
        OperationSlotDTO dto = toSlotDto(po, slotItemMapper.listBySlot(po.getId(), null, MAX_ADMIN_LIST), false);
        adminAuditService.recordRequired(operatorUid, existing == null ? "OPERATION_SLOT_CREATE" : "OPERATION_SLOT_UPDATE",
                "OPERATION_SLOT", po.getId(), before, dto, po.getDescription());
        return dto;
    }

    @Transactional
    public OperationSlotItemDTO upsertSlotItem(Long slotId, OperationSlotItemCmd cmd, Long operatorUid) {
        OperationSlotPO slot = requireSlot(slotId);
        String sourceType = normalizeSourceType(cmd == null ? null : cmd.getSourceType(), false);
        Long sourceId = requireId(cmd == null ? null : cmd.getSourceId());
        validateSourceOperable(sourceType, sourceId, SOURCE_OPERATION_TOPIC.equals(sourceType));
        OperationSlotItemPO existing = slotItemMapper.selectBySlotSource(slotId, sourceType, sourceId);
        OperationSlotItemPO po = existing == null ? new OperationSlotItemPO() : existing;
        Object before = existing == null ? null : existing;
        if (existing == null) {
            po.setId(idGen.nextId());
            po.setSlotId(slotId);
            po.setSourceType(sourceType);
            po.setSourceId(sourceId);
            po.setCreatedBy(operatorUid);
        }
        po.setItemStatus(normalizeItemStatus(cmd.getStatus(), false));
        po.setSortOrder(cmd.getSortOrder() == null ? 100 : cmd.getSortOrder());
        po.setNote(limit(clean(cmd.getNote()), 500));
        po.setUpdatedBy(operatorUid);
        if (existing == null) {
            slotItemMapper.insert(po);
        } else {
            slotItemMapper.updateById(po);
        }
        OperationSlotItemDTO dto = toSlotItemDto(po, false);
        adminAuditService.recordRequired(operatorUid, existing == null ? "OPERATION_SLOT_ITEM_ADD" : "OPERATION_SLOT_ITEM_UPDATE",
                "OPERATION_SLOT_ITEM", po.getId(), before, dto, po.getNote());
        return dto;
    }

    @Transactional
    public void deleteSlotItem(Long itemId, Long operatorUid, String note) {
        OperationSlotItemPO existing = slotItemMapper.selectById(itemId);
        if (existing == null || existing.getIsDeleted() != null && existing.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        slotItemMapper.deleteById(itemId);
        adminAuditService.recordRequired(operatorUid, "OPERATION_SLOT_ITEM_DELETE",
                "OPERATION_SLOT_ITEM", itemId, existing, Map.of("deleted", true), limit(clean(note), 500));
    }

    public List<OperationTopicDTO> listAdminTopics(String status, String operationType, String keyword, int limit) {
        return topicMapper.listTopics(normalizeWorkflowStatus(status, true), normalizeOperationType(operationType, true),
                        clean(keyword), adminLimit(limit)).stream()
                .map(topic -> toTopicDto(topic, topicSectionMapper.listByTopic(topic.getId(), null, MAX_ADMIN_LIST), false))
                .toList();
    }

    public OperationTopicDTO getPublicTopic(String slug) {
        OperationTopicPO topic = topicMapper.selectBySlug(requireCode(slug, 64));
        if (topic == null || !STATUS_PUBLISHED.equals(topic.getTopicStatus()) || !inWindow(topic.getStartsAt(), topic.getEndsAt())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        OperationTopicDTO snapshot = readSnapshot(topic.getPublishedSnapshotJson());
        OperationTopicDTO dto = snapshot == null
                ? toTopicDto(topic, topicSectionMapper.listByTopic(topic.getId(), ITEM_ACTIVE, MAX_ADMIN_LIST), true)
                : filterTopicSnapshot(snapshot);
        if (dto.getSections() == null || dto.getSections().isEmpty()) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return dto;
    }

    public OperationTopicDTO previewTopic(Long topicId) {
        OperationTopicPO topic = requireTopic(topicId);
        return toTopicDto(topic, topicSectionMapper.listByTopic(topicId, null, MAX_ADMIN_LIST), false);
    }

    @Transactional
    public OperationTopicDTO createTopic(OperationTopicCmd cmd, Long operatorUid) {
        OperationTopicPO po = new OperationTopicPO();
        po.setId(idGen.nextId());
        po.setSlug(requireCode(cmd == null ? null : cmd.getSlug(), 64));
        if (topicMapper.selectBySlug(po.getSlug()) != null) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        applyTopicCmd(po, cmd, operatorUid);
        po.setCreatedBy(operatorUid);
        po.setPreviewToken(token());
        po.setCurrentVersion(0);
        topicMapper.insert(po);
        replaceSections(po.getId(), cmd.getSections(), operatorUid);
        OperationTopicDTO dto = previewTopic(po.getId());
        adminAuditService.recordRequired(operatorUid, "OPERATION_TOPIC_CREATE",
                "OPERATION_TOPIC", po.getId(), null, dto, po.getNote());
        return dto;
    }

    @Transactional
    public OperationTopicDTO updateTopic(Long topicId, OperationTopicCmd cmd, Long operatorUid) {
        OperationTopicPO po = requireTopic(topicId);
        OperationTopicDTO before = previewTopic(topicId);
        applyTopicCmd(po, cmd, operatorUid);
        topicMapper.updateById(po);
        if (cmd != null && cmd.getSections() != null) {
            replaceSections(topicId, cmd.getSections(), operatorUid);
        }
        OperationTopicDTO dto = previewTopic(topicId);
        adminAuditService.recordRequired(operatorUid, "OPERATION_TOPIC_UPDATE",
                "OPERATION_TOPIC", topicId, before, dto, po.getNote());
        return dto;
    }

    @Transactional
    public OperationTopicDTO markPreview(Long topicId, Long operatorUid, String note) {
        OperationTopicPO po = requireTopic(topicId);
        OperationTopicDTO before = previewTopic(topicId);
        po.setTopicStatus(STATUS_PREVIEW);
        po.setNote(limit(clean(note), 500));
        po.setUpdatedBy(operatorUid);
        if (!StringUtils.hasText(po.getPreviewToken())) {
            po.setPreviewToken(token());
        }
        topicMapper.updateById(po);
        OperationTopicDTO dto = previewTopic(topicId);
        adminAuditService.recordRequired(operatorUid, "OPERATION_TOPIC_PREVIEW",
                "OPERATION_TOPIC", topicId, before, dto, po.getNote());
        return dto;
    }

    @Transactional
    public OperationTopicDTO publishTopic(Long topicId, Long operatorUid, String note) {
        OperationTopicPO po = requireTopic(topicId);
        OperationTopicDTO before = previewTopic(topicId);
        OperationTopicDTO current = filterTopicSnapshot(copyTopicSnapshot(before));
        if (current.getSections() == null || current.getSections().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "published topic requires visible sections");
        }
        po.setRollbackSnapshotJson(po.getPublishedSnapshotJson());
        po.setTopicStatus(STATUS_PUBLISHED);
        po.setCurrentVersion((po.getCurrentVersion() == null ? 0 : po.getCurrentVersion()) + 1);
        current.setStatus(STATUS_PUBLISHED);
        current.setCurrentVersion(po.getCurrentVersion());
        po.setPublishedSnapshotJson(writeJson(current));
        po.setNote(limit(clean(note), 500));
        po.setUpdatedBy(operatorUid);
        topicMapper.updateById(po);
        OperationTopicDTO dto = getPublicTopic(po.getSlug());
        adminAuditService.recordRequired(operatorUid, "OPERATION_TOPIC_PUBLISH",
                "OPERATION_TOPIC", topicId, before, dto, po.getNote());
        return dto;
    }

    @Transactional
    public OperationTopicDTO offlineTopic(Long topicId, Long operatorUid, String note) {
        OperationTopicPO po = requireTopic(topicId);
        OperationTopicDTO before = previewTopic(topicId);
        po.setTopicStatus(STATUS_OFFLINE);
        po.setNote(limit(clean(note), 500));
        po.setUpdatedBy(operatorUid);
        topicMapper.updateById(po);
        OperationTopicDTO dto = previewTopic(topicId);
        adminAuditService.recordRequired(operatorUid, "OPERATION_TOPIC_OFFLINE",
                "OPERATION_TOPIC", topicId, before, dto, po.getNote());
        return dto;
    }

    @Transactional
    public OperationTopicDTO rollbackTopic(Long topicId, Long operatorUid, String note) {
        OperationTopicPO po = requireTopic(topicId);
        OperationTopicDTO rollback = readSnapshot(po.getRollbackSnapshotJson());
        if (rollback == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "no rollback snapshot");
        }
        OperationTopicDTO before = readSnapshot(po.getPublishedSnapshotJson());
        if (before == null) {
            before = previewTopic(topicId);
        }
        OperationTopicDTO dto = filterTopicSnapshot(copyTopicSnapshot(rollback));
        if (dto.getSections() == null || dto.getSections().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "rollback snapshot has no visible sections");
        }
        po.setTopicStatus(STATUS_PUBLISHED);
        po.setCurrentVersion((po.getCurrentVersion() == null ? 0 : po.getCurrentVersion()) + 1);
        dto.setStatus(STATUS_PUBLISHED);
        dto.setCurrentVersion(po.getCurrentVersion());
        po.setPublishedSnapshotJson(writeJson(dto));
        po.setRollbackSnapshotJson(writeJson(before));
        po.setNote(limit(clean(note), 500));
        po.setUpdatedBy(operatorUid);
        topicMapper.updateById(po);
        adminAuditService.recordRequired(operatorUid, "OPERATION_TOPIC_ROLLBACK",
                "OPERATION_TOPIC", topicId, before, dto, po.getNote());
        return dto;
    }

    private void applyTopicCmd(OperationTopicPO po, OperationTopicCmd cmd, Long operatorUid) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        po.setTopicName(requireCleanText(cmd.getName(), 64, "topic name required"));
        po.setDescription(limit(clean(cmd.getDescription()), 500));
        po.setOperationType(normalizeOperationType(cmd.getOperationType(), false));
        po.setCoverUrl(limit(clean(cmd.getCoverUrl()), 512));
        po.setDomain(requireOptionalDomain(cmd.getDomain()));
        po.setTopicStatus(normalizeWorkflowStatus(cmd.getStatus(), false));
        po.setSortOrder(cmd.getSortOrder() == null ? 100 : cmd.getSortOrder());
        po.setStartsAt(cmd.getStartsAt());
        po.setEndsAt(cmd.getEndsAt());
        po.setNote(limit(clean(cmd.getNote()), 500));
        po.setUpdatedBy(operatorUid);
        validateDisplayText(po.getTopicName(), po.getDescription(), po.getNote());
    }

    private void replaceSections(Long topicId, List<OperationTopicSectionCmd> sections, Long operatorUid) {
        topicSectionMapper.softDeleteByTopic(topicId, operatorUid);
        if (sections == null) {
            return;
        }
        int order = 10;
        for (OperationTopicSectionCmd cmd : sections) {
            String sourceType = normalizeSourceType(cmd.getSourceType(), false);
            Long sourceId = requireId(cmd.getSourceId());
            validateSourceOperable(sourceType, sourceId, false);
            OperationTopicSectionPO section = new OperationTopicSectionPO();
            section.setId(idGen.nextId());
            section.setTopicId(topicId);
            section.setSectionTitle(limit(clean(cmd.getTitle()), 64));
            section.setSourceType(sourceType);
            section.setSourceId(sourceId);
            section.setSectionStatus(normalizeItemStatus(cmd.getStatus(), false));
            section.setSortOrder(cmd.getSortOrder() == null ? order : cmd.getSortOrder());
            section.setNote(limit(clean(cmd.getNote()), 500));
            section.setCreatedBy(operatorUid);
            section.setUpdatedBy(operatorUid);
            validateDisplayText(section.getSectionTitle(), section.getNote());
            topicSectionMapper.insert(section);
            order += 10;
        }
    }

    private OperationSlotDTO toSlotDto(OperationSlotPO slot, List<OperationSlotItemPO> items, boolean publicOnly) {
        List<OperationSlotItemDTO> itemDtos = items.stream()
                .map(item -> toSlotItemDto(item, publicOnly))
                .filter(Objects::nonNull)
                .toList();
        return OperationSlotDTO.builder()
                .id(slot.getId())
                .slotCode(slot.getSlotCode())
                .name(slot.getSlotName())
                .description(slot.getDescription())
                .status(slot.getSlotStatus())
                .sortOrder(slot.getSortOrder())
                .defaultLimit(slotLimit(slot.getDefaultLimit()))
                .startsAt(slot.getStartsAt())
                .endsAt(slot.getEndsAt())
                .previewToken(publicOnly ? null : slot.getPreviewToken())
                .items(itemDtos)
                .createTime(slot.getCreateTime())
                .updateTime(slot.getUpdateTime())
                .build();
    }

    private OperationSlotItemDTO toSlotItemDto(OperationSlotItemPO item, boolean publicOnly) {
        PostBriefDTO post = null;
        OperationTopicDTO topic = null;
        if (SOURCE_POST.equals(item.getSourceType())) {
            post = loadPost(item.getSourceId());
            if (!PublicContentFilter.isDistributablePost(post)) {
                return publicOnly ? null : OperationSlotItemDTO.builder()
                        .id(item.getId())
                        .sourceType(item.getSourceType())
                        .sourceId(item.getSourceId())
                        .status(item.getItemStatus())
                        .sortOrder(item.getSortOrder())
                        .note(publicOnly ? null : item.getNote())
                        .build();
            }
        } else if (SOURCE_OPERATION_TOPIC.equals(item.getSourceType())) {
            try {
                OperationTopicPO topicPO = topicMapper.selectById(item.getSourceId());
                if (topicPO != null && (!publicOnly || STATUS_PUBLISHED.equals(topicPO.getTopicStatus()))) {
                    topic = publicOnly ? getPublicTopic(topicPO.getSlug())
                            : toTopicDto(topicPO, topicSectionMapper.listByTopic(topicPO.getId(), null, MAX_ADMIN_LIST), false);
                }
            } catch (BizException ignored) {
                topic = null;
            }
            if (topic == null && publicOnly) {
                return null;
            }
        }
        return OperationSlotItemDTO.builder()
                .id(item.getId())
                .sourceType(item.getSourceType())
                .sourceId(item.getSourceId())
                .status(item.getItemStatus())
                .sortOrder(item.getSortOrder())
                .note(publicOnly ? null : item.getNote())
                .post(post)
                .topic(topic)
                .createTime(item.getCreateTime())
                .updateTime(item.getUpdateTime())
                .build();
    }

    private OperationTopicDTO toTopicDto(OperationTopicPO topic, List<OperationTopicSectionPO> sections, boolean publicOnly) {
        List<OperationTopicSectionDTO> sectionDtos = sections.stream()
                .map(section -> toTopicSectionDto(section, publicOnly))
                .filter(Objects::nonNull)
                .toList();
        return OperationTopicDTO.builder()
                .id(topic.getId())
                .slug(topic.getSlug())
                .name(topic.getTopicName())
                .description(topic.getDescription())
                .operationType(topic.getOperationType())
                .coverUrl(topic.getCoverUrl())
                .domain(topic.getDomain())
                .status(topic.getTopicStatus())
                .sortOrder(topic.getSortOrder())
                .startsAt(topic.getStartsAt())
                .endsAt(topic.getEndsAt())
                .previewToken(publicOnly ? null : topic.getPreviewToken())
                .currentVersion(topic.getCurrentVersion())
                .note(publicOnly ? null : topic.getNote())
                .sections(sectionDtos)
                .createTime(topic.getCreateTime())
                .updateTime(topic.getUpdateTime())
                .build();
    }

    private OperationTopicSectionDTO toTopicSectionDto(OperationTopicSectionPO section, boolean publicOnly) {
        PostBriefDTO post = SOURCE_POST.equals(section.getSourceType()) ? loadPost(section.getSourceId()) : null;
        if (SOURCE_POST.equals(section.getSourceType()) && !PublicContentFilter.isDistributablePost(post)) {
            return publicOnly ? null : OperationTopicSectionDTO.builder()
                    .id(section.getId())
                    .title(section.getSectionTitle())
                    .sourceType(section.getSourceType())
                    .sourceId(section.getSourceId())
                    .status(section.getSectionStatus())
                    .sortOrder(section.getSortOrder())
                    .note(section.getNote())
                    .build();
        }
        return OperationTopicSectionDTO.builder()
                .id(section.getId())
                .title(section.getSectionTitle())
                .sourceType(section.getSourceType())
                .sourceId(section.getSourceId())
                .status(section.getSectionStatus())
                .sortOrder(section.getSortOrder())
                .note(publicOnly ? null : section.getNote())
                .post(post)
                .build();
    }

    private OperationCurationItemDTO toCurationDto(OperationCurationItemPO item, PostBriefDTO post) {
        return OperationCurationItemDTO.builder()
                .id(item.getId())
                .sourceType(item.getSourceType())
                .sourceId(item.getSourceId())
                .status(item.getItemStatus())
                .sortOrder(item.getSortOrder())
                .note(item.getNote())
                .post(post)
                .createTime(item.getCreateTime())
                .updateTime(item.getUpdateTime())
                .build();
    }

    private OperationTopicDTO filterTopicSnapshot(OperationTopicDTO snapshot) {
        if (snapshot == null) {
            return null;
        }
        List<OperationTopicSectionDTO> sections = snapshot.getSections() == null ? List.of() : snapshot.getSections().stream()
                .map(section -> {
                    if (!SOURCE_POST.equals(section.getSourceType())) {
                        return null;
                    }
                    PostBriefDTO post = loadPost(section.getSourceId());
                    if (!PublicContentFilter.isDistributablePost(post)) {
                        return null;
                    }
                    section.setPost(post);
                    section.setNote(null);
                    return section;
                })
                .filter(Objects::nonNull)
                .toList();
        snapshot.setSections(sections);
        snapshot.setNote(null);
        snapshot.setPreviewToken(null);
        return snapshot;
    }

    private void validateSourceOperable(String sourceType, Long sourceId, boolean topicMustBePublished) {
        if (SOURCE_POST.equals(sourceType)) {
            if (!PublicContentFilter.isDistributablePost(loadPost(sourceId))) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "source post is not public and governed");
            }
            return;
        }
        if (SOURCE_OPERATION_TOPIC.equals(sourceType)) {
            OperationTopicPO topic = topicMapper.selectById(sourceId);
            if (topic == null || topic.getIsDeleted() != null && topic.getIsDeleted() == 1) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            if (topicMustBePublished && !STATUS_PUBLISHED.equals(topic.getTopicStatus())) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "source topic is not published");
            }
            return;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private PostBriefDTO loadPost(Long postId) {
        return postFacade.batchGetPosts(List.of(postId), null, false).get(postId);
    }

    private Map<Long, PostBriefDTO> loadPosts(Collection<Long> postIds) {
        return postFacade.batchGetPosts(postIds, null, false);
    }

    private OperationCurationItemPO requireCurationItem(Long itemId) {
        OperationCurationItemPO po = curationItemMapper.selectById(requireId(itemId));
        if (po == null || po.getIsDeleted() != null && po.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return po;
    }

    private OperationSlotPO requireSlot(Long slotId) {
        OperationSlotPO po = slotMapper.selectById(requireId(slotId));
        if (po == null || po.getIsDeleted() != null && po.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return po;
    }

    private OperationTopicPO requireTopic(Long topicId) {
        OperationTopicPO po = topicMapper.selectById(requireId(topicId));
        if (po == null || po.getIsDeleted() != null && po.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return po;
    }

    private OperationTopicDTO readSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, OperationTopicDTO.class);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "invalid operation snapshot");
        }
    }

    private OperationTopicDTO copyTopicSnapshot(OperationTopicDTO source) {
        return source == null ? null : objectMapper.convertValue(source, OperationTopicDTO.class);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "operation snapshot write failed");
        }
    }

    private void validateDisplayText(String... values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (PublicContentFilter.isUnsafeSuggestionText(value)) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "operation text is not allowed");
            }
        }
    }

    private String requireCleanText(String value, int max, String message) {
        String text = limit(clean(value), max);
        if (!StringUtils.hasText(text)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), message);
        }
        return text;
    }

    private static String normalizeWorkflowStatus(String status, boolean nullable) {
        if (!StringUtils.hasText(status)) {
            return nullable ? null : STATUS_DRAFT;
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (STATUS_DRAFT.equals(value) || STATUS_PREVIEW.equals(value)
                || STATUS_PUBLISHED.equals(value) || STATUS_OFFLINE.equals(value)) {
            return value;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private static String normalizeItemStatus(String status, boolean nullable) {
        if (!StringUtils.hasText(status)) {
            return nullable ? null : ITEM_ACTIVE;
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (ITEM_ACTIVE.equals(value) || ITEM_PAUSED.equals(value)) {
            return value;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private static String normalizeSourceType(String sourceType, boolean curationPool) {
        if (!StringUtils.hasText(sourceType)) {
            return SOURCE_POST;
        }
        String value = sourceType.trim().toUpperCase(Locale.ROOT);
        if (SOURCE_POST.equals(value) || (!curationPool && SOURCE_OPERATION_TOPIC.equals(value))) {
            return value;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private static String normalizeNullableSourceType(String sourceType) {
        return StringUtils.hasText(sourceType) ? normalizeSourceType(sourceType, true) : null;
    }

    private static String normalizeOperationType(String operationType, boolean nullable) {
        if (!StringUtils.hasText(operationType)) {
            return nullable ? null : TYPE_TOPIC;
        }
        String value = operationType.trim().toUpperCase(Locale.ROOT);
        if (TYPE_TOPIC.equals(value) || TYPE_EVENT.equals(value)) {
            return value;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private static Long requireId(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return id;
    }

    private static Integer requireOptionalDomain(Integer domain) {
        if (domain == null) {
            return null;
        }
        if (PostDomain.isValid(domain)) {
            return domain;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private static String requireCode(String code, int max) {
        String value = limit(clean(code), max);
        if (!StringUtils.hasText(value) || !value.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{1," + (max - 1) + "}")) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static int adminLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 50 : limit, MAX_ADMIN_LIST));
    }

    private static int slotLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return MIN_SLOT_LIMIT;
        }
        return Math.max(MIN_SLOT_LIMIT, Math.min(limit, MAX_SLOT_LIMIT));
    }

    private static boolean inWindow(LocalDateTime startsAt, LocalDateTime endsAt) {
        LocalDateTime now = LocalDateTime.now();
        return (startsAt == null || !startsAt.isAfter(now)) && (endsAt == null || endsAt.isAfter(now));
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String limit(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String token() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
