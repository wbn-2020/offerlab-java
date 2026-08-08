package com.offerlab.community.post.collaboration.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.CommunityRoleAccessService;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.ContentMaintenanceBatchTaskCoordinationResult;
import com.offerlab.community.post.api.ContentMaintenanceTaskBatchDispatchCmd;
import com.offerlab.community.post.api.ContentMaintenanceTaskCommandFacade;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.PublicPostUpdateDTO;
import com.offerlab.community.post.api.quality.PostContentRevisionQuery;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryFacade;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryResult;
import com.offerlab.community.post.api.quality.PostContentRevisionSnapshot;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceCandidateDTO;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceLinkedPublicPostDTO;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceRevisionEvidenceDTO;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskAttemptDTO;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskCloseCmd;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskCreateCmd;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskDTO;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskReassignCmd;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskReviewContextDTO;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskReviewCmd;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskSubmitCmd;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationRows.SeriesRow;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskAttemptRow;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceBatchTaskCountsRow;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceBatchTaskCoordinationTaskRow;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskRow;
import com.offerlab.community.post.domain.model.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ContentMaintenanceTaskService implements ContentMaintenanceTaskCommandFacade {

    private static final String MIGRATION =
            "db/migration/20260805_channel_quality_review_dispatch_batch.sql";
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_BATCH_TASKS = 20;
    private static final int PUBLIC_UPDATE_QUERY_LIMIT = 20;
    private static final int PUBLIC_UPDATE_RESPONSE_LIMIT = 3;
    private static final String AVAILABLE = "AVAILABLE";
    private static final String NOT_LINKED = "NOT_LINKED";
    private static final String UNAVAILABLE = "UNAVAILABLE";
    private static final String NOT_SUBMITTED = "NOT_SUBMITTED";
    private static final String NON_POST_DELIVERY = "NON_POST_DELIVERY";
    private static final String SAME_POST_UPDATED = "SAME_POST_UPDATED";
    private static final String SAME_POST_NO_PUBLIC_UPDATE = "SAME_POST_NO_PUBLIC_UPDATE";
    private static final String SEPARATE_PUBLIC_DELIVERY = "SEPARATE_PUBLIC_DELIVERY";
    private static final String SOURCE_UNAVAILABLE = "SOURCE_UNAVAILABLE";
    private static final String DELIVERY_UNAVAILABLE = "DELIVERY_UNAVAILABLE";
    private static final String EVIDENCE_UNAVAILABLE = "EVIDENCE_UNAVAILABLE";
    private static final LocalDateTime EARLIEST_REVISION_WINDOW_START =
            LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final Set<String> SOURCE_TYPES = Set.of(
            "CHANNEL_HEALTH", "SEARCH_GAP", "SUGGESTION", "FRESHNESS",
            "PROFILE_CONFIRMATION", "QUESTION", "MANUAL");
    private static final Set<String> STATUSES = Set.of(
            "OPEN", "CLAIMED", "SUBMITTED", "COMPLETED", "CLOSED");
    private static final Set<String> PRIORITIES = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Set<String> APPROVED_REASON_CODES =
            Set.of("QUALITY_VERIFIED", "EVIDENCE_SUFFICIENT");
    private static final Set<String> REJECTED_REASON_CODES =
            Set.of("CONTENT_INCOMPLETE", "PUBLIC_EVIDENCE_MISSING", "SCOPE_MISMATCH", "OTHER");
    private static final Set<String> CLOSE_REASON_CODES =
            Set.of("OUT_OF_SCOPE", "DUPLICATE", "NO_LONGER_RELEVANT", "AUTHOR_UNRESPONSIVE", "OTHER");

    private final ContentMaintenanceTaskMapper mapper;
    private final SnowflakeIdGenerator idGenerator;
    private final DomainModeratorService domainModeratorService;
    private final CommunityRoleAccessService communityRoleAccessService;
    private final AdminAuditService adminAuditService;
    private final PostFacade postFacade;
    private final CollaborationMapper collaborationMapper;
    private final PostContentRevisionQueryFacade postContentRevisionQuery;

    @Transactional
    public ContentMaintenanceTaskDTO create(ContentMaintenanceTaskCreateCmd cmd, Long operatorUid) {
        requireTable();
        if (cmd == null) throw new BizException(ErrorCode.PARAM_ERROR);
        int domain = requireDomain(cmd.getDomain());
        requireModerate(operatorUid, domain);
        String normalizedSourceType = sourceType(cmd.getSourceType());
        if ("CHANNEL_HEALTH".equals(normalizedSourceType)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "CHANNEL_HEALTH tasks must be dispatched from a quality review batch");
        }
        return createTask(
                domain,
                normalizedSourceType,
                positiveOrNull(cmd.getSourceRefId()),
                positiveOrNull(cmd.getSourcePostId()),
                operatorUid,
                requireId(cmd.getAssigneeUid()),
                required(cmd.getTitle(), 160),
                required(cmd.getDetail(), 2000),
                null,
                "MEDIUM",
                null,
                operatorUid);
    }

    @Override
    @Transactional
    public ContentMaintenanceTaskDTO dispatchChannelHealthTask(
            ContentMaintenanceTaskBatchDispatchCmd cmd,
            Long operatorUid) {
        requireTable();
        if (cmd == null) throw new BizException(ErrorCode.PARAM_ERROR);
        int domain = requireDomain(cmd.getDomain());
        requireModerate(operatorUid, domain);
        Long batchId = requireId(cmd.getBatchId());
        if (mapper.dispatchBatchExists(batchId, domain) <= 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Long sourcePostId = requireId(cmd.getSourcePostId());
        Long sourceRefId = requireId(cmd.getSourceRefId());
        Long assigneeUid = requireId(cmd.getAssigneeUid());
        if (mapper.userExists(assigneeUid) <= 0) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        requireMaintenanceRole(assigneeUid, domain);
        return createTask(
                domain,
                "CHANNEL_HEALTH",
                sourceRefId,
                sourcePostId,
                operatorUid,
                assigneeUid,
                required(cmd.getTitle(), 160),
                required(cmd.getDetail(), 2000),
                batchId,
                priority(cmd.getPriority()),
                cmd.getDueAt(),
                operatorUid);
    }

    @Override
    @Transactional
    public ContentMaintenanceBatchTaskCoordinationResult extendBatchActiveTaskDueAt(
            Long batchId,
            Integer domain,
            LocalDateTime effectiveDueAt,
            Long operatorUid) {
        requireTable();
        int safeDomain = requireDomain(domain);
        long safeBatchId = requireId(batchId);
        requireModerate(operatorUid, safeDomain);
        if (effectiveDueAt == null) throw new BizException(ErrorCode.PARAM_ERROR);
        if (mapper.dispatchBatchExists(safeBatchId, safeDomain) <= 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        ContentMaintenanceBatchTaskCountsRow counts = lockBatchTaskCounts(safeBatchId, safeDomain);
        int active = counts.getActiveTaskCount();
        if (active <= 0) throw new BizException(ErrorCode.INVALID_STATUS);
        int affected = mapper.extendBatchActiveDueAt(safeBatchId, safeDomain, effectiveDueAt);
        if (affected != active) throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        return new ContentMaintenanceBatchTaskCoordinationResult(
                counts.getOpenTaskCount(), active, affected, null);
    }

    @Override
    @Transactional
    public ContentMaintenanceBatchTaskCoordinationResult reassignBatchActiveTasks(
            Long batchId,
            Integer domain,
            Long replacementUid,
            Long operatorUid) {
        requireTable();
        int safeDomain = requireDomain(domain);
        long safeBatchId = requireId(batchId);
        long safeReplacementUid = requireId(replacementUid);
        requireModerate(operatorUid, safeDomain);
        if (mapper.dispatchBatchExists(safeBatchId, safeDomain) <= 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (mapper.userExists(safeReplacementUid) <= 0) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        requireMaintenanceRole(safeReplacementUid, safeDomain);
        BatchTaskCoordinationSnapshot snapshot =
                lockBatchTaskCoordinationSnapshot(safeBatchId, safeDomain);
        ContentMaintenanceBatchTaskCountsRow counts = snapshot.counts();
        if (counts.getReassignableTaskCount() <= 0) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        List<Long> previousAssignees = snapshot.rows().stream()
                .filter(row -> Set.of("OPEN", "CLAIMED").contains(row.getStatus()))
                .filter(row -> !Long.valueOf(safeReplacementUid).equals(row.getAssigneeUid()))
                .map(ContentMaintenanceBatchTaskCoordinationTaskRow::getAssigneeUid)
                .distinct()
                .toList();
        int expectedAffected = (int) snapshot.rows().stream()
                .filter(row -> Set.of("OPEN", "CLAIMED").contains(row.getStatus()))
                .filter(row -> !Long.valueOf(safeReplacementUid).equals(row.getAssigneeUid()))
                .count();
        if (expectedAffected <= 0) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        int affected = mapper.reassignBatchActiveTasks(safeBatchId, safeDomain, safeReplacementUid);
        if (affected != expectedAffected) throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        return new ContentMaintenanceBatchTaskCoordinationResult(
                counts.getOpenTaskCount(), counts.getActiveTaskCount(), affected,
                previousAssignees.size() == 1 ? previousAssignees.get(0) : null);
    }

    @Override
    @Transactional
    public ContentMaintenanceBatchTaskCoordinationResult withdrawBatchOpenTasks(
            Long batchId,
            Integer domain,
            Integer expectedOpenTaskCount,
            Integer expectedActiveTaskCount,
            Long operatorUid,
            String note) {
        requireTable();
        int safeDomain = requireDomain(domain);
        long safeBatchId = requireId(batchId);
        requireModerate(operatorUid, safeDomain);
        if (expectedOpenTaskCount == null || expectedOpenTaskCount < 0
                || expectedActiveTaskCount == null || expectedActiveTaskCount < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (mapper.dispatchBatchExists(safeBatchId, safeDomain) <= 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        ContentMaintenanceBatchTaskCountsRow counts = lockBatchTaskCounts(safeBatchId, safeDomain);
        if (!expectedOpenTaskCount.equals(counts.getOpenTaskCount())
                || !expectedActiveTaskCount.equals(counts.getActiveTaskCount())) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        if (counts.getOpenTaskCount() <= 0) throw new BizException(ErrorCode.INVALID_STATUS);
        String safeNote = required(note, 500);
        int affected = mapper.withdrawBatchOpenTasks(safeBatchId, safeDomain, operatorUid, safeNote);
        if (affected != counts.getOpenTaskCount()) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        return new ContentMaintenanceBatchTaskCoordinationResult(
                counts.getOpenTaskCount(), counts.getActiveTaskCount(), affected, null);
    }

    private ContentMaintenanceTaskDTO createTask(
            int domain,
            String sourceType,
            Long sourceRefId,
            Long sourcePostId,
            Long operatorUid,
            Long assigneeUid,
            String title,
            String detail,
            Long dispatchBatchId,
            String priority,
            LocalDateTime dueAt,
            Long auditUid) {
        if (mapper.userExists(assigneeUid) <= 0) throw new BizException(ErrorCode.USER_NOT_FOUND);
        requireMaintenanceRole(assigneeUid, domain);
        requireSourceAssociation(sourceType, sourcePostId, sourceRefId, domain, operatorUid);
        Long id = idGenerator.nextId();
        try {
            mapper.insert(id, domain, sourceType, sourceRefId,
                    sourcePostId, operatorUid, assigneeUid, title, detail,
                    dispatchBatchId, priority, dueAt);
        } catch (DuplicateKeyException ignored) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        ContentMaintenanceTaskRow created = requireTask(mapper.lockById(id));
        adminAuditService.recordRequired(auditUid, "CONTENT_MAINTENANCE_TASK_CREATE",
                "CONTENT_MAINTENANCE_TASK", id, null, created,
                dispatchBatchId == null ? "maintenance task created" : "channel-health batch task dispatched");
        return toDto(created, auditUid);
    }

    private ContentMaintenanceBatchTaskCountsRow lockBatchTaskCounts(long batchId, int domain) {
        return lockBatchTaskCoordinationSnapshot(batchId, domain).counts();
    }

    private BatchTaskCoordinationSnapshot lockBatchTaskCoordinationSnapshot(long batchId, int domain) {
        List<ContentMaintenanceBatchTaskCoordinationTaskRow> rows =
                mapper.lockBatchTasksForCoordination(batchId, domain);
        if (rows == null || rows.isEmpty() || rows.size() > MAX_BATCH_TASKS) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        ContentMaintenanceBatchTaskCountsRow counts = new ContentMaintenanceBatchTaskCountsRow();
        counts.setOpenTaskCount(0);
        counts.setActiveTaskCount(0);
        counts.setReassignableTaskCount(0);
        for (ContentMaintenanceBatchTaskCoordinationTaskRow row : rows) {
            if (row == null || row.getId() == null || row.getId() <= 0
                    || row.getAssigneeUid() == null || row.getAssigneeUid() <= 0
                    || !STATUSES.contains(row.getStatus())) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            if ("OPEN".equals(row.getStatus())) {
                counts.setOpenTaskCount(counts.getOpenTaskCount() + 1);
            }
            if (Set.of("OPEN", "CLAIMED", "SUBMITTED").contains(row.getStatus())) {
                counts.setActiveTaskCount(counts.getActiveTaskCount() + 1);
            }
            if (Set.of("OPEN", "CLAIMED").contains(row.getStatus())) {
                counts.setReassignableTaskCount(counts.getReassignableTaskCount() + 1);
            }
        }
        return new BatchTaskCoordinationSnapshot(counts, List.copyOf(rows));
    }

    public PageResult<ContentMaintenanceTaskDTO> listMine(Long uid, String requestedStatus, long cursor, int size) {
        requireTable();
        requireId(uid);
        return page(mapper.listMine(uid, status(requestedStatus), safeCursor(cursor), pageSize(size) + 1),
                pageSize(size), row -> toDto(row, uid));
    }

    public PageResult<ContentMaintenanceTaskDTO> listQueue(Integer requestedDomain, String requestedStatus,
                                                            Long operatorUid, long cursor, int size) {
        requireTable();
        Integer domain = requestedDomain == null ? null : requireDomain(requestedDomain);
        List<Integer> moderatedDomains = moderatedDomains(operatorUid);
        if (domain != null && !moderatedDomains.contains(domain)) throw new BizException(ErrorCode.FORBIDDEN);
        int safeSize = pageSize(size);
        String normalizedStatus = status(requestedStatus);
        List<ContentMaintenanceTaskRow> rows;
        if (domain != null || moderatedDomains.size() == 5) {
            rows = mapper.listQueue(domain, normalizedStatus, safeCursor(cursor), safeSize + 1);
        } else {
            rows = moderatedDomains.stream()
                    .flatMap(item -> mapper.listQueue(item, normalizedStatus, safeCursor(cursor), safeSize + 1).stream())
                    .sorted((left, right) -> Long.compare(right.getId(), left.getId()))
                    .limit(safeSize + 1L)
                    .toList();
        }
        return page(rows, safeSize, row -> toDto(row, operatorUid));
    }

    public ContentMaintenanceTaskReviewContextDTO reviewContext(Long id, Long viewerUid) {
        requireTable();
        ContentMaintenanceTaskRow task = requireTask(mapper.selectById(requireId(id)));
        requireReviewContextAccess(task, viewerUid);

        if (!hasSubmittedDelivery(task)) {
            LinkedPostRead source = readLinkedPublicPost(task.getSourcePostId());
            return reviewContext(task, viewerUid, source.dto(), notLinkedPost(),
                    evidence(NOT_SUBMITTED, "任务尚未提交公开交付。", List.of()),
                    false, null);
        }
        if (!"POST".equals(task.getDeliveryType()) && !"QUESTION".equals(task.getDeliveryType())) {
            LinkedPostRead source = readLinkedPublicPost(task.getSourcePostId());
            return reviewContext(task, viewerUid, source.dto(), notLinkedPost(),
                    evidence(NON_POST_DELIVERY, "交付不是帖子或问题，本接口不提供帖子修订证据。", List.of()),
                    false, null);
        }

        LinkedPostRead source = readLinkedPublicPost(task.getSourcePostId());
        LinkedPostRead delivery = task.getDeliveryPostId() == null
                ? new LinkedPostRead(unavailablePost(null), true)
                : Objects.equals(task.getSourcePostId(), task.getDeliveryPostId())
                ? source
                : readLinkedPublicPost(task.getDeliveryPostId());
        if (source.unavailable()) {
            return unavailableContext(task, viewerUid, source.dto(), delivery.dto(),
                    SOURCE_UNAVAILABLE, "关联原内容当前不可作为公开复核依据。");
        }
        if (delivery.unavailable() || task.getDeliveryPostId() == null) {
            return unavailableContext(task, viewerUid, source.dto(), delivery.dto(),
                    DELIVERY_UNAVAILABLE, "交付内容当前不可作为公开复核依据。");
        }
        if (!Objects.equals(task.getSourcePostId(), task.getDeliveryPostId())) {
            return reviewContext(task, viewerUid, source.dto(), delivery.dto(),
                    evidence(SEPARATE_PUBLIC_DELIVERY, "交付为另一篇当前公开内容。", List.of()),
                    false, null);
        }

        try {
            List<ContentMaintenanceRevisionEvidenceDTO.PublicUpdateDTO> updates = safePublicUpdates(task);
            String state = updates.isEmpty() ? SAME_POST_NO_PUBLIC_UPDATE : SAME_POST_UPDATED;
            String summary = updates.isEmpty()
                    ? "未找到任务创建后的公开更新说明。"
                    : "任务创建后存在公开更新说明。";
            return reviewContext(task, viewerUid, source.dto(), delivery.dto(),
                    evidence(state, summary, updates), false, null);
        } catch (RuntimeException ignored) {
            return unavailableContext(task, viewerUid, source.dto(), delivery.dto(),
                    EVIDENCE_UNAVAILABLE, "复核依据暂不可读取。");
        }
    }

    public PageResult<ContentMaintenanceCandidateDTO> listCandidates(
            Long uid,
            Integer requestedDomain,
            String requestedSourceType,
            Integer contentType,
            long cursor,
            int size) {
        requireTable();
        requireId(uid);
        if (contentType != null && !Post.isSupportedType(contentType)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String normalizedSourceType = StringUtils.hasText(requestedSourceType)
                ? sourceType(requestedSourceType)
                : null;
        List<Integer> allowedDomains = maintenanceDomains(uid);
        Integer domain = requestedDomain == null ? null : requireDomain(requestedDomain);
        if (domain != null && !allowedDomains.contains(domain)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        int safeSize = pageSize(size);
        List<ContentMaintenanceTaskRow> rows;
        if (domain != null) {
            rows = mapper.listCandidates(uid, domain, normalizedSourceType, contentType,
                    safeCursor(cursor), safeSize + 1);
        } else {
            rows = allowedDomains.stream()
                    .flatMap(item -> mapper.listCandidates(uid, item, normalizedSourceType, contentType,
                            safeCursor(cursor), safeSize + 1).stream())
                    .sorted((left, right) -> Long.compare(right.getId(), left.getId()))
                    .limit(safeSize + 1L)
                    .toList();
        }
        return candidatePage(rows, safeSize, uid);
    }

    @Transactional
    public ContentMaintenanceTaskDTO claim(Long id, Long uid) {
        requireTable();
        ContentMaintenanceTaskRow before = requireTask(mapper.lockById(requireId(id)));
        if (!uid.equals(before.getAssigneeUid())) throw new BizException(ErrorCode.FORBIDDEN);
        requireMaintenanceRole(uid, before.getDomain());
        if (mapper.claim(before.getId(), uid) != 1 && !"CLAIMED".equals(before.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        ContentMaintenanceTaskRow after = requireTask(mapper.lockById(before.getId()));
        adminAuditService.recordRequired(uid, "CONTENT_MAINTENANCE_TASK_CLAIM",
                "CONTENT_MAINTENANCE_TASK", before.getId(), before, after, "maintenance task claimed");
        return toDto(after, uid);
    }

    @Transactional
    public ContentMaintenanceTaskDTO reassign(
            Long id,
            ContentMaintenanceTaskReassignCmd cmd,
            Long operatorUid) {
        requireTable();
        if (cmd == null) throw new BizException(ErrorCode.PARAM_ERROR);
        ContentMaintenanceTaskRow before = requireTask(mapper.lockById(requireId(id)));
        requireModerate(operatorUid, before.getDomain());
        if (!Set.of("OPEN", "CLAIMED").contains(before.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        Long replacementUid = requireId(cmd.getReplacementUid());
        if (replacementUid.equals(before.getAssigneeUid())) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        if (mapper.userExists(replacementUid) <= 0) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        requireMaintenanceRole(replacementUid, before.getDomain());
        String reason = required(cmd.getReason(), 500);
        if (mapper.reassign(before.getId(), replacementUid) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        ContentMaintenanceTaskRow after = requireTask(mapper.lockById(before.getId()));
        adminAuditService.recordRequired(operatorUid, "CONTENT_MAINTENANCE_TASK_REASSIGN",
                "CONTENT_MAINTENANCE_TASK", before.getId(), before, after, reason);
        return toDto(after, operatorUid);
    }

    @Transactional
    public ContentMaintenanceTaskDTO submit(Long id, ContentMaintenanceTaskSubmitCmd cmd, Long uid) {
        requireTable();
        if (cmd == null) throw new BizException(ErrorCode.PARAM_ERROR);
        ContentMaintenanceTaskRow before = requireTask(mapper.lockById(requireId(id)));
        if (!uid.equals(before.getAssigneeUid())) throw new BizException(ErrorCode.FORBIDDEN);
        requireMaintenanceRole(uid, before.getDomain());
        Delivery delivery = delivery(cmd, before.getDomain(), uid);
        int attemptNo = Math.max(0, before.getCurrentAttemptNo() == null
                ? 0 : before.getCurrentAttemptNo()) + 1;
        if (mapper.submit(before.getId(), uid, delivery.type(), delivery.id(), delivery.postId(),
                required(cmd.getNote(), 1000), attemptNo) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        String note = required(cmd.getNote(), 1000);
        if (mapper.insertAttempt(idGenerator.nextId(), before.getId(), attemptNo,
                delivery.type(), delivery.id(), delivery.postId(), note, uid) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        ContentMaintenanceTaskRow after = requireTask(mapper.lockById(before.getId()));
        adminAuditService.recordRequired(uid, "CONTENT_MAINTENANCE_TASK_SUBMIT",
                "CONTENT_MAINTENANCE_TASK", before.getId(), before, after, note);
        return toDto(after, uid);
    }

    @Transactional
    public ContentMaintenanceTaskDTO review(Long id, ContentMaintenanceTaskReviewCmd cmd, Long operatorUid) {
        requireTable();
        if (cmd == null) throw new BizException(ErrorCode.PARAM_ERROR);
        ContentMaintenanceTaskRow before = requireTask(mapper.lockById(requireId(id)));
        requireModerate(operatorUid, before.getDomain());
        String note = required(cmd.getNote(), 1000);
        String decision = enumValue(cmd.getDecision(), Set.of("APPROVED", "REJECTED"));
        String reasonCode = reviewReasonCode(decision, cmd.getReasonCode());
        ContentMaintenanceTaskAttemptRow attempt = ensureReviewAttempt(before);
        if (attempt == null || attempt.getId() == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (mapper.decideAttempt(attempt.getId(), before.getId(), decision,
                reasonCode, operatorUid, note) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        int updated = "APPROVED".equals(decision)
                ? mapper.approve(before.getId(), operatorUid, reasonCode, note)
                : mapper.reject(before.getId(), operatorUid, reasonCode, note);
        if (updated != 1) throw new BizException(ErrorCode.INVALID_STATUS);
        ContentMaintenanceTaskRow after = requireTask(mapper.lockById(before.getId()));
        adminAuditService.recordRequired(operatorUid, "CONTENT_MAINTENANCE_TASK_" + decision,
                "CONTENT_MAINTENANCE_TASK", before.getId(), before, after, reasonCode + ": " + note);
        return toDto(after, operatorUid);
    }

    @Transactional
    public ContentMaintenanceTaskDTO close(Long id, ContentMaintenanceTaskCloseCmd cmd, Long operatorUid) {
        requireTable();
        if (cmd == null) throw new BizException(ErrorCode.PARAM_ERROR);
        ContentMaintenanceTaskRow before = requireTask(mapper.lockById(requireId(id)));
        requireModerate(operatorUid, before.getDomain());
        String reasonCode = enumValue(cmd.getReasonCode(), CLOSE_REASON_CODES);
        String note = required(cmd.getNote(), 1000);
        if ("SUBMITTED".equals(before.getStatus())) {
            ContentMaintenanceTaskAttemptRow attempt = ensureReviewAttempt(before);
            if (attempt == null || attempt.getId() == null
                    || mapper.decideAttempt(attempt.getId(), before.getId(), "CLOSED",
                    reasonCode, operatorUid, note) != 1) {
                throw new BizException(ErrorCode.INVALID_STATUS);
            }
        }
        if (mapper.close(before.getId(), operatorUid, reasonCode, note) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        ContentMaintenanceTaskRow after = requireTask(mapper.lockById(before.getId()));
        adminAuditService.recordRequired(operatorUid, "CONTENT_MAINTENANCE_TASK_CLOSE",
                "CONTENT_MAINTENANCE_TASK", before.getId(), before, after, reasonCode + ": " + note);
        return toDto(after, operatorUid);
    }

    public List<ContentMaintenanceTaskAttemptDTO> attempts(Long id, Long viewerUid) {
        requireTable();
        ContentMaintenanceTaskRow task = requireTask(mapper.selectById(requireId(id)));
        requireReviewContextAccess(task, viewerUid);
        List<ContentMaintenanceTaskAttemptRow> rows = mapper.listAttempts(task.getId());
        if (rows == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return rows.stream()
                .map(ContentMaintenanceTaskService::toAttemptDto)
                .toList();
    }

    private ContentMaintenanceTaskAttemptRow ensureReviewAttempt(ContentMaintenanceTaskRow task) {
        int currentAttemptNo = task.getCurrentAttemptNo() == null
                ? 0 : task.getCurrentAttemptNo();
        if (currentAttemptNo <= 0) {
            if (!"SUBMITTED".equals(task.getStatus())) {
                return null;
            }
            int legacyAttemptNo = 1;
            ContentMaintenanceTaskAttemptRow existing =
                    mapper.lockAttempt(task.getId(), legacyAttemptNo);
            if (existing == null) {
                Long submittedByUid = task.getAssigneeUid() != null
                        ? task.getAssigneeUid() : task.getCreatedByUid();
                if (submittedByUid == null
                        || task.getSubmittedAt() == null
                        || !hasValidDeliverySnapshot(task)
                        || mapper.insertHistoricalAttempt(idGenerator.nextId(), task.getId(), legacyAttemptNo,
                        task.getDeliveryType(), task.getDeliveryRefId(), task.getDeliveryPostId(),
                        task.getDeliveryNote(), submittedByUid, task.getSubmittedAt()) != 1) {
                    throw new BizException(ErrorCode.DEPENDENCY_ERROR);
                }
            }
            int currentAttemptUpdated = mapper.setCurrentAttemptNo(task.getId(), legacyAttemptNo);
            existing = mapper.lockAttempt(task.getId(), legacyAttemptNo);
            if (currentAttemptUpdated != 1 && existing == null) {
                throw new BizException(ErrorCode.INVALID_STATUS);
            }
            if (existing == null) throw new BizException(ErrorCode.INVALID_STATUS);
            return existing;
        }
        return mapper.lockAttempt(task.getId(), currentAttemptNo);
    }

    private static ContentMaintenanceTaskAttemptDTO toAttemptDto(ContentMaintenanceTaskAttemptRow row) {
        if (row == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return ContentMaintenanceTaskAttemptDTO.builder()
                .id(row.getId())
                .taskId(row.getTaskId())
                .attemptNo(row.getAttemptNo())
                .deliveryType(row.getDeliveryType())
                .deliveryRefId(row.getDeliveryRefId())
                .deliveryPostId(row.getDeliveryPostId())
                .note(row.getNote())
                .submittedByUid(row.getSubmittedByUid())
                .submittedAt(row.getSubmittedAt())
                .decision(row.getDecision())
                .reasonCode(row.getReasonCode())
                .reviewedByUid(row.getReviewedByUid())
                .reviewedAt(row.getReviewedAt())
                .reviewNote(row.getReviewNote())
                .createTime(row.getCreateTime())
                .updateTime(row.getUpdateTime())
                .build();
    }

    private Delivery delivery(ContentMaintenanceTaskSubmitCmd cmd, Integer domain, Long uid) {
        String type = enumValue(cmd.getDeliveryType(), Set.of("POST", "QUESTION", "SERIES"));
        Long id = requireId(cmd.getDeliveryRefId());
        Long postId = positiveOrNull(cmd.getDeliveryPostId());
        if ("SERIES".equals(type)) {
            if (postId != null) throw new BizException(ErrorCode.PARAM_ERROR);
            SeriesRow series = collaborationMapper.selectSeries(id, uid);
            if (series == null
                    || !domain.equals(series.getDomain())
                    || series.getPostCount() == null
                    || series.getPostCount() <= 0
                    || collaborationMapper.seriesHasContributor(id, uid) <= 0) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
            return new Delivery(type, id, null);
        }
        if (postId == null) postId = id;
        if (!id.equals(postId)) throw new BizException(ErrorCode.PARAM_ERROR);
        PostDTO post = postFacade.getPostForAuthor(postId, uid);
        if (post == null || !isPublic(post) || !domain.equals(post.getDomain())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if ("QUESTION".equals(type) && !Integer.valueOf(Post.TYPE_COMMUNITY_QUESTION).equals(post.getPostType())) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return new Delivery(type, id, postId);
    }

    private void requirePublicPostInDomain(Long postId, Integer domain) {
        PostDTO post = postFacade.getPost(postId);
        if (post == null || !isPublic(post) || !domain.equals(post.getDomain())) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private void requireSourceAssociation(
            String sourceType,
            Long sourcePostId,
            Long sourceRefId,
            Integer domain,
            Long operatorUid) {
        if (!"CHANNEL_HEALTH".equals(sourceType)) {
            if (sourcePostId != null) {
                requirePublicPostInDomain(sourcePostId, domain);
            }
            return;
        }
        if ((sourcePostId == null) != (sourceRefId == null)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (sourcePostId == null) {
            return;
        }
        requirePublicPostInDomain(sourcePostId, domain);
        requireCurrentChannelHealthRevision(sourcePostId, sourceRefId, domain, operatorUid);
    }

    private void requireCurrentChannelHealthRevision(
            Long sourcePostId,
            Long sourceRefId,
            Integer domain,
            Long operatorUid) {
        PostContentRevisionQueryResult result;
        try {
            result = postContentRevisionQuery.query(PostContentRevisionQuery.authorizedChannel(
                    operatorUid, List.of(sourcePostId), EARLIEST_REVISION_WINDOW_START, List.of(domain)));
        } catch (RuntimeException ignored) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (result == null || !result.available() || result.items().size() != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        PostContentRevisionSnapshot snapshot = result.items().get(0);
        if (snapshot == null
                || !sourcePostId.equals(snapshot.postId())
                || snapshot.status() != PostContentRevisionSnapshot.Status.FOUND
                || !snapshot.hasEffectiveRevision()
                || snapshot.effectivePublishedPostVersion() == null
                || snapshot.effectivePublishedPostVersion() <= 0
                || !sourceRefId.equals(snapshot.effectivePublishedPostVersion().longValue())) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static boolean isPublic(PostDTO post) {
        return Integer.valueOf(Post.STATUS_PUBLISHED).equals(post.getPostStatus())
                && Integer.valueOf(Post.VIS_PUBLIC).equals(post.getVisibility());
    }

    private static boolean hasSubmittedDelivery(ContentMaintenanceTaskRow task) {
        return task != null
                && Set.of("SUBMITTED", "COMPLETED", "CLOSED").contains(task.getStatus())
                && task.getSubmittedAt() != null
                && hasValidDeliverySnapshot(task);
    }

    private static boolean hasValidDeliverySnapshot(ContentMaintenanceTaskRow task) {
        if (task == null
                || !StringUtils.hasText(task.getDeliveryType())
                || task.getDeliveryRefId() == null
                || task.getDeliveryRefId() <= 0
                || !StringUtils.hasText(task.getDeliveryNote())) {
            return false;
        }
        return switch (task.getDeliveryType()) {
            case "POST", "QUESTION" -> task.getDeliveryPostId() != null
                    && task.getDeliveryRefId().equals(task.getDeliveryPostId());
            case "SERIES" -> task.getDeliveryPostId() == null;
            default -> false;
        };
    }

    private void requireReviewContextAccess(ContentMaintenanceTaskRow task, Long viewerUid) {
        if (viewerUid == null
                || (!viewerUid.equals(task.getAssigneeUid())
                && !viewerUid.equals(task.getCreatedByUid())
                && !canModerate(viewerUid, task.getDomain()))) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private LinkedPostRead readLinkedPublicPost(Long postId) {
        if (postId == null) {
            return new LinkedPostRead(notLinkedPost(), false);
        }
        try {
            PostDTO post = postFacade.getPost(postId, null);
            if (post == null || !postId.equals(post.getId()) || !isPublic(post)) {
                return new LinkedPostRead(unavailablePost(postId), true);
            }
            return new LinkedPostRead(ContentMaintenanceLinkedPublicPostDTO.builder()
                    .postId(post.getId())
                    .domain(post.getDomain())
                    .postType(post.getPostType())
                    .title(post.getTitle())
                    .postHref("/post/" + post.getId())
                    .availability(AVAILABLE)
                    .build(), false);
        } catch (RuntimeException ignored) {
            return new LinkedPostRead(unavailablePost(postId), true);
        }
    }

    private static ContentMaintenanceLinkedPublicPostDTO notLinkedPost() {
        return ContentMaintenanceLinkedPublicPostDTO.builder()
                .availability(NOT_LINKED)
                .build();
    }

    private static ContentMaintenanceLinkedPublicPostDTO unavailablePost(Long postId) {
        return ContentMaintenanceLinkedPublicPostDTO.builder()
                .postId(postId)
                .availability(UNAVAILABLE)
                .build();
    }

    private List<ContentMaintenanceRevisionEvidenceDTO.PublicUpdateDTO> safePublicUpdates(
            ContentMaintenanceTaskRow task) {
        if (task.getCreateTime() == null) {
            throw new IllegalStateException("Task creation time is unavailable");
        }
        List<PublicPostUpdateDTO> updates =
                postFacade.listPublicUpdates(task.getDeliveryPostId(), PUBLIC_UPDATE_QUERY_LIMIT);
        if (updates == null || updates.isEmpty()) {
            return List.of();
        }
        return updates.stream()
                .filter(Objects::nonNull)
                .filter(update -> update.getCreateTime() != null)
                .filter(update -> !update.getCreateTime().isBefore(task.getCreateTime()))
                .sorted((left, right) -> right.getCreateTime().compareTo(left.getCreateTime()))
                .limit(PUBLIC_UPDATE_RESPONSE_LIMIT)
                .map(ContentMaintenanceTaskService::toPublicUpdate)
                .toList();
    }

    private static ContentMaintenanceRevisionEvidenceDTO.PublicUpdateDTO toPublicUpdate(
            PublicPostUpdateDTO update) {
        return ContentMaintenanceRevisionEvidenceDTO.PublicUpdateDTO.builder()
                .resultVersion(update.getResultVersion())
                .publicUpdateSummary(update.getPublicUpdateSummary())
                .impactScope(update.getImpactScope())
                .createTime(update.getCreateTime())
                .build();
    }

    private ContentMaintenanceTaskReviewContextDTO unavailableContext(
            ContentMaintenanceTaskRow task,
            Long viewerUid,
            ContentMaintenanceLinkedPublicPostDTO source,
            ContentMaintenanceLinkedPublicPostDTO delivery,
            String state,
            String summary) {
        return reviewContext(task, viewerUid, source, delivery,
                evidence(state, summary, List.of()), true, state);
    }

    private ContentMaintenanceTaskReviewContextDTO reviewContext(
            ContentMaintenanceTaskRow task,
            Long viewerUid,
            ContentMaintenanceLinkedPublicPostDTO source,
            ContentMaintenanceLinkedPublicPostDTO delivery,
            ContentMaintenanceRevisionEvidenceDTO evidence,
            boolean degraded,
            String fallbackReason) {
        return ContentMaintenanceTaskReviewContextDTO.builder()
                .task(toDto(task, viewerUid))
                .source(source)
                .delivery(delivery)
                .evidence(evidence)
                .degraded(degraded)
                .fallbackReason(fallbackReason)
                .build();
    }

    private static ContentMaintenanceRevisionEvidenceDTO evidence(
            String state,
            String summary,
            List<ContentMaintenanceRevisionEvidenceDTO.PublicUpdateDTO> updates) {
        return ContentMaintenanceRevisionEvidenceDTO.builder()
                .state(state)
                .summary(summary)
                .updates(updates == null ? List.of() : updates)
                .build();
    }

    private ContentMaintenanceTaskDTO toDto(ContentMaintenanceTaskRow row, Long viewerUid) {
        boolean moderator = viewerUid != null && canModerate(viewerUid, row.getDomain());
        boolean assignee = viewerUid != null && viewerUid.equals(row.getAssigneeUid());
        boolean activeMaintainer = assignee && hasMaintenanceRole(viewerUid, row.getDomain());
        return ContentMaintenanceTaskDTO.builder()
                .id(row.getId()).domain(row.getDomain()).sourceType(row.getSourceType())
                .sourceRefId(row.getSourceRefId()).sourcePostId(row.getSourcePostId())
                .createdByUid(row.getCreatedByUid()).assigneeUid(row.getAssigneeUid())
                .title(row.getTitle()).detail(row.getDetail()).status(row.getStatus())
                .deliveryType(row.getDeliveryType()).deliveryRefId(row.getDeliveryRefId())
                .deliveryPostId(row.getDeliveryPostId()).deliveryNote(row.getDeliveryNote())
                .reviewNote(row.getReviewNote())
                .dispatchBatchId(row.getDispatchBatchId())
                .priority(taskPriority(row))
                .dueAt(row.getDueAt())
                .currentAttemptNo(row.getCurrentAttemptNo() == null ? 0 : row.getCurrentAttemptNo())
                .maintenancePhase(maintenancePhase(row))
                .terminalOutcomeCode(row.getTerminalOutcomeCode())
                .closeReasonCode(row.getCloseReasonCode())
                .canClaim(activeMaintainer && "OPEN".equals(row.getStatus()))
                .canSubmit(activeMaintainer && "CLAIMED".equals(row.getStatus()))
                .canReview(moderator && "SUBMITTED".equals(row.getStatus()))
                .canClose(moderator && Set.of("OPEN", "CLAIMED", "SUBMITTED").contains(row.getStatus()))
                .canReassign(moderator && Set.of("OPEN", "CLAIMED").contains(row.getStatus()))
                .claimedAt(row.getClaimedAt()).submittedAt(row.getSubmittedAt())
                .reviewedByUid(row.getReviewedByUid()).reviewedAt(row.getReviewedAt())
                .closedByUid(row.getClosedByUid()).closedAt(row.getClosedAt())
                .createTime(row.getCreateTime()).updateTime(row.getUpdateTime())
                .build();
    }

    private static String maintenancePhase(ContentMaintenanceTaskRow row) {
        if (row == null || row.getStatus() == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return switch (row.getStatus()) {
            case "OPEN" -> "OPEN";
            case "CLAIMED" -> "REJECTED".equals(row.getLatestAttemptDecision())
                    ? "REWORK" : "IN_PROGRESS";
            case "SUBMITTED" -> "REVIEW_PENDING";
            case "COMPLETED" -> "VERIFIED_DELIVERY";
            case "CLOSED" -> "CLOSED";
            default -> throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        };
    }

    private static String taskPriority(ContentMaintenanceTaskRow row) {
        if (row.getPriority() == null) {
            return "MEDIUM";
        }
        if (!PRIORITIES.contains(row.getPriority())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return row.getPriority();
    }

    private boolean canModerate(Long uid, Integer domain) {
        return uid != null && domain != null && domainModeratorService.canModerateDomain(uid, domain);
    }

    private void requireModerate(Long uid, Integer domain) {
        if (uid == null || !canModerate(uid, domain)) throw new BizException(ErrorCode.FORBIDDEN);
    }

    private List<Integer> moderatedDomains(Long uid) {
        if (uid == null) throw new BizException(ErrorCode.FORBIDDEN);
        List<Integer> domains = List.of(1, 2, 3, 4, 5).stream()
                .filter(domain -> canModerate(uid, domain))
                .toList();
        if (domains.isEmpty()) throw new BizException(ErrorCode.FORBIDDEN);
        return domains;
    }

    private List<Integer> maintenanceDomains(Long uid) {
        List<Integer> domains = List.of(1, 2, 3, 4, 5).stream()
                .filter(domain -> hasMaintenanceRole(uid, domain))
                .toList();
        if (domains.isEmpty()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return domains;
    }

    private boolean hasMaintenanceRole(Long uid, Integer domain) {
        return uid != null
                && domain != null
                && communityRoleAccessService.hasActiveGrant(
                uid, "CHANNEL_RESOURCE_MAINTAINER", domainCode(domain));
    }

    private void requireMaintenanceRole(Long uid, Integer domain) {
        if (!hasMaintenanceRole(uid, domain)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireTable() {
        try {
            if (mapper.tableExists() > 0 && mapper.attemptTableExists() > 0) return;
        } catch (RuntimeException ignored) {
            // Convert metadata failures to the public dependency contract.
        }
        throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                "Content maintenance migration is required: " + MIGRATION);
    }

    private static ContentMaintenanceTaskRow requireTask(ContentMaintenanceTaskRow row) {
        if (row == null) throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        return row;
    }

    private static String sourceType(String value) {
        return enumValue(value, SOURCE_TYPES);
    }

    private static String priority(String value) {
        return enumValue(value, PRIORITIES);
    }

    private static String reviewReasonCode(String decision, String reasonCode) {
        return enumValue(reasonCode, "APPROVED".equals(decision)
                ? APPROVED_REASON_CODES : REJECTED_REASON_CODES);
    }

    private static String status(String value) {
        return StringUtils.hasText(value) ? enumValue(value, STATUSES) : null;
    }

    private static String enumValue(String value, Set<String> allowed) {
        String normalized = required(value, 32).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new BizException(ErrorCode.PARAM_ERROR);
        return normalized;
    }

    private static int requireDomain(Integer domain) {
        if (domain == null || domain < 1 || domain > 5) throw new BizException(ErrorCode.PARAM_ERROR);
        return domain;
    }

    private static Long requireId(Long value) {
        if (value == null || value <= 0) throw new BizException(ErrorCode.PARAM_ERROR);
        return value;
    }

    private static Long positiveOrNull(Long value) {
        return value == null ? null : requireId(value);
    }

    private static String required(String value, int max) {
        if (!StringUtils.hasText(value)) throw new BizException(ErrorCode.PARAM_ERROR);
        String normalized = value.trim();
        if (normalized.length() > max) throw new BizException(ErrorCode.PARAM_ERROR);
        return normalized;
    }

    private static int pageSize(int value) {
        return Math.max(1, Math.min(value <= 0 ? 20 : value, MAX_PAGE_SIZE));
    }

    private static long safeCursor(long cursor) {
        return Math.max(0, cursor);
    }

    private static PageResult<ContentMaintenanceTaskDTO> page(List<ContentMaintenanceTaskRow> rows, int size,
                                                               Function<ContentMaintenanceTaskRow, ContentMaintenanceTaskDTO> converter) {
        List<ContentMaintenanceTaskRow> safe = rows == null ? List.of() : rows;
        boolean hasMore = safe.size() > size;
        List<ContentMaintenanceTaskRow> visible = safe.stream().limit(size).toList();
        String next = hasMore && !visible.isEmpty() ? String.valueOf(visible.get(visible.size() - 1).getId()) : null;
        return PageResult.of(visible.stream().map(converter).toList(), next, hasMore);
    }

    private static PageResult<ContentMaintenanceCandidateDTO> candidatePage(
            List<ContentMaintenanceTaskRow> rows,
            int size,
            Long uid) {
        List<ContentMaintenanceTaskRow> safe = rows == null ? List.of() : rows;
        boolean hasMore = safe.size() > size;
        List<ContentMaintenanceTaskRow> visible = safe.stream().limit(size).toList();
        String next = hasMore && !visible.isEmpty()
                ? String.valueOf(visible.get(visible.size() - 1).getId())
                : null;
        List<ContentMaintenanceCandidateDTO> items = visible.stream()
                .map(row -> {
                    boolean assignedToViewer = uid.equals(row.getAssigneeUid());
                    return ContentMaintenanceCandidateDTO.builder()
                            .id(row.getId())
                            .domain(row.getDomain())
                            .sourceType(row.getSourceType())
                            .sourceRefId(row.getSourceRefId())
                            .sourcePostId(row.getSourcePostId())
                            .sourcePostType(row.getSourcePostType())
                            .title(row.getTitle())
                            .status(row.getStatus())
                            .assignmentStatus(assignedToViewer ? "ASSIGNED_TO_ME" : "UNASSIGNED")
                            .canClaim(assignedToViewer)
                            .createTime(row.getCreateTime())
                            .updateTime(row.getUpdateTime())
                            .build();
                })
                .toList();
        return PageResult.of(items, next, hasMore);
    }

    private static String domainCode(Integer domain) {
        return switch (domain == null ? 0 : domain) {
            case 1 -> "TECH";
            case 2 -> "CAREER";
            case 3 -> "READING";
            case 4 -> "LIFESTYLE";
            case 5 -> "INVESTMENT";
            default -> throw new BizException(ErrorCode.PARAM_ERROR);
        };
    }

    private record Delivery(String type, Long id, Long postId) {
    }

    private record LinkedPostRead(ContentMaintenanceLinkedPublicPostDTO dto, boolean unavailable) {
    }

    private record BatchTaskCoordinationSnapshot(
            ContentMaintenanceBatchTaskCountsRow counts,
            List<ContentMaintenanceBatchTaskCoordinationTaskRow> rows) {
    }
}
