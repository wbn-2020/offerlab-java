package com.offerlab.community.interaction.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.utils.SqlLimits;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.review.ReviewQueueItemCommand;
import com.offerlab.community.infra.review.ReviewQueuePublisher;
import com.offerlab.community.interaction.api.dto.ContactRequestCreateCmd;
import com.offerlab.community.interaction.api.dto.ContactRequestDTO;
import com.offerlab.community.interaction.api.dto.ContactRequestReportCmd;
import com.offerlab.community.interaction.api.dto.ContactRequestStatsDTO;
import com.offerlab.community.interaction.api.event.ContactRequestCreatedEvent;
import com.offerlab.community.interaction.api.event.ContactRequestHandledEvent;
import com.offerlab.community.interaction.api.event.ContactRequestReportReviewedEvent;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.CommentMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.ContactRequestMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentPO;
import com.offerlab.community.interaction.infrastructure.persistence.po.ContactRequestPO;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.ContactRequestPolicyCheckDTO;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    private static final String MODERATION_SCOPE = ContentModerationService.SCOPE_CONTACT_REQUEST;
    private static final int MIN_MESSAGE_LENGTH = 20;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MAX_REPORT_REASON_LENGTH = 64;
    private static final int MAX_REPORT_DETAIL_LENGTH = 1000;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_EXPIRE_SWEEP_ROWS = 200;
    private static final int DEFAULT_EXPIRE_DAYS = 30;
    private static final int COMMENT_STATUS_NORMAL = 1;
    private static final String REPORT_USER_STATUS_ACTION_TAKEN = "ACTION_TAKEN";
    private static final String REPORT_USER_STATUS_NOT_ACCEPTED = "NOT_ACCEPTED";
    private static final String REPORT_USER_STATUS_CLOSED = "CLOSED";
    private static final String CONTACT_REQUEST_INBOX_PATH = "/me/contact-requests?tab=inbox";

    private final ContactRequestMapper contactRequestMapper;
    private final CommentMapper commentMapper;
    private final UserFacade userFacade;
    private final PostFacade postFacade;
    private final ContentModerationService contentModerationService;
    private final SnowflakeIdGenerator idGen;
    private final ApplicationEventPublisher events;
    private final ReviewQueuePublisher reviewQueuePublisher;

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
        sourceId = validateSourceBinding(requesterUid, receiverUid, sourceType, sourceId);
        String scene = normalizeScene(cmd.getScene());
        String message = normalizeMessage(cmd.getMessage());

        contentModerationService.requireUserCanPublish(requesterUid);
        ContactRequestPolicyCheckDTO policy = requireUserPolicyAllows(requesterUid, receiverUid);
        Long requestId = idGen.nextId();
        ContentModerationService.ModerationDecision moderationDecision = contentModerationService.checkContent(
                requesterUid, MODERATION_SCOPE, ContentModerationService.SOURCE_CONTACT_REQUEST, requestId, message);
        if (moderationDecision.reviewRequired()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "contact request requires review");
        }

        LocalDateTime now = LocalDateTime.now();
        contactRequestMapper.expireStalePending(requesterUid, receiverUid, now);
        ContactRequestPO existing = contactRequestMapper.selectActivePending(requesterUid, receiverUid, now);
        if (existing != null) {
            return toDto(refreshExpired(existing));
        }
        boolean releaseDailyLimitLockOnExit = acquireDailyLimitLock(requesterUid);
        ContactRequestPO po;
        try {
            enforceDailyLimit(requesterUid, policy);

            po = new ContactRequestPO();
            po.setId(requestId);
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
                LocalDateTime retryNow = LocalDateTime.now();
                int expired = contactRequestMapper.expireStalePending(requesterUid, receiverUid, retryNow);
                ContactRequestPO duplicated = contactRequestMapper.selectActivePending(requesterUid, receiverUid, retryNow);
                if (duplicated != null) {
                    return toDto(refreshExpired(duplicated));
                }
                if (expired > 0) {
                    enforceDailyLimit(requesterUid, policy);
                    contactRequestMapper.insert(po);
                } else {
                    throw e;
                }
            }
        } finally {
            if (releaseDailyLimitLockOnExit) {
                releaseDailyLimitLock(requesterUid);
            }
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
        ContactCursor parsedCursor = parseCursor(cursor);
        contactRequestMapper.expireStalePendingForReceiver(receiverUid, LocalDateTime.now(), MAX_EXPIRE_SWEEP_ROWS);
        List<ContactRequestPO> rows = contactRequestMapper.listInbox(receiverUid, normalizedStatus,
                parsedCursor.time(), parsedCursor.id(), SqlLimits.clamp(safeLimit + 1, 1, MAX_PAGE_SIZE + 1));
        return page(rows, normalizedStatus, safeLimit);
    }

    public PageResult<ContactRequestDTO> outbox(Long requesterUid, String status, String cursor, Integer limit) {
        requireUid(requesterUid);
        String normalizedStatus = normalizeStatusFilter(status);
        int safeLimit = clampLimit(limit);
        ContactCursor parsedCursor = parseCursor(cursor);
        contactRequestMapper.expireStalePendingForRequester(requesterUid, LocalDateTime.now(), MAX_EXPIRE_SWEEP_ROWS);
        List<ContactRequestPO> rows = contactRequestMapper.listOutbox(requesterUid, normalizedStatus,
                parsedCursor.time(), parsedCursor.id(), SqlLimits.clamp(safeLimit + 1, 1, MAX_PAGE_SIZE + 1));
        return page(rows, normalizedStatus, safeLimit);
    }

    public ContactRequestStatsDTO stats(Long uid) {
        requireUid(uid);
        LocalDateTime now = LocalDateTime.now();
        contactRequestMapper.expireStalePendingForReceiver(uid, now, MAX_EXPIRE_SWEEP_ROWS);
        contactRequestMapper.expireStalePendingForRequester(uid, now, MAX_EXPIRE_SWEEP_ROWS);
        ContactRequestStatsDTO stats = new ContactRequestStatsDTO();
        applyStats(stats, contactRequestMapper.countInboxByStatus(uid), true);
        applyStats(stats, contactRequestMapper.countOutboxByStatus(uid), false);
        return stats;
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
    public ContactRequestDTO report(Long receiverUid, Long requestId, ContactRequestReportCmd cmd) {
        ContactRequestPO po = refreshExpired(requireExisting(requirePositiveId(requestId)));
        if (!Objects.equals(po.getReceiverUid(), receiverUid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        Long reportId = createReportForContactRequest(receiverUid, po, cmd);
        return handle(receiverUid, requestId, STATUS_REPORTED, reportId);
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

    public List<ContactRequestDTO> listIndependentReports(Long receiverUid, Long cursorId, int limit) {
        requireUid(receiverUid);
        return toDtos(contactRequestMapper.selectIndependentReportsAsReceiver(
                receiverUid, cursorId == null || cursorId <= 0 ? null : cursorId, clampLimit(limit)));
    }

    public ContactRequestDTO getIndependentReport(Long receiverUid, Long reportId) {
        requireUid(receiverUid);
        Long id = requirePositiveId(reportId);
        ContactRequestPO po = contactRequestMapper.selectIndependentReportById(receiverUid, id);
        if (po == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return toDto(po);
    }

    @Transactional
    public void resolveReportFromQueue(Long requestId, Long operatorUid, String queueStatus, String note) {
        Long id = requirePositiveId(requestId);
        ContactRequestPO po = requireExisting(id);
        if (!STATUS_REPORTED.equals(po.getRequestStatus()) || !Objects.equals(po.getReportId(), po.getId())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        contactRequestMapper.touchReportResolved(id);
        events.publishEvent(ContactRequestReportReviewedEvent.builder()
                .requestId(po.getId())
                .reporterUid(po.getReceiverUid())
                .reportId(po.getReportId() == null ? po.getId() : po.getReportId())
                .userStatus(reportUserStatus(queueStatus))
                .targetPath(CONTACT_REQUEST_INBOX_PATH)
                .timestamp(System.currentTimeMillis())
                .build());
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

    private Long createReportForContactRequest(Long receiverUid, ContactRequestPO po, ContactRequestReportCmd cmd) {
        // Reporting must stay possible even after the source post/comment was
        // deleted or made private: the binding was validated when the request was
        // created, and report() has already verified the caller is the receiver.
        // Re-checking source visibility here only lets abusers dodge reports by
        // removing the source afterwards.
        String reason = clean(cmd == null ? null : cmd.getReason(), MAX_REPORT_REASON_LENGTH, "CONTACT_REQUEST_ABUSE");
        String detail = contactReportDetail(po, cmd == null ? null : cmd.getDetail());
        contentModerationService.requireContentAllowed(receiverUid, ContentModerationService.SCOPE_REPORT, reason, detail);
        publishContactRequestQueueItem(receiverUid, po, detail);
        return po.getId();
    }

    private void publishContactRequestQueueItem(Long receiverUid, ContactRequestPO po, String detail) {
        reviewQueuePublisher.upsert(new ReviewQueueItemCommand(
                "CONTACT_REQUEST_REPORT",
                po.getId(),
                "联系请求举报 " + po.getId(),
                detail,
                "high",
                receiverUid,
                70,
                "{\"requestId\":" + po.getId() + ",\"requesterUid\":" + po.getRequesterUid()
                        + ",\"receiverUid\":" + po.getReceiverUid()
                        + ",\"sourceType\":\"" + po.getSourceType()
                        + "\",\"sourceId\":" + po.getSourceId() + "}",
                "联系请求被举报"
        ));
    }

    private String contactReportDetail(ContactRequestPO po, String userDetail) {
        String message = po == null ? null : po.getMessagePreview();
        String detail = "联系请求举报"
                + " / requestId=" + (po == null ? null : po.getId())
                + " / requesterUid=" + (po == null ? null : po.getRequesterUid())
                + " / source=" + (po == null ? null : po.getSourceType())
                + ":" + (po == null ? null : po.getSourceId())
                + " / messagePreviewPresent=" + StringUtils.hasText(message)
                + " / detail=" + clean(userDetail, MAX_REPORT_DETAIL_LENGTH, "");
        return detail.length() <= MAX_REPORT_DETAIL_LENGTH ? detail : detail.substring(0, MAX_REPORT_DETAIL_LENGTH);
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

    private Long validateSourceBinding(Long requesterUid, Long receiverUid, String sourceType, Long sourceId) {
        if ("profile".equals(sourceType)) {
            if (sourceId != null && !Objects.equals(sourceId, receiverUid)) {
                throw new BizException(ErrorCode.FORBIDDEN.getCode(), "contact request source does not match receiver");
            }
            return receiverUid;
        }
        if ("post".equals(sourceType)) {
            PostBriefDTO post = postFacade.batchGetPosts(List.of(requirePositiveId(sourceId)), requesterUid).get(sourceId);
            if (post == null || !Objects.equals(post.getAuthorId(), receiverUid)) {
                throw new BizException(ErrorCode.FORBIDDEN.getCode(), "contact request source does not match receiver");
            }
            return sourceId;
        }
        if ("comment".equals(sourceType)) {
            CommentPO comment = commentMapper.selectById(requirePositiveId(sourceId));
            if (!isVisibleComment(comment)) {
                throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
            }
            if (postFacade.getPost(comment.getPostId(), requesterUid) == null) {
                throw new BizException(ErrorCode.POST_NOT_FOUND);
            }
            if (!Objects.equals(comment.getAuthorId(), receiverUid)
                    && !Objects.equals(comment.getPostAuthorId(), receiverUid)) {
                throw new BizException(ErrorCode.FORBIDDEN.getCode(), "contact request source does not match receiver");
            }
            return sourceId;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private static boolean isVisibleComment(CommentPO comment) {
        return comment != null
                && comment.getCommentStatus() != null
                && comment.getCommentStatus() == COMMENT_STATUS_NORMAL
                && (comment.getIsDeleted() == null || comment.getIsDeleted() == 0);
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

    private boolean acquireDailyLimitLock(Long requesterUid) {
        Integer locked = contactRequestMapper.acquireDailyLimitLock(requesterUid);
        if (locked == null || locked != 1) {
            throw new BizException(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(), "contact request daily limit is busy");
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    releaseDailyLimitLock(requesterUid);
                }
            });
            return false;
        }
        return true;
    }

    private void releaseDailyLimitLock(Long requesterUid) {
        try {
            contactRequestMapper.releaseDailyLimitLock(requesterUid);
        } catch (Exception ignored) {
            // Connection-scoped advisory locks are released automatically when the connection closes.
        }
    }

    private static String reportUserStatus(String queueStatus) {
        String normalized = queueStatus == null ? "" : queueStatus.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "APPROVED" -> REPORT_USER_STATUS_ACTION_TAKEN;
            case "CLOSED" -> REPORT_USER_STATUS_CLOSED;
            default -> REPORT_USER_STATUS_NOT_ACCEPTED;
        };
    }

    private static void applyStats(ContactRequestStatsDTO stats, List<Map<String, Object>> rows, boolean inbox) {
        if (stats == null || rows == null || rows.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String status = String.valueOf(firstPresent(row, "status", "STATUS", "request_status", "REQUEST_STATUS"))
                    .trim()
                    .toUpperCase(Locale.ROOT);
            long count = longValue(firstPresent(row, "count", "COUNT", "COUNT(*)"));
            if (count <= 0) {
                continue;
            }
            if (inbox) {
                stats.setInboxTotal(stats.getInboxTotal() + count);
                setInboxStatusCount(stats, status, count);
            } else {
                stats.setOutboxTotal(stats.getOutboxTotal() + count);
                setOutboxStatusCount(stats, status, count);
            }
        }
    }

    private static void setInboxStatusCount(ContactRequestStatsDTO stats, String status, long count) {
        switch (status) {
            case STATUS_PENDING -> stats.setInboxPending(stats.getInboxPending() + count);
            case STATUS_ACCEPTED -> stats.setInboxAccepted(stats.getInboxAccepted() + count);
            case STATUS_REJECTED -> stats.setInboxRejected(stats.getInboxRejected() + count);
            case STATUS_IGNORED -> stats.setInboxIgnored(stats.getInboxIgnored() + count);
            case STATUS_REPORTED -> stats.setInboxReported(stats.getInboxReported() + count);
            case STATUS_CANCELLED -> stats.setInboxCancelled(stats.getInboxCancelled() + count);
            case STATUS_EXPIRED -> stats.setInboxExpired(stats.getInboxExpired() + count);
            default -> {
            }
        }
    }

    private static void setOutboxStatusCount(ContactRequestStatsDTO stats, String status, long count) {
        switch (status) {
            case STATUS_PENDING -> stats.setOutboxPending(stats.getOutboxPending() + count);
            case STATUS_ACCEPTED -> stats.setOutboxAccepted(stats.getOutboxAccepted() + count);
            case STATUS_REJECTED -> stats.setOutboxRejected(stats.getOutboxRejected() + count);
            case STATUS_IGNORED -> stats.setOutboxIgnored(stats.getOutboxIgnored() + count);
            case STATUS_REPORTED -> stats.setOutboxReported(stats.getOutboxReported() + count);
            case STATUS_CANCELLED -> stats.setOutboxCancelled(stats.getOutboxCancelled() + count);
            case STATUS_EXPIRED -> stats.setOutboxExpired(stats.getOutboxExpired() + count);
            default -> {
            }
        }
    }

    private static Object firstPresent(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
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

    private static String clean(String value, int maxLen, String fallback) {
        String normalized = StringUtils.hasText(value) ? value.trim() : fallback;
        if (normalized == null) {
            return null;
        }
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen);
    }

    private static int clampLimit(Integer limit) {
        int value = limit == null ? DEFAULT_PAGE_SIZE : limit;
        return Math.max(1, Math.min(value, MAX_PAGE_SIZE));
    }

    private static ContactCursor parseCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return ContactCursor.empty();
        }
        try {
            String[] parts = cursor.trim().split(":", 2);
            long millis = Long.parseLong(parts[0]);
            LocalDateTime time = millis > 0 ? LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC) : null;
            Long id = parts.length > 1 ? requirePositiveId(Long.parseLong(parts[1])) : null;
            return new ContactCursor(time, id);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static String cursorOf(ContactRequestPO po) {
        if (po == null || po.getUpdateTime() == null) {
            return null;
        }
        return po.getUpdateTime().toInstant(ZoneOffset.UTC).toEpochMilli() + ":" + po.getId();
    }

    private record ContactCursor(LocalDateTime time, Long id) {
        private static ContactCursor empty() {
            return new ContactCursor(null, null);
        }
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
