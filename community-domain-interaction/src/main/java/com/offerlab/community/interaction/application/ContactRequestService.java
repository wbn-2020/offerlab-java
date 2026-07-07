package com.offerlab.community.interaction.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.utils.SqlLimits;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.interaction.api.dto.ContactRequestCreateCmd;
import com.offerlab.community.interaction.api.dto.ContactRequestDTO;
import com.offerlab.community.interaction.api.event.ContactRequestCreatedEvent;
import com.offerlab.community.interaction.api.event.ContactRequestHandledEvent;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.ContactRequestMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.ContactRequestPO;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.ContactRequestPolicyCheckDTO;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ContactRequestService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_IGNORED = "IGNORED";
    public static final String STATUS_REPORTED = "REPORTED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    private static final Set<String> VALID_SOURCE_TYPES = Set.of("profile", "post", "comment");
    private static final Set<String> VALID_SCENES = Set.of("ask", "supplement", "feedback", "collaboration");
    private static final Set<String> VALID_STATUSES = Set.of(
            STATUS_PENDING, STATUS_ACCEPTED, STATUS_REJECTED, STATUS_IGNORED,
            STATUS_REPORTED, STATUS_CANCELLED, STATUS_EXPIRED);
    private static final String MODERATION_SCOPE = "CONTACT_REQUEST";
    private static final int MIN_MESSAGE_LENGTH = 20;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_EXPIRE_DAYS = 30;

    private final ContactRequestMapper contactRequestMapper;
    private final UserFacade userFacade;
    private final ContentModerationService contentModerationService;
    private final SnowflakeIdGenerator idGen;
    private final ApplicationEventPublisher events;

    @Transactional
    public ContactRequestDTO create(Long requesterUid, ContactRequestCreateCmd cmd) {
        requireUid(requesterUid);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Long receiverUid = requirePositiveId(cmd.getReceiverUid());
        if (Objects.equals(requesterUid, receiverUid)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "cannot send contact request to yourself");
        }
        String sourceType = normalizeSourceType(cmd.getSourceType());
        Long sourceId = normalizeSourceId(sourceType, cmd.getSourceId());
        String scene = normalizeScene(cmd.getScene());
        String message = normalizeMessage(cmd.getMessage());

        contentModerationService.requireUserCanPublish(requesterUid);
        ContactRequestPolicyCheckDTO policy = requireUserPolicyAllows(requesterUid, receiverUid);
        ContentModerationService.ModerationDecision moderationDecision = contentModerationService.checkContent(
                requesterUid, MODERATION_SCOPE, message);
        if (moderationDecision.reviewRequired()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "contact request requires review");
        }

        LocalDateTime now = LocalDateTime.now();
        ContactRequestPO existing = contactRequestMapper.selectActivePending(requesterUid, receiverUid, now);
        if (existing != null) {
            return toDto(refreshExpired(existing));
        }
        enforceDailyLimit(requesterUid, policy);

        ContactRequestPO po = new ContactRequestPO();
        po.setId(idGen.nextId());
        po.setRequesterUid(requesterUid);
        po.setReceiverUid(receiverUid);
        po.setSourceType(sourceType);
        po.setSourceId(sourceId);
        po.setScene(scene);
        po.setMessagePreview(message);
        po.setRequestStatus(STATUS_PENDING);
        po.setExpireTime(now.plusDays(DEFAULT_EXPIRE_DAYS));
        po.setDedupKey(pendingDedupKey(requesterUid, receiverUid));
        po.setIsDeleted(0);
        try {
            contactRequestMapper.insert(po);
        } catch (DuplicateKeyException e) {
            ContactRequestPO duplicated = contactRequestMapper.selectActivePending(requesterUid, receiverUid, LocalDateTime.now());
            if (duplicated != null) {
                return toDto(refreshExpired(duplicated));
            }
            throw e;
        }
        events.publishEvent(ContactRequestCreatedEvent.builder()
                .requestId(po.getId())
                .requesterUid(requesterUid)
                .receiverUid(receiverUid)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .scene(scene)
                .timestamp(System.currentTimeMillis())
                .build());
        return toDto(contactRequestMapper.selectActiveById(po.getId()));
    }

    public PageResult<ContactRequestDTO> inbox(Long receiverUid, String status, String cursor, Integer limit) {
        requireUid(receiverUid);
        String normalizedStatus = normalizeStatusFilter(status);
        int safeLimit = clampLimit(limit);
        List<ContactRequestPO> rows = contactRequestMapper.listInbox(receiverUid, normalizedStatus,
                parseCursor(cursor), SqlLimits.clamp(safeLimit + 1, 1, MAX_PAGE_SIZE + 1));
        return page(rows, normalizedStatus, safeLimit);
    }

    public PageResult<ContactRequestDTO> outbox(Long requesterUid, String status, String cursor, Integer limit) {
        requireUid(requesterUid);
        String normalizedStatus = normalizeStatusFilter(status);
        int safeLimit = clampLimit(limit);
        List<ContactRequestPO> rows = contactRequestMapper.listOutbox(requesterUid, normalizedStatus,
                parseCursor(cursor), SqlLimits.clamp(safeLimit + 1, 1, MAX_PAGE_SIZE + 1));
        return page(rows, normalizedStatus, safeLimit);
    }

    @Transactional
    public ContactRequestDTO accept(Long receiverUid, Long requestId) {
        return handle(receiverUid, requestId, STATUS_ACCEPTED, null);
    }

    @Transactional
    public ContactRequestDTO reject(Long receiverUid, Long requestId) {
        return handle(receiverUid, requestId, STATUS_REJECTED, null);
    }

    @Transactional
    public ContactRequestDTO ignore(Long receiverUid, Long requestId) {
        return handle(receiverUid, requestId, STATUS_IGNORED, null);
    }

    @Transactional
    public ContactRequestDTO report(Long receiverUid, Long requestId) {
        return handle(receiverUid, requestId, STATUS_REPORTED, null);
    }

    private ContactRequestDTO handle(Long receiverUid, Long requestId, String newStatus, Long reportId) {
        requireUid(receiverUid);
        Long id = requirePositiveId(requestId);
        ContactRequestPO po = refreshExpired(requireExisting(id));
        if (!Objects.equals(po.getReceiverUid(), receiverUid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!STATUS_PENDING.equals(po.getRequestStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        int updated = contactRequestMapper.updatePendingStatusAsReceiver(id, receiverUid, newStatus, reportId);
        if (updated <= 0) {
            ContactRequestPO current = refreshExpired(requireExisting(id));
            if (!STATUS_PENDING.equals(current.getRequestStatus())) {
                throw new BizException(ErrorCode.INVALID_STATUS);
            }
            throw new BizException(ErrorCode.DATABASE_ERROR);
        }
        ContactRequestPO handled = requireExisting(id);
        events.publishEvent(ContactRequestHandledEvent.builder()
                .requestId(handled.getId())
                .requesterUid(handled.getRequesterUid())
                .receiverUid(handled.getReceiverUid())
                .requestStatus(handled.getRequestStatus())
                .reportId(handled.getReportId())
                .timestamp(System.currentTimeMillis())
                .build());
        return toDto(handled);
    }

    private PageResult<ContactRequestDTO> page(List<ContactRequestPO> rows, String statusFilter, int size) {
        if (rows == null || rows.isEmpty()) {
            return PageResult.empty();
        }
        List<ContactRequestPO> refreshed = rows.stream()
                .map(this::refreshExpired)
                .filter(row -> statusFilter == null || statusFilter.equals(row.getRequestStatus()))
                .toList();
        boolean hasMore = refreshed.size() > size;
        List<ContactRequestPO> pageRows = hasMore ? refreshed.subList(0, size) : refreshed;
        String next = hasMore && !pageRows.isEmpty() ? cursorOf(pageRows.get(pageRows.size() - 1)) : null;
        return PageResult.of(toDtos(pageRows), next, hasMore);
    }

    private ContactRequestPO refreshExpired(ContactRequestPO po) {
        if (po == null || !STATUS_PENDING.equals(po.getRequestStatus()) || po.getExpireTime() == null) {
            return po;
        }
        if (po.getExpireTime().isAfter(LocalDateTime.now())) {
            return po;
        }
        contactRequestMapper.expirePendingById(po.getId());
        ContactRequestPO refreshed = contactRequestMapper.selectActiveById(po.getId());
        return refreshed == null ? po : refreshed;
    }

    private ContactRequestPO requireExisting(Long requestId) {
        ContactRequestPO po = contactRequestMapper.selectActiveById(requestId);
        if (po == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return po;
    }

    private List<ContactRequestDTO> toDtos(List<ContactRequestPO> rows) {
        Map<Long, UserBriefDTO> users = loadUsers(rows);
        return rows.stream().map(row -> toDto(row, users)).toList();
    }

    private ContactRequestDTO toDto(ContactRequestPO po) {
        if (po == null) {
            return null;
        }
        return toDto(po, loadUsers(List.of(po)));
    }

    private ContactRequestDTO toDto(ContactRequestPO po, Map<Long, UserBriefDTO> users) {
        UserBriefDTO requester = users.get(po.getRequesterUid());
        UserBriefDTO receiver = users.get(po.getReceiverUid());
        return ContactRequestDTO.builder()
                .requestId(po.getId())
                .requesterUid(po.getRequesterUid())
                .requesterName(requester == null ? null : requester.getNickname())
                .receiverUid(po.getReceiverUid())
                .receiverName(receiver == null ? null : receiver.getNickname())
                .sourceType(po.getSourceType())
                .sourceId(po.getSourceId())
                .scene(po.getScene())
                .messagePreview(po.getMessagePreview())
                .requestStatus(po.getRequestStatus())
                .receiverActionTime(po.getReceiverActionTime())
                .expireTime(po.getExpireTime())
                .reportId(po.getReportId())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private Map<Long, UserBriefDTO> loadUsers(Collection<ContactRequestPO> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Set<Long> uids = new HashSet<>();
        rows.forEach(row -> {
            if (row != null) {
                addUid(uids, row.getRequesterUid());
                addUid(uids, row.getReceiverUid());
            }
        });
        if (uids.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserBriefDTO> users = userFacade.batchGetUserBriefs(uids);
        return users == null ? Map.of() : users;
    }

    private static void addUid(Set<Long> uids, Long uid) {
        if (uid != null && uid > 0) {
            uids.add(uid);
        }
    }

    private ContactRequestPolicyCheckDTO requireUserPolicyAllows(Long requesterUid, Long receiverUid) {
        ContactRequestPolicyCheckDTO policy = userFacade.checkContactRequestPolicy(requesterUid, receiverUid);
        if (policy == null || !Boolean.TRUE.equals(policy.getAllowed())) {
            String message = policy == null || !StringUtils.hasText(policy.getReasonMessage())
                    ? ErrorCode.FORBIDDEN.getMessage()
                    : policy.getReasonMessage();
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), message);
        }
        return policy;
    }

    private void enforceDailyLimit(Long requesterUid, ContactRequestPolicyCheckDTO policy) {
        Integer dailyLimit = policy.getContactRequestDailyLimit();
        if (dailyLimit != null && dailyLimit > 0) {
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            if (contactRequestMapper.countCreatedSince(requesterUid, todayStart) >= dailyLimit) {
                throw new BizException(ErrorCode.FORBIDDEN.getCode(), "contact request daily limit exceeded");
            }
        }
    }

    private static String pendingDedupKey(Long requesterUid, Long receiverUid) {
        return "contact:" + requesterUid + ":" + receiverUid + ":pending";
    }

    private static String normalizeSourceType(String sourceType) {
        String value = normalizeLower(sourceType);
        if (!VALID_SOURCE_TYPES.contains(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static Long normalizeSourceId(String sourceType, Long sourceId) {
        if ("profile".equals(sourceType)) {
            return sourceId != null && sourceId > 0 ? sourceId : null;
        }
        return requirePositiveId(sourceId);
    }

    private static String normalizeScene(String scene) {
        String value = normalizeLower(scene);
        if (!VALID_SCENES.contains(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static String normalizeMessage(String message) {
        if (!StringUtils.hasText(message)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String value = message.trim();
        if (value.length() < MIN_MESSAGE_LENGTH || value.length() > MAX_MESSAGE_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "contact request message length must be 20..500");
        }
        return value;
    }

    private static String normalizeStatusFilter(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!VALID_STATUSES.contains(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static String normalizeLower(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static int clampLimit(Integer limit) {
        int value = limit == null ? DEFAULT_PAGE_SIZE : limit;
        return Math.max(1, Math.min(value, MAX_PAGE_SIZE));
    }

    private static LocalDateTime parseCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        try {
            long millis = Long.parseLong(cursor.trim());
            return millis > 0 ? LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC) : null;
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static String cursorOf(ContactRequestPO po) {
        if (po == null || po.getUpdateTime() == null) {
            return null;
        }
        return String.valueOf(po.getUpdateTime().toInstant(ZoneOffset.UTC).toEpochMilli());
    }

    private static void requireUid(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static Long requirePositiveId(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return id;
    }
}
