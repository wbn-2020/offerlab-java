package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.post.api.CreatorCurationFeedbackFacade;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.PublicContentFilter;
import com.offerlab.community.post.api.dto.OperationCurationFeedbackDTO;
import com.offerlab.community.post.api.dto.OperationCandidateDTO;
import com.offerlab.community.post.api.dto.OperationCurationItemCmd;
import com.offerlab.community.post.api.dto.OperationCurationItemDTO;
import com.offerlab.community.post.api.dto.OperationSlotCmd;
import com.offerlab.community.post.api.dto.OperationSlotDTO;
import com.offerlab.community.post.api.dto.OperationSlotItemCmd;
import com.offerlab.community.post.api.dto.OperationSlotItemDTO;
import com.offerlab.community.post.api.dto.OperationTopicCmd;
import com.offerlab.community.post.api.dto.OperationTopicDTO;
import com.offerlab.community.post.api.dto.OperationTopicCandidateHintCmd;
import com.offerlab.community.post.api.dto.OperationTopicSectionCmd;
import com.offerlab.community.post.api.dto.OperationTopicSectionDTO;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.event.OperationCurationSelectedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OperationCurationService implements CreatorCurationFeedbackFacade {
    public static final String SOURCE_POST = "POST";
    public static final String SOURCE_OPERATION_TOPIC = "OPERATION_TOPIC";
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PREVIEW = "PREVIEW";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_OFFLINE = "OFFLINE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";
    public static final String ITEM_ACTIVE = "ACTIVE";
    public static final String ITEM_PAUSED = "PAUSED";
    public static final String TYPE_TOPIC = "TOPIC";
    public static final String TYPE_EVENT = "EVENT";
    public static final String HOME_FEATURED_SLOT_CODE = "HOME_FEATURED";
    public static final String DISCOVERY_FEATURED_TOPICS_SLOT_CODE = "DISCOVERY_FEATURED_TOPICS";
    private static final String OPERATION_SOURCE_REMOTE = "remote";
    private static final String OPERATION_SOURCE_LOCAL = "local";
    private static final String OPERATION_SOURCE_FALLBACK = "fallback";
    private static final String OPERATION_SOURCE_DEMO = "demo";
    private static final String CANDIDATE_SOURCE_PUBLIC_QUERY = "public_content_query";
    private static final String CANDIDATE_SOURCE_EDITOR_HINT = "editor_topic_hint";
    private static final String ELIGIBLE = "eligible";
    private static final String ALREADY_IN_TOPIC = "already_in_topic";
    private static final String FILTERED = "filtered";
    private static final String UNAVAILABLE = "unavailable";
    private static final String DEGRADED = "degraded";
    private static final String PLACEMENT_SLOT = "SLOT";
    private static final String PLACEMENT_TOPIC = "TOPIC";
    private static final String FEEDBACK_ENTRANCE_PREFIX = "/growth/profile";

    private static final int MAX_OPERATION_SLOTS = 2;
    private static final List<String> SUPPORTED_OPERATION_SLOT_CODES = List.of(
            HOME_FEATURED_SLOT_CODE,
            DISCOVERY_FEATURED_TOPICS_SLOT_CODE
    );
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
    private final ApplicationEventPublisher applicationEventPublisher;

    public List<OperationCandidateDTO> listCandidates(String keyword, Integer domain, Integer postType, int limit) {
        Integer activeDomain = requireOptionalDomain(domain);
        List<Long> ids = postMapper.selectOperationCandidates(clean(keyword), activeDomain, postType, adminLimit(limit)).stream()
                .map(PostPO::getId)
                .toList();
        Map<Long, PostBriefDTO> posts = postFacade.batchGetPosts(ids, null, false);
        return ids.stream()
                .map(posts::get)
                .filter(PublicContentFilter::isDistributablePost)
                .map(post -> toEligibleCandidate(CANDIDATE_SOURCE_PUBLIC_QUERY, null, null, null, post,
                        null, false, false))
                .toList();
    }

    public List<OperationCandidateDTO> listTopicCandidates(Long topicId, String keyword,
                                                           Integer domain, Integer postType, int limit) {
        OperationTopicPO topic = requireTopic(topicId);
        return listCandidates(keyword, domain, postType, limit).stream()
                .map(candidate -> withTopicCandidateState(candidate, topic))
                .toList();
    }

    @Transactional
    public List<OperationCandidateDTO> receiveTopicCandidateHints(Long topicId,
                                                                  List<OperationTopicCandidateHintCmd> hints,
                                                                  Long operatorUid) {
        OperationTopicPO topic = requireTopic(topicId);
        if (hints == null || hints.isEmpty()) {
            return List.of();
        }
        return hints.stream()
                .map(hint -> receiveTopicCandidateHint(topic, hint, operatorUid))
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

    @Override
    public List<OperationCurationFeedbackDTO> listCreatorCurationFeedback(Long authorUid, int limit) {
        requireId(authorUid);
        int safeLimit = adminLimit(limit <= 0 ? 20 : limit);
        Map<String, OperationCurationFeedbackDTO> feedback = new LinkedHashMap<>();
        for (OperationSlotPO slot : slotMapper.listSlots(STATUS_PUBLISHED, MAX_ADMIN_LIST)) {
            if (!inWindow(slot.getStartsAt(), slot.getEndsAt())) {
                continue;
            }
            OperationSlotDTO rawSnapshot = readSlotSnapshot(slot.getPublishedSnapshotJson());
            if (rawSnapshot == null || !isRealRemotePlacement(rawSnapshot.getSource(), rawSnapshot.getFallbackReason())) {
                continue;
            }
            OperationSlotDTO snapshot = filterSlotSnapshot(copySlotSnapshot(rawSnapshot));
            if (snapshot == null) {
                continue;
            }
            for (OperationSlotItemDTO item : snapshot.getItems() == null ? List.<OperationSlotItemDTO>of() : snapshot.getItems()) {
                addSlotFeedback(feedback, authorUid, slot, item);
            }
        }
        for (String topicStatus : List.of(STATUS_PUBLISHED, STATUS_ARCHIVED)) {
            for (OperationTopicPO topic : topicMapper.listTopics(topicStatus, null, null, MAX_ADMIN_LIST)) {
                if (STATUS_PUBLISHED.equals(topicStatus) && !inWindow(topic.getStartsAt(), topic.getEndsAt())) {
                    continue;
                }
                OperationTopicDTO rawSnapshot = readSnapshot(topic.getPublishedSnapshotJson());
                if (rawSnapshot == null || !isRealRemotePlacement(rawSnapshot.getSource(), rawSnapshot.getFallbackReason())) {
                    continue;
                }
                OperationTopicDTO snapshot = filterTopicSnapshot(copyTopicSnapshot(rawSnapshot));
                if (snapshot == null) {
                    continue;
                }
                for (OperationTopicSectionDTO section : snapshot.getSections() == null ? List.<OperationTopicSectionDTO>of() : snapshot.getSections()) {
                    addTopicFeedback(feedback, authorUid, topic, snapshot, section);
                }
            }
        }
        return feedback.values().stream()
                .sorted(Comparator.comparing(OperationCurationFeedbackDTO::getUpdateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(safeLimit)
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
        requireSupportedOperationSlot(slot == null ? slotCode : slot.getSlotCode());
        if (slot == null || !STATUS_PUBLISHED.equals(slot.getSlotStatus()) || !inWindow(slot.getStartsAt(), slot.getEndsAt())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        int displayLimit = slotLimit(limit <= 0 ? slot.getDefaultLimit() : limit);
        OperationSlotDTO snapshot = readSlotSnapshot(slot.getPublishedSnapshotJson());
        OperationSlotDTO dto = snapshot == null ? null : filterSlotSnapshot(copySlotSnapshot(snapshot));
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        dto.setItems(dto.getItems().stream().limit(displayLimit).toList());
        return dto;
    }

    @Transactional
    public OperationSlotDTO upsertSlot(OperationSlotCmd cmd, Long operatorUid) {
        String code = requireCode(cmd == null ? null : cmd.getSlotCode(), 64);
        requireSupportedOperationSlot(code);
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
        String lifecycleStatus = existing == null ? STATUS_DRAFT : existing.getSlotStatus();
        po.setSlotStatus(lifecycleStatus);
        po.setSortOrder(cmd.getSortOrder() == null ? 100 : cmd.getSortOrder());
        po.setDefaultLimit(slotLimit(cmd.getDefaultLimit()));
        po.setStartsAt(cmd.getStartsAt());
        po.setEndsAt(cmd.getEndsAt());
        po.setUpdatedBy(operatorUid);
        if (!StringUtils.hasText(po.getPreviewToken())) {
            po.setPreviewToken(token());
        }
        if (po.getCurrentVersion() == null) {
            po.setCurrentVersion(0);
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
        requireSupportedOperationSlot(slot.getSlotCode());
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
        OperationSlotPO slot = requireSlot(existing.getSlotId());
        requireSupportedOperationSlot(slot.getSlotCode());
        slotItemMapper.deleteById(itemId);
        adminAuditService.recordRequired(operatorUid, "OPERATION_SLOT_ITEM_DELETE",
                "OPERATION_SLOT_ITEM", itemId, existing, Map.of("deleted", true), limit(clean(note), 500));
    }

    @Transactional
    public OperationSlotDTO publishSlot(Long slotId, Long operatorUid, String note) {
        OperationSlotPO slot = requireSlot(slotId);
        requireSupportedOperationSlot(slot.getSlotCode());
        OperationSlotDTO before = toSlotDto(slot, slotItemMapper.listBySlot(slotId, null, MAX_ADMIN_LIST), false);
        OperationSlotDTO current = filterSlotSnapshot(copySlotSnapshot(before));
        if (current == null || current.getItems() == null || current.getItems().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "operation slot publish requires at least one governed public item");
        }
        slot.setRollbackSnapshotJson(slot.getPublishedSnapshotJson());
        slot.setSlotStatus(STATUS_PUBLISHED);
        slot.setCurrentVersion((slot.getCurrentVersion() == null ? 0 : slot.getCurrentVersion()) + 1);
        current.setStatus(STATUS_PUBLISHED);
        current.setCurrentVersion(slot.getCurrentVersion());
        current.setSource(OPERATION_SOURCE_REMOTE);
        current.setDegraded(false);
        current.setFallbackReason(null);
        slot.setPublishedSnapshotJson(writeJson(current));
        slot.setUpdatedBy(operatorUid);
        slotMapper.updateById(slot);
        OperationSlotDTO dto = getPublicSlot(slot.getSlotCode(), slot.getDefaultLimit() == null ? 0 : slot.getDefaultLimit());
        adminAuditService.recordRequired(operatorUid, "OPERATION_SLOT_PUBLISH",
                "OPERATION_SLOT", slotId, before, dto, limit(clean(note), 500));
        publishCreatorCurationSelectedEvents(current);
        return dto;
    }

    @Transactional
    public OperationSlotDTO offlineSlot(Long slotId, Long operatorUid, String note) {
        OperationSlotPO slot = requireSlot(slotId);
        requireSupportedOperationSlot(slot.getSlotCode());
        OperationSlotDTO before = toSlotDto(slot, slotItemMapper.listBySlot(slotId, null, MAX_ADMIN_LIST), false);
        slot.setSlotStatus(STATUS_OFFLINE);
        slot.setUpdatedBy(operatorUid);
        slotMapper.updateById(slot);
        OperationSlotDTO dto = toSlotDto(slot, slotItemMapper.listBySlot(slotId, null, MAX_ADMIN_LIST), false);
        adminAuditService.recordRequired(operatorUid, "OPERATION_SLOT_OFFLINE",
                "OPERATION_SLOT", slotId, before, dto, limit(clean(note), 500));
        return dto;
    }

    @Transactional
    public OperationSlotDTO rollbackSlot(Long slotId, Long operatorUid, String note) {
        OperationSlotPO slot = requireSlot(slotId);
        requireSupportedOperationSlot(slot.getSlotCode());
        OperationSlotDTO rollback = readSlotSnapshot(slot.getRollbackSnapshotJson());
        if (rollback == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "no operation slot rollback snapshot");
        }
        OperationSlotDTO before = readSlotSnapshot(slot.getPublishedSnapshotJson());
        if (before == null) {
            before = toSlotDto(slot, slotItemMapper.listBySlot(slotId, null, MAX_ADMIN_LIST), false);
        }
        OperationSlotDTO dto = filterSlotSnapshot(copySlotSnapshot(rollback));
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "rollback snapshot has no governed public items");
        }
        slot.setSlotStatus(STATUS_PUBLISHED);
        slot.setCurrentVersion((slot.getCurrentVersion() == null ? 0 : slot.getCurrentVersion()) + 1);
        dto.setStatus(STATUS_PUBLISHED);
        dto.setCurrentVersion(slot.getCurrentVersion());
        dto.setSource(OPERATION_SOURCE_REMOTE);
        dto.setDegraded(false);
        dto.setFallbackReason(null);
        slot.setPublishedSnapshotJson(writeJson(dto));
        slot.setRollbackSnapshotJson(writeJson(before));
        slot.setUpdatedBy(operatorUid);
        slotMapper.updateById(slot);
        OperationSlotDTO publicDto = getPublicSlot(slot.getSlotCode(), slot.getDefaultLimit() == null ? 0 : slot.getDefaultLimit());
        adminAuditService.recordRequired(operatorUid, "OPERATION_SLOT_ROLLBACK",
                "OPERATION_SLOT", slotId, before, publicDto, limit(clean(note), 500));
        publishCreatorCurationSelectedEvents(dto);
        return publicDto;
    }

    public List<OperationTopicDTO> listAdminTopics(String status, String operationType, String keyword, int limit) {
        return topicMapper.listTopics(normalizeWorkflowStatus(status, true), normalizeOperationType(operationType, true),
                        clean(keyword), adminLimit(limit)).stream()
                .map(topic -> toTopicDto(topic, topicSectionMapper.listByTopic(topic.getId(), null, MAX_ADMIN_LIST), false))
                .toList();
    }

    public OperationTopicDTO getAdminTopic(Long topicId) {
        return previewTopic(topicId);
    }

    public OperationTopicDTO getPublicTopic(String slug) {
        OperationTopicPO topic = topicMapper.selectBySlug(requireCode(slug, 64));
        if (topic == null
                || !isPublicReadableTopicStatus(topic.getTopicStatus())
                || STATUS_PUBLISHED.equals(topic.getTopicStatus()) && !inWindow(topic.getStartsAt(), topic.getEndsAt())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        OperationTopicDTO snapshot = readSnapshot(topic.getPublishedSnapshotJson());
        OperationTopicDTO dto = snapshot == null
                ? toTopicDto(topic, topicSectionMapper.listByTopic(topic.getId(), ITEM_ACTIVE, MAX_ADMIN_LIST), true)
                : filterTopicSnapshot(snapshot);
        if (dto == null || dto.getSections() == null || dto.getSections().isEmpty()) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        dto.setStatus(topic.getTopicStatus());
        dto.setCurrentVersion(topic.getCurrentVersion());
        return dto;
    }

    public OperationTopicDTO previewTopic(Long topicId) {
        OperationTopicPO topic = requireTopic(topicId);
        return toTopicDto(topic, topicSectionMapper.listByTopic(topicId, null, MAX_ADMIN_LIST), false);
    }

    public Map<String, Object> checkTopicPublish(Long topicId) {
        OperationTopicDTO preview = previewTopic(topicId);
        OperationTopicDTO publicSnapshot = filterTopicSnapshot(copyTopicSnapshot(preview));
        int visibleSections = publicSnapshot == null || publicSnapshot.getSections() == null
                ? 0
                : publicSnapshot.getSections().size();
        boolean hasVisibleSections = visibleSections > 0;
        boolean hasOnlyPublicSnapshot = publicSnapshot != null
                && publicSnapshot.getPreviewToken() == null
                && publicSnapshot.getNote() == null
                && isRealRemotePlacement(publicSnapshot.getSource(), publicSnapshot.getFallbackReason());
        boolean hasReasonText = publicSnapshot != null
                && publicSnapshot.getSections() != null
                && publicSnapshot.getSections().stream()
                .allMatch(section -> StringUtils.hasText(section.getReasonText()));
        List<Map<String, Object>> items = List.of(
                publishCheckItem("visible_public_content", "公开可见内容", hasVisibleSections,
                        hasVisibleSections ? "可发布内容 " + visibleSections + " 条" : "没有可发布的公开合规内容"),
                publishCheckItem("public_snapshot_only", "发布快照边界", hasOnlyPublicSnapshot,
                        hasOnlyPublicSnapshot ? "快照不包含草稿、预览 token 或降级来源" : "快照仍包含非公开或降级来源信息"),
                publishCheckItem("reason_text_confirmed", "公开 reasonText", hasReasonText,
                        hasReasonText ? "所有收录内容都有公开理由" : "存在缺少公开 reasonText 的内容")
        );
        return Map.of(
                "topicId", preview.getId(),
                "canPublish", hasVisibleSections && hasOnlyPublicSnapshot && hasReasonText,
                "source", OPERATION_SOURCE_REMOTE,
                "degraded", false,
                "checkedAt", LocalDateTime.now(),
                "items", items
        );
    }

    @Transactional
    public OperationTopicDTO createTopic(OperationTopicCmd cmd, Long operatorUid) {
        OperationTopicPO po = new OperationTopicPO();
        po.setId(idGen.nextId());
        po.setSlug(requireCode(cmd == null ? null : cmd.getSlug(), 64));
        if (topicMapper.selectBySlug(po.getSlug()) != null) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        applyTopicCmd(po, cmd, operatorUid, true);
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
        if (STATUS_ARCHIVED.equals(po.getTopicStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "archived topic is read-only");
        }
        OperationTopicDTO before = previewTopic(topicId);
        applyTopicCmd(po, cmd, operatorUid, false);
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
        requireTopicTransition(po.getTopicStatus(), "preview", STATUS_DRAFT, STATUS_PREVIEW);
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
        requireTopicTransition(po.getTopicStatus(), "publish",
                STATUS_DRAFT, STATUS_PREVIEW, STATUS_PUBLISHED, STATUS_OFFLINE);
        OperationTopicDTO current = filterTopicSnapshot(copyTopicSnapshot(before));
        requirePublishableTopicSnapshot(current, "published topic requires visible sections and public reasonText");
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
        publishCreatorCurationSelectedEvents(dto);
        return dto;
    }

    @Transactional
    public OperationTopicDTO offlineTopic(Long topicId, Long operatorUid, String note) {
        OperationTopicPO po = requireTopic(topicId);
        OperationTopicDTO before = previewTopic(topicId);
        requireTopicTransition(po.getTopicStatus(), "offline", STATUS_PUBLISHED);
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
        requireTopicTransition(po.getTopicStatus(), "rollback", STATUS_PUBLISHED, STATUS_OFFLINE);
        OperationTopicDTO rollback = readSnapshot(po.getRollbackSnapshotJson());
        if (rollback == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "no rollback snapshot");
        }
        OperationTopicDTO before = readSnapshot(po.getPublishedSnapshotJson());
        if (before == null) {
            before = previewTopic(topicId);
        }
        OperationTopicDTO dto = filterTopicSnapshot(copyTopicSnapshot(rollback));
        requirePublishableTopicSnapshot(dto, "rollback snapshot has no visible sections or public reasonText");
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
        publishCreatorCurationSelectedEvents(dto);
        return dto;
    }

    @Transactional
    public OperationTopicDTO archiveTopic(Long topicId, Long operatorUid, String note) {
        OperationTopicPO po = requireTopic(topicId);
        requireTopicTransition(po.getTopicStatus(), "archive", STATUS_PUBLISHED);
        OperationTopicDTO before = getPublicTopic(po.getSlug());
        OperationTopicDTO archived = filterTopicSnapshot(copyTopicSnapshot(readSnapshot(po.getPublishedSnapshotJson())));
        if (archived == null) {
            archived = filterTopicSnapshot(copyTopicSnapshot(before));
        }
        requirePublishableTopicSnapshot(archived, "archived topic requires a visible published snapshot with reasonText");
        po.setTopicStatus(STATUS_ARCHIVED);
        archived.setStatus(STATUS_ARCHIVED);
        archived.setCurrentVersion(po.getCurrentVersion());
        po.setPublishedSnapshotJson(writeJson(archived));
        po.setNote(limit(clean(note), 500));
        po.setUpdatedBy(operatorUid);
        topicMapper.updateById(po);
        OperationTopicDTO dto = getPublicTopic(po.getSlug());
        adminAuditService.recordRequired(operatorUid, "OPERATION_TOPIC_ARCHIVE",
                "OPERATION_TOPIC", topicId, before, dto, po.getNote());
        return dto;
    }

    private void applyTopicCmd(OperationTopicPO po, OperationTopicCmd cmd, Long operatorUid, boolean create) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String requestedStatus = normalizeWorkflowStatus(cmd.getStatus(), true);
        po.setTopicName(requireCleanText(cmd.getName(), 64, "topic name required"));
        po.setDescription(limit(clean(cmd.getDescription()), 500));
        po.setOperationType(normalizeOperationType(cmd.getOperationType(), false));
        po.setCoverUrl(limit(clean(cmd.getCoverUrl()), 512));
        po.setDomain(requireOptionalDomain(cmd.getDomain()));
        if (create) {
            po.setTopicStatus(requestedStatus == null ? STATUS_DRAFT : requireInitialTopicStatus(requestedStatus));
        } else if (requestedStatus != null && !requestedStatus.equals(po.getTopicStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "topic status changes require lifecycle endpoint");
        }
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
            section.setNote(confirmedSectionReason(cmd));
            section.setCreatedBy(operatorUid);
            section.setUpdatedBy(operatorUid);
            validateDisplayText(section.getSectionTitle(), section.getNote());
            topicSectionMapper.insert(section);
            order += 10;
        }
    }

    private String confirmedSectionReason(OperationTopicSectionCmd cmd) {
        if (cmd == null) {
            return null;
        }
        if (Boolean.TRUE.equals(cmd.getReasonConfirmed()) && StringUtils.hasText(cmd.getReasonText())) {
            return publicCurationReason(null, cmd.getReasonText());
        }
        if (StringUtils.hasText(cmd.getReasonText()) || StringUtils.hasText(cmd.getReasonDraftText())) {
            return null;
        }
        return publicCurationReason(null, cmd.getNote());
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
                .currentVersion(slot.getCurrentVersion())
                .source(OPERATION_SOURCE_REMOTE)
                .degraded(false)
                .fallbackReason(null)
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
                        .contentId(item.getSourceId())
                        .contentType(item.getSourceType())
                        .reasonText(item.getNote())
                        .rank(item.getSortOrder())
                        .source(OPERATION_SOURCE_REMOTE)
                        .blocked(true)
                        .blockReasons(List.of("ITEM_NOT_PUBLIC"))
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
                .contentId(item.getSourceId())
                .contentType(item.getSourceType())
                .reasonText(item.getNote())
                .rank(item.getSortOrder())
                .source(OPERATION_SOURCE_REMOTE)
                .blocked(false)
                .blockReasons(List.of())
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
                .source(OPERATION_SOURCE_REMOTE)
                .degraded(false)
                .fallbackReason(null)
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
                    .reasonText(publicOnly ? null : publicCurationReason(null, section.getNote()))
                    .reasonConfirmed(StringUtils.hasText(section.getNote()))
                    .items(List.of())
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
                .reasonText(publicCurationReason(null, section.getNote()))
                .reasonConfirmed(StringUtils.hasText(section.getNote()))
                .post(post)
                .items(List.of())
                .build();
    }

    private OperationCandidateDTO receiveTopicCandidateHint(OperationTopicPO topic,
                                                            OperationTopicCandidateHintCmd hint,
                                                            Long operatorUid) {
        String candidateSource = normalizeCandidateSource(hint == null ? null : hint.getCandidateSource());
        String origin = normalizeCandidateOrigin(hint == null ? null : hint.getSource(), candidateSource);
        String sourceType = normalizeHintSourceType(hint == null ? null : hint.getSourceType());
        Long sourceId = hint == null ? null : hint.getSourceId();
        String reasonDraft = safeCandidateReason(hint == null ? null : hint.getReasonText());
        List<String> blockReasons = new ArrayList<>();
        if (!SOURCE_POST.equals(sourceType)) {
            blockReasons.add("SOURCE_TYPE_UNSUPPORTED");
        }
        if (!isPositive(sourceId)) {
            blockReasons.add("SOURCE_ID_REQUIRED");
        }
        if (isFallbackDemoCandidate(origin, candidateSource, hint == null ? null : hint.getReasonText())) {
            blockReasons.add("FALLBACK_DEMO_READ_ONLY");
        }
        PostBriefDTO post = isPositive(sourceId) && SOURCE_POST.equals(sourceType)
                ? safeLoadPublicSlotPost(sourceId)
                : null;
        if (isPositive(sourceId) && SOURCE_POST.equals(sourceType) && post == null) {
            blockReasons.add("POST_UNAVAILABLE");
        } else if (post != null && !PublicContentFilter.isDistributablePost(post)) {
            blockReasons.add("POST_NOT_PUBLIC_GOVERNED");
        }
        if (StringUtils.hasText(hint == null ? null : hint.getReasonText()) && reasonDraft == null) {
            blockReasons.add("UNSAFE_REASON_DRAFT_DROPPED");
        }
        if (post != null && topicContainsSource(topic.getId(), SOURCE_POST, post.getId())) {
            blockReasons.add(ALREADY_IN_TOPIC.toUpperCase(Locale.ROOT));
        }

        String eligibility = candidateEligibility(blockReasons);
        OperationCandidateDTO candidate = OperationCandidateDTO.builder()
                .candidateId(candidateId(candidateSource, topic.getId(), sourceType, sourceId))
                .candidateSource(candidateSource)
                .topicId(topic.getId())
                .topicSlug(topic.getSlug())
                .sectionKey(limit(clean(hint == null ? null : hint.getSectionKey()), 64))
                .sourceType(sourceType)
                .sourceId(sourceId)
                .source(origin)
                .title(candidateTitle(hint, post))
                .summary(post == null ? null : post.getSummary())
                .reason(ELIGIBLE.equals(eligibility) ? "PUBLIC_VISIBLE_GOVERNED" : blockReasons.get(0))
                .reasonText(null)
                .reasonDraftText(reasonDraft)
                .reasonConfirmed(false)
                .eligibility(eligibility)
                .blockReasons(List.copyOf(blockReasons))
                .href(candidateHref(hint, sourceId))
                .degraded(DEGRADED.equals(eligibility))
                .fallbackReason(DEGRADED.equals(eligibility) ? "fallback/demo candidate is read-only" : null)
                .operable(ELIGIBLE.equals(eligibility))
                .post(post)
                .createdAt(LocalDateTime.now())
                .build();
        if (Boolean.TRUE.equals(hint == null ? null : hint.getPersistCandidate()) && Boolean.TRUE.equals(candidate.getOperable())) {
            persistCandidateDraft(candidate, operatorUid);
        }
        return candidate;
    }

    private OperationCandidateDTO toEligibleCandidate(String candidateSource, Long topicId, String topicSlug,
                                                      String sectionKey, PostBriefDTO post, String reasonDraft,
                                                      boolean reasonConfirmed, boolean alreadyInTopic) {
        String eligibility = alreadyInTopic ? ALREADY_IN_TOPIC : ELIGIBLE;
        return OperationCandidateDTO.builder()
                .candidateId(candidateId(candidateSource, topicId, SOURCE_POST, post.getId()))
                .candidateSource(candidateSource)
                .topicId(topicId)
                .topicSlug(topicSlug)
                .sectionKey(sectionKey)
                .sourceType(SOURCE_POST)
                .sourceId(post.getId())
                .source(OPERATION_SOURCE_REMOTE)
                .title(post.getTitle())
                .summary(post.getSummary())
                .reason(alreadyInTopic ? "ALREADY_IN_TOPIC" : "PUBLIC_VISIBLE_GOVERNED")
                .reasonText(reasonConfirmed ? reasonDraft : null)
                .reasonDraftText(reasonConfirmed ? null : reasonDraft)
                .reasonConfirmed(reasonConfirmed)
                .eligibility(eligibility)
                .blockReasons(alreadyInTopic ? List.of("ALREADY_IN_TOPIC") : List.of())
                .href("/post/" + post.getId())
                .degraded(false)
                .fallbackReason(null)
                .operable(!alreadyInTopic)
                .post(post)
                .createdAt(post.getCreateTime())
                .build();
    }

    private OperationCandidateDTO withTopicCandidateState(OperationCandidateDTO candidate, OperationTopicPO topic) {
        boolean alreadyInTopic = candidate != null
                && topicContainsSource(topic.getId(), candidate.getSourceType(), candidate.getSourceId());
        if (!alreadyInTopic || candidate == null || candidate.getPost() == null) {
            if (candidate != null) {
                candidate.setTopicId(topic.getId());
                candidate.setTopicSlug(topic.getSlug());
            }
            return candidate;
        }
        return toEligibleCandidate(candidate.getCandidateSource(), topic.getId(), topic.getSlug(),
                candidate.getSectionKey(), candidate.getPost(), candidate.getReasonDraftText(),
                Boolean.TRUE.equals(candidate.getReasonConfirmed()), true);
    }

    private void persistCandidateDraft(OperationCandidateDTO candidate, Long operatorUid) {
        OperationCurationItemCmd cmd = new OperationCurationItemCmd();
        cmd.setSourceType(candidate.getSourceType());
        cmd.setSourceId(candidate.getSourceId());
        cmd.setStatus(ITEM_ACTIVE);
        cmd.setNote(candidate.getReasonDraftText());
        upsertCurationItem(cmd, operatorUid);
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

    private void addSlotFeedback(Map<String, OperationCurationFeedbackDTO> feedback, Long authorUid,
                                 OperationSlotPO slot, OperationSlotItemDTO item) {
        if (item == null || !SOURCE_POST.equals(item.getSourceType()) || !ITEM_ACTIVE.equals(item.getStatus())) {
            return;
        }
        PostBriefDTO post = safeAuthorVisiblePost(item.getSourceId(), authorUid);
        if (post == null) {
            return;
        }
        OperationCurationFeedbackDTO dto = OperationCurationFeedbackDTO.builder()
                .contentId(post.getId())
                .contentTitle(post.getTitle())
                .placementType(PLACEMENT_SLOT)
                .placementId(slot.getId())
                .placementKey(slot.getSlotCode())
                .sectionKey(null)
                .reason(publicCurationReason("Selected for a public operation slot.",
                        item.getReasonText(), item.getNote()))
                .entrance(FEEDBACK_ENTRANCE_PREFIX + "?section=curation-feedback&placementType=SLOT&placementKey=" + slot.getSlotCode())
                .status(STATUS_PUBLISHED)
                .updateTime(slot.getUpdateTime())
                .build();
        feedback.putIfAbsent(feedbackKey(dto), dto);
    }

    private void addTopicFeedback(Map<String, OperationCurationFeedbackDTO> feedback, Long authorUid,
                                  OperationTopicPO topic, OperationTopicDTO snapshot,
                                  OperationTopicSectionDTO section) {
        if (section == null || !SOURCE_POST.equals(section.getSourceType()) || !ITEM_ACTIVE.equals(section.getStatus())) {
            return;
        }
        PostBriefDTO post = safeAuthorVisiblePost(section.getSourceId(), authorUid);
        if (post == null) {
            return;
        }
        String sectionKey = StringUtils.hasText(section.getTitle()) ? section.getTitle() : String.valueOf(section.getId());
        OperationCurationFeedbackDTO dto = OperationCurationFeedbackDTO.builder()
                .contentId(post.getId())
                .contentTitle(post.getTitle())
                .placementType(PLACEMENT_TOPIC)
                .placementId(topic.getId())
                .placementKey(topic.getSlug())
                .sectionKey(sectionKey)
                .reason(publicCurationReason("Selected for a public operation topic.",
                        section.getReasonText(), section.getNote(), snapshot.getDescription()))
                .entrance("/topics/" + topic.getSlug())
                .status(STATUS_PUBLISHED)
                .updateTime(topic.getUpdateTime())
                .build();
        feedback.putIfAbsent(feedbackKey(dto), dto);
    }

    private void publishCreatorCurationSelectedEvents(OperationSlotDTO slot) {
        if (slot == null || !STATUS_PUBLISHED.equals(slot.getStatus())
                || !isRealRemotePlacement(slot.getSource(), slot.getFallbackReason())) {
            return;
        }
        for (OperationSlotItemDTO item : slot.getItems() == null ? List.<OperationSlotItemDTO>of() : slot.getItems()) {
            if (item == null || !SOURCE_POST.equals(item.getSourceType()) || !ITEM_ACTIVE.equals(item.getStatus())) {
                continue;
            }
            PostBriefDTO post = safeLoadPublicSlotPost(item.getSourceId());
            if (!isAuthorVisibleFeedbackPost(post)) {
                continue;
            }
            applicationEventPublisher.publishEvent(OperationCurationSelectedEvent.builder()
                    .authorUid(post.getAuthorId())
                    .contentId(post.getId())
                    .contentTitle(post.getTitle())
                    .placementType(PLACEMENT_SLOT)
                    .placementId(slot.getId())
                    .placementKey(slot.getSlotCode())
                    .sectionKey(null)
                    .reason(publicCurationReason("Selected for a public operation slot.",
                            item.getReasonText(), item.getNote()))
                    .entrance(FEEDBACK_ENTRANCE_PREFIX + "?section=curation-feedback&placementType=SLOT&placementKey=" + slot.getSlotCode())
                    .status(STATUS_PUBLISHED)
                    .eventType(OperationCurationSelectedEvent.OPERATION_CURATION_SELECTED)
                    .dedupKey(operationCurationDedupKey(post.getAuthorId(), post.getId(), PLACEMENT_SLOT,
                            slot.getId(), slot.getSlotCode(), null))
                    .build());
        }
    }

    private void publishCreatorCurationSelectedEvents(OperationTopicDTO topic) {
        if (topic == null || !STATUS_PUBLISHED.equals(topic.getStatus())) {
            return;
        }
        for (OperationTopicSectionDTO section : topic.getSections() == null ? List.<OperationTopicSectionDTO>of() : topic.getSections()) {
            if (section == null || !SOURCE_POST.equals(section.getSourceType()) || !ITEM_ACTIVE.equals(section.getStatus())) {
                continue;
            }
            PostBriefDTO post = safeLoadPublicSlotPost(section.getSourceId());
            if (!isAuthorVisibleFeedbackPost(post)) {
                continue;
            }
            String sectionKey = StringUtils.hasText(section.getTitle()) ? section.getTitle() : String.valueOf(section.getId());
            applicationEventPublisher.publishEvent(OperationCurationSelectedEvent.builder()
                    .authorUid(post.getAuthorId())
                    .contentId(post.getId())
                    .contentTitle(post.getTitle())
                    .placementType(PLACEMENT_TOPIC)
                    .placementId(topic.getId())
                    .placementKey(topic.getSlug())
                    .sectionKey(sectionKey)
                    .reason(publicCurationReason("Selected for a public operation topic.",
                            section.getReasonText(), section.getNote(), topic.getDescription()))
                    .entrance("/topics/" + topic.getSlug())
                    .status(STATUS_PUBLISHED)
                    .eventType(OperationCurationSelectedEvent.OPERATION_CURATION_SELECTED)
                    .dedupKey(operationCurationDedupKey(post.getAuthorId(), post.getId(), PLACEMENT_TOPIC,
                            topic.getId(), topic.getSlug(), sectionKey))
                    .build());
        }
    }

    private OperationTopicDTO filterTopicSnapshot(OperationTopicDTO snapshot) {
        if (snapshot == null) {
            return null;
        }
        List<OperationTopicSectionDTO> sections = snapshot.getSections() == null ? List.of() : snapshot.getSections().stream()
                .map(section -> {
                    if (section == null || !SOURCE_POST.equals(section.getSourceType())
                            || !ITEM_ACTIVE.equals(section.getStatus())) {
                        return null;
                    }
                    PostBriefDTO post = loadPost(section.getSourceId());
                    if (!PublicContentFilter.isDistributablePost(post)) {
                        return null;
                    }
                    section.setPost(post);
                    section.setTitle(publicSnapshotText(section.getTitle(), 64));
                    section.setReasonText(publicCurationReason(null, section.getReasonText(), section.getNote()));
                    section.setReasonDraftText(null);
                    section.setReasonConfirmed(StringUtils.hasText(section.getReasonText()));
                    section.setNote(null);
                    section.setItems(List.of());
                    return section;
                })
                .filter(Objects::nonNull)
                .toList();
        if (!StringUtils.hasText(snapshot.getName()) || !isPublicSnapshotText(snapshot.getName())) {
            return null;
        }
        snapshot.setName(limit(snapshot.getName().trim(), 64));
        snapshot.setDescription(publicSnapshotText(snapshot.getDescription(), 500));
        snapshot.setSections(sections);
        snapshot.setNote(null);
        snapshot.setPreviewToken(null);
        snapshot.setSource(OPERATION_SOURCE_REMOTE);
        snapshot.setDegraded(false);
        snapshot.setFallbackReason(null);
        return snapshot;
    }

    private OperationSlotDTO filterSlotSnapshot(OperationSlotDTO snapshot) {
        if (snapshot == null) {
            return null;
        }
        List<OperationSlotItemDTO> items = snapshot.getItems() == null ? List.of() : snapshot.getItems().stream()
                .filter(this::isPublishablePublicSlotItem)
                .map(item -> {
                    if (SOURCE_POST.equals(item.getSourceType())) {
                        PostBriefDTO post = safeLoadPublicSlotPost(item.getSourceId());
                        if (post == null) {
                            return null;
                        }
                        item.setPost(post);
                    } else if (SOURCE_OPERATION_TOPIC.equals(item.getSourceType())) {
                        OperationTopicPO topicPO = topicMapper.selectById(item.getSourceId());
                        try {
                            item.setTopic(topicPO == null ? null : getPublicTopic(topicPO.getSlug()));
                        } catch (BizException ignored) {
                            return null;
                        }
                    }
                    item.setReasonText(item.getReasonText() == null ? item.getNote() : item.getReasonText());
                    item.setNote(null);
                    item.setContentId(item.getSourceId());
                    item.setContentType(item.getSourceType());
                    item.setSource(OPERATION_SOURCE_REMOTE);
                    item.setBlocked(false);
                    item.setBlockReasons(List.of());
                    return item;
                })
                .filter(Objects::nonNull)
                .toList();
        snapshot.setSlotCode(requireSupportedOperationSlot(snapshot.getSlotCode()));
        snapshot.setPreviewToken(null);
        snapshot.setSource(OPERATION_SOURCE_REMOTE);
        snapshot.setDegraded(false);
        snapshot.setFallbackReason(null);
        snapshot.setItems(items);
        return snapshot;
    }

    private boolean isPublishablePublicSlotItem(OperationSlotItemDTO item) {
        if (item == null || !ITEM_ACTIVE.equals(item.getStatus())) {
            return false;
        }
        if (SOURCE_POST.equals(item.getSourceType())) {
            PostBriefDTO post = safeLoadPublicSlotPost(item.getSourceId());
            return post != null && PublicContentFilter.isDistributablePost(post);
        }
        if (SOURCE_OPERATION_TOPIC.equals(item.getSourceType())) {
            try {
                OperationTopicPO topic = topicMapper.selectById(item.getSourceId());
                return topic != null && STATUS_PUBLISHED.equals(topic.getTopicStatus())
                        && getPublicTopic(topic.getSlug()) != null;
            } catch (BizException ignored) {
                return false;
            }
        }
        return false;
    }

    private boolean isRealRemotePlacement(String source, String fallbackReason) {
        String normalizedSource = source == null ? "" : source.toLowerCase(Locale.ROOT);
        String normalizedReason = fallbackReason == null ? "" : fallbackReason.toLowerCase(Locale.ROOT);
        return OPERATION_SOURCE_REMOTE.equals(normalizedSource)
                && !normalizedReason.contains("fallback")
                && !normalizedReason.contains("demo")
                && !normalizedReason.contains("fixture");
    }

    private static Map<String, Object> publishCheckItem(String code, String label, boolean passed, String detail) {
        return Map.of(
                "code", code,
                "label", label,
                "passed", passed,
                "detail", detail
        );
    }

    private static void requirePublishableTopicSnapshot(OperationTopicDTO snapshot, String message) {
        boolean publishable = snapshot != null
                && snapshot.getSections() != null
                && !snapshot.getSections().isEmpty()
                && snapshot.getSections().stream()
                .allMatch(section -> section != null && StringUtils.hasText(section.getReasonText()));
        if (!publishable) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), message);
        }
    }

    private static boolean isPublicReadableTopicStatus(String status) {
        return STATUS_PUBLISHED.equals(status) || STATUS_ARCHIVED.equals(status);
    }

    private PostBriefDTO safeAuthorVisiblePost(Long postId, Long authorUid) {
        PostBriefDTO post = safeLoadPublicSlotPost(postId);
        if (!isAuthorVisibleFeedbackPost(post) || !Objects.equals(post.getAuthorId(), authorUid)) {
            return null;
        }
        return post;
    }

    private boolean isAuthorVisibleFeedbackPost(PostBriefDTO post) {
        return PublicContentFilter.isDistributablePost(post)
                && post.getAuthorId() != null
                && !Boolean.TRUE.equals(post.getAnonymous());
    }

    private static String feedbackKey(OperationCurationFeedbackDTO dto) {
        return String.join(":",
                String.valueOf(dto.getContentId()),
                String.valueOf(dto.getPlacementType()),
                String.valueOf(dto.getPlacementId()),
                String.valueOf(dto.getPlacementKey()),
                String.valueOf(dto.getSectionKey()));
    }

    private static String operationCurationDedupKey(Long authorUid, Long contentId, String placementType,
                                                    Long placementId, String placementKey, String sectionKey) {
        return String.join(":",
                "operation_curation_selected",
                String.valueOf(authorUid),
                String.valueOf(contentId),
                String.valueOf(placementType),
                String.valueOf(placementId),
                String.valueOf(placementKey),
                String.valueOf(sectionKey),
                OperationCurationSelectedEvent.OPERATION_CURATION_SELECTED);
    }

    private boolean topicContainsSource(Long topicId, String sourceType, Long sourceId) {
        if (!isPositive(topicId) || !StringUtils.hasText(sourceType) || !isPositive(sourceId)) {
            return false;
        }
        return topicSectionMapper.listByTopic(topicId, null, MAX_ADMIN_LIST).stream()
                .anyMatch(section -> sourceType.equals(section.getSourceType())
                        && Objects.equals(sourceId, section.getSourceId()));
    }

    private static String candidateId(String candidateSource, Long topicId, String sourceType, Long sourceId) {
        return String.join(":",
                "topic_candidate",
                String.valueOf(candidateSource),
                String.valueOf(topicId),
                String.valueOf(sourceType),
                String.valueOf(sourceId));
    }

    private static String normalizeCandidateSource(String value) {
        if (!StringUtils.hasText(value)) {
            return CANDIDATE_SOURCE_EDITOR_HINT;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (List.of(CANDIDATE_SOURCE_PUBLIC_QUERY, "curation_pool", CANDIDATE_SOURCE_EDITOR_HINT,
                "creator_workbench_idea", "search_gap", "manual").contains(normalized)) {
            return normalized;
        }
        return CANDIDATE_SOURCE_EDITOR_HINT;
    }

    private static String normalizeCandidateOrigin(String source, String candidateSource) {
        String normalized = StringUtils.hasText(source) ? source.trim().toLowerCase(Locale.ROOT) : OPERATION_SOURCE_REMOTE;
        if (OPERATION_SOURCE_REMOTE.equals(normalized) || OPERATION_SOURCE_LOCAL.equals(normalized)
                || OPERATION_SOURCE_FALLBACK.equals(normalized) || OPERATION_SOURCE_DEMO.equals(normalized)) {
            return normalized;
        }
        if (isFallbackDemoText(candidateSource)) {
            return OPERATION_SOURCE_DEMO;
        }
        return OPERATION_SOURCE_REMOTE;
    }

    private static String normalizeHintSourceType(String sourceType) {
        if (!StringUtils.hasText(sourceType)) {
            return SOURCE_POST;
        }
        String value = sourceType.trim().toUpperCase(Locale.ROOT);
        return SOURCE_POST.equals(value) ? value : value;
    }

    private static boolean isFallbackDemoCandidate(String source, String candidateSource, String reasonText) {
        return OPERATION_SOURCE_FALLBACK.equals(source)
                || OPERATION_SOURCE_DEMO.equals(source)
                || isFallbackDemoText(candidateSource)
                || isFallbackDemoText(reasonText);
    }

    private static boolean isFallbackDemoText(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String text = value.toLowerCase(Locale.ROOT);
        return text.contains("fallback") || text.contains("demo") || text.contains("fixture");
    }

    private static String candidateEligibility(List<String> blockReasons) {
        if (blockReasons == null || blockReasons.isEmpty()) {
            return ELIGIBLE;
        }
        List<String> blocking = blockReasons.stream()
                .filter(reason -> !"UNSAFE_REASON_DRAFT_DROPPED".equals(reason))
                .toList();
        if (blocking.isEmpty()) {
            return ELIGIBLE;
        }
        if (blocking.contains("FALLBACK_DEMO_READ_ONLY")) {
            return DEGRADED;
        }
        if (blocking.contains("SOURCE_ID_REQUIRED") || blocking.contains("POST_UNAVAILABLE")) {
            return UNAVAILABLE;
        }
        if (blocking.contains("ALREADY_IN_TOPIC")) {
            return ALREADY_IN_TOPIC;
        }
        return FILTERED;
    }

    private static String safeCandidateReason(String value) {
        return publicCurationReason(null, value);
    }

    private static String candidateTitle(OperationTopicCandidateHintCmd hint, PostBriefDTO post) {
        if (post != null && StringUtils.hasText(post.getTitle())) {
            return post.getTitle();
        }
        String title = limit(clean(hint == null ? null : hint.getTitle()), 120);
        if (PublicContentFilter.isSyntheticText(title) || PublicContentFilter.isUnsafeSuggestionText(title)) {
            return null;
        }
        return title;
    }

    private static String candidateHref(OperationTopicCandidateHintCmd hint, Long sourceId) {
        String href = firstText(hint == null ? null : hint.getHref(), hint == null ? null : hint.getReturnHref());
        if (StringUtils.hasText(href) && href.startsWith("/") && !href.startsWith("//")
                && !href.startsWith("/api/") && !href.contains(" ")) {
            return limit(href, 512);
        }
        return isPositive(sourceId) ? "/post/" + sourceId : null;
    }

    private static boolean isPositive(Long value) {
        return value != null && value > 0;
    }

    private static String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static String publicCurationReason(String fallback, String... values) {
        String candidate = firstText(values);
        if (!StringUtils.hasText(candidate)
                || PublicContentFilter.isSyntheticText(candidate)
                || PublicContentFilter.isUnsafeSuggestionText(candidate)) {
            return fallback;
        }
        return limit(candidate, 120);
    }

    private static String publicSnapshotText(String value, int max) {
        return StringUtils.hasText(value) && isPublicSnapshotText(value) ? limit(value.trim(), max) : null;
    }

    private static boolean isPublicSnapshotText(String value) {
        return !StringUtils.hasText(value)
                || !PublicContentFilter.isSyntheticText(value) && !PublicContentFilter.isUnsafeSuggestionText(value);
    }

    private PostBriefDTO safeLoadPublicSlotPost(Long postId) {
        try {
            return loadPost(postId);
        } catch (BizException ignored) {
            return null;
        }
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
            OperationTopicDTO snapshot = objectMapper.readValue(json, OperationTopicDTO.class);
            normalizeReadSnapshot(snapshot);
            return snapshot;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "invalid operation snapshot");
        }
    }

    private OperationSlotDTO readSlotSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            OperationSlotDTO snapshot = objectMapper.readValue(json, OperationSlotDTO.class);
            normalizeReadSnapshot(snapshot);
            return snapshot;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "invalid operation slot snapshot");
        }
    }

    private void normalizeReadSnapshot(OperationTopicDTO snapshot) {
        if (snapshot == null) {
            return;
        }
        if (!StringUtils.hasText(snapshot.getSchemaVersion())) {
            snapshot.setSchemaVersion("operation-topic-snapshot.legacy-v0");
        }
        if (snapshot.getSourceVersion() == null) {
            snapshot.setSourceVersion(snapshot.getCurrentVersion());
        }
    }

    private void normalizeReadSnapshot(OperationSlotDTO snapshot) {
        if (snapshot == null) {
            return;
        }
        if (!StringUtils.hasText(snapshot.getSchemaVersion())) {
            snapshot.setSchemaVersion("operation-slot-snapshot.legacy-v0");
        }
        if (snapshot.getSourceVersion() == null) {
            snapshot.setSourceVersion(snapshot.getCurrentVersion());
        }
    }

    private OperationTopicDTO copyTopicSnapshot(OperationTopicDTO source) {
        return source == null ? null : objectMapper.convertValue(source, OperationTopicDTO.class);
    }

    private OperationSlotDTO copySlotSnapshot(OperationSlotDTO source) {
        return source == null ? null : objectMapper.convertValue(source, OperationSlotDTO.class);
    }

    private String writeJson(Object value) {
        try {
            applySnapshotVersion(value);
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "operation snapshot write failed");
        }
    }

    private void applySnapshotVersion(Object value) {
        if (value instanceof OperationTopicDTO topic) {
            topic.setSchemaVersion("operation-topic-snapshot.v1");
            topic.setSourceVersion(topic.getCurrentVersion());
        }
        if (value instanceof OperationSlotDTO slot) {
            slot.setSchemaVersion("operation-slot-snapshot.v1");
            slot.setSourceVersion(slot.getCurrentVersion());
        }
    }

    private void validateDisplayText(String... values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (PublicContentFilter.isSyntheticText(value) || PublicContentFilter.isUnsafeSuggestionText(value)) {
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
                || STATUS_PUBLISHED.equals(value) || STATUS_OFFLINE.equals(value)
                || STATUS_ARCHIVED.equals(value)) {
            return value;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private static String requireInitialTopicStatus(String status) {
        if (STATUS_DRAFT.equals(status) || STATUS_PREVIEW.equals(status)) {
            return status;
        }
        throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "initial topic status must be DRAFT or PREVIEW");
    }

    private static void requireTopicTransition(String currentStatus, String action, String... allowedStatuses) {
        String current = normalizeWorkflowStatus(currentStatus, false);
        for (String allowed : allowedStatuses) {
            if (allowed.equals(current)) {
                return;
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                "topic " + action + " is not allowed from " + current);
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

    private static String requireSupportedOperationSlot(String slotCode) {
        String value = requireCode(slotCode, 64);
        if (!SUPPORTED_OPERATION_SLOT_CODES.contains(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "V3 P0 only supports HOME_FEATURED and DISCOVERY_FEATURED_TOPICS");
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
