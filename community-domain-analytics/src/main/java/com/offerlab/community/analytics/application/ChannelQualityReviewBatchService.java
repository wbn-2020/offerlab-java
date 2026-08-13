package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchCandidateCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchCreateCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchDetailDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchPageDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchTaskDTO;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchMapper;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchTaskRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchTaskStatusRow;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.api.ContentMaintenanceTaskBatchDispatchCmd;
import com.offerlab.community.post.api.ContentMaintenanceTaskCommandFacade;
import com.offerlab.community.post.api.ContentMaintenanceTaskRevisionKey;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChannelQualityReviewBatchService {

    private static final String SOURCE_TYPE = "CHANNEL_HEALTH";
    private static final String MIGRATION =
            "db/migration/20260805_channel_quality_review_dispatch_batch.sql";
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_CANDIDATES = 20;
    private static final int MAX_PAGE_SIZE = MAX_CANDIDATES;
    private static final int TASK_DETAIL_LIMIT = MAX_CANDIDATES;
    private static final String TASK_DETAIL =
            "由频道质量复核批次派发，请基于当前公开内容完成维护并提交交付。";
    private static final Set<String> PRIORITIES = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Set<Integer> DUE_IN_DAYS = Set.of(1, 3, 7, 14, 30);
    private static final Set<String> TASK_STATUSES = Set.of(
            "OPEN", "CLAIMED", "SUBMITTED", "COMPLETED", "CLOSED");
    private static final Set<String> TERMINAL_OUTCOMES = Set.of("VERIFIED_DELIVERY");
    private static final Set<String> ATTEMPT_DECISIONS = Set.of("APPROVED", "REJECTED", "CLOSED");

    private final ChannelQualityReviewBatchMapper mapper;
    private final ChannelQualityReviewCandidateService candidateService;
    private final ContentMaintenanceTaskCommandFacade taskCommandFacade;
    private final SnowflakeIdGenerator idGenerator;
    private final AdminAuditService adminAuditService;
    private final AdminPermissionService adminPermissionService;
    private final DomainModeratorService domainModeratorService;
    private final Clock analyticsClock;

    @Transactional
    public ChannelQualityReviewBatchDetailDTO create(ChannelQualityReviewBatchCreateCmd cmd, Long operatorUid) {
        requireTables();
        requireOperator(operatorUid);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int domain = requireDomain(cmd.getDomain());
        requireModerate(operatorUid, domain);
        String name = requiredName(cmd.getName());
        long assigneeUid = requireId(cmd.getAssigneeUid());
        String priority = enumValue(cmd.getPriority(), PRIORITIES);
        int dueInDays = requireDueInDays(cmd.getDueInDays());
        List<ContentMaintenanceTaskRevisionKey> sourceKeys = sourceKeys(cmd.getCandidates());

        candidateService.acquireDispatchGates(sourceKeys);
        List<ChannelQualityReviewCandidateService.DispatchableCandidate> candidates =
                candidateService.resolveReadyForDispatch(domain, sourceKeys, operatorUid);
        if (!validResolvedCandidates(candidates, sourceKeys)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }

        LocalDateTime dueAt = LocalDateTime.ofInstant(analyticsClock.instant(), ZoneOffset.UTC)
                .plusDays(dueInDays);
        long batchId = idGenerator.nextId();
        if (mapper.insert(
                batchId,
                domain,
                SOURCE_TYPE,
                name,
                assigneeUid,
                priority,
                dueAt,
                operatorUid,
                candidates.size()) != 1) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }

        List<ContentMaintenanceTaskDTO> dispatchedTasks = new ArrayList<>(candidates.size());
        for (ChannelQualityReviewCandidateService.DispatchableCandidate candidate : candidates) {
            ContentMaintenanceTaskBatchDispatchCmd dispatchCmd = new ContentMaintenanceTaskBatchDispatchCmd();
            dispatchCmd.setBatchId(batchId);
            dispatchCmd.setDomain(domain);
            dispatchCmd.setSourcePostId(candidate.sourcePostId());
            dispatchCmd.setSourceRefId(candidate.sourceRefId());
            dispatchCmd.setAssigneeUid(assigneeUid);
            dispatchCmd.setTitle(candidate.title());
            dispatchCmd.setDetail(TASK_DETAIL);
            dispatchCmd.setPriority(priority);
            dispatchCmd.setDueAt(dueAt);
            ContentMaintenanceTaskDTO task =
                    taskCommandFacade.dispatchChannelHealthTask(dispatchCmd, operatorUid);
            if (!validDispatchedTask(task, batchId, domain, candidate)) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            dispatchedTasks.add(task);
        }

        ChannelQualityReviewBatchDetailDTO created = readDetail(batchId, domain);
        adminAuditService.recordRequired(
                operatorUid,
                "CHANNEL_QUALITY_REVIEW_BATCH_CREATE",
                "CHANNEL_QUALITY_REVIEW_BATCH",
                batchId,
                Map.of(),
                batchAuditView(created, dispatchedTasks),
                "channel quality review batch created");
        return created;
    }

    @Transactional(readOnly = true)
    public ChannelQualityReviewBatchPageDTO list(Integer domain, Long cursor, Integer size, Long operatorUid) {
        requireTables();
        requireOperator(operatorUid);
        int requestedDomain = requireDomain(domain);
        requireModerate(operatorUid, requestedDomain);
        int requestedSize = requirePageSize(size);
        long requestedCursor = requireCursor(cursor);

        List<ChannelQualityReviewBatchRow> rows;
        try {
            rows = mapper.listByDomain(requestedDomain, requestedCursor, requestedSize + 1);
        } catch (RuntimeException ignored) {
            return ChannelQualityReviewBatchPageDTO.unavailable();
        }
        if (rows == null || rows.size() > requestedSize + 1) {
            return ChannelQualityReviewBatchPageDTO.unavailable();
        }
        List<ChannelQualityReviewBatchRow> visible = rows.stream().limit(requestedSize).toList();
        if (!validOrderedRows(visible, requestedDomain, requestedCursor)) {
            return ChannelQualityReviewBatchPageDTO.unavailable();
        }
        Map<Long, Map<String, Integer>> taskStatusCounts = taskStatusCounts(visible);
        if (taskStatusCounts == null) {
            return ChannelQualityReviewBatchPageDTO.unavailable();
        }
        Instant now = analyticsClock.instant();
        List<ChannelQualityReviewBatchDTO> items = new ArrayList<>(visible.size());
        for (ChannelQualityReviewBatchRow row : visible) {
            Map<String, Integer> counts = taskStatusCounts.get(row.getId());
            if (!validTaskCounts(row, counts)) {
                return ChannelQualityReviewBatchPageDTO.unavailable();
            }
            items.add(toSummary(row, counts, now));
        }
        Long nextCursor = rows.size() > requestedSize && !visible.isEmpty()
                ? visible.get(visible.size() - 1).getId()
                : null;
        return ChannelQualityReviewBatchPageDTO.available(nextCursor, items);
    }

    @Transactional(readOnly = true)
    public ChannelQualityReviewBatchDetailDTO get(Long batchId, Long operatorUid) {
        requireTables();
        requireOperator(operatorUid);
        long id = requireId(batchId);
        ChannelQualityReviewBatchRow row = requireBatch(mapper.selectById(id));
        requireModerate(operatorUid, row.getDomain());
        return readDetail(id, row.getDomain());
    }

    private ChannelQualityReviewBatchDetailDTO readDetail(long batchId, int expectedDomain) {
        ChannelQualityReviewBatchRow row = requireBatch(mapper.selectById(batchId));
        if (!Integer.valueOf(expectedDomain).equals(row.getDomain())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        Map<Long, Map<String, Integer>> countByBatch = taskStatusCounts(List.of(row));
        Map<String, Integer> counts = countByBatch == null ? null : countByBatch.get(batchId);
        if (!validTaskCounts(row, counts)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        List<ChannelQualityReviewBatchTaskRow> taskRows = mapper.listTasksByBatchId(batchId, TASK_DETAIL_LIMIT + 1);
        if (!validTaskRows(taskRows, row, counts)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        Instant now = analyticsClock.instant();
        return ChannelQualityReviewBatchDetailDTO.builder()
                .id(row.getId())
                .domain(row.getDomain())
                .sourceType(row.getSourceType())
                .name(row.getName())
                .assigneeUid(row.getAssigneeUid())
                .createdByUid(row.getCreatedByUid())
                .priority(row.getPriority())
                .dueAt(toInstant(row.getDueAt()))
                .candidateCount(row.getCandidateCount())
                .progressState(progressState(counts))
                .dueState(dueState(row.getDueAt(), counts, now))
                .taskStatusCounts(counts)
                .createTime(toInstant(row.getCreateTime()))
                .tasks(taskRows.stream().map(this::toTask).toList())
                .build();
    }

    private Map<Long, Map<String, Integer>> taskStatusCounts(List<ChannelQualityReviewBatchRow> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> batchIds = rows.stream().map(ChannelQualityReviewBatchRow::getId).toList();
        List<ChannelQualityReviewBatchTaskStatusRow> statusRows;
        try {
            statusRows = mapper.listTaskStatusCounts(batchIds);
        } catch (RuntimeException ignored) {
            return null;
        }
        if (statusRows == null) {
            return null;
        }
        Map<Long, Map<String, Integer>> result = new LinkedHashMap<>();
        for (Long batchId : batchIds) {
            result.put(batchId, emptyTaskCounts());
        }
        for (ChannelQualityReviewBatchTaskStatusRow statusRow : statusRows) {
            if (statusRow == null
                    || statusRow.getBatchId() == null
                    || !result.containsKey(statusRow.getBatchId())
                    || !TASK_STATUSES.contains(statusRow.getStatus())
                    || statusRow.getTaskCount() == null
                    || statusRow.getTaskCount() <= 0
                    || statusRow.getTaskCount() > TASK_DETAIL_LIMIT) {
                return null;
            }
            int taskCount = Math.toIntExact(statusRow.getTaskCount());
            Map<String, Integer> counts = result.get(statusRow.getBatchId());
            if (counts.put(statusRow.getStatus(), taskCount) != 0) {
                return null;
            }
        }
        return result;
    }

    private static boolean validOrderedRows(
            List<ChannelQualityReviewBatchRow> rows, int expectedDomain, long cursor) {
        long previousId = cursor == 0 ? Long.MAX_VALUE : cursor;
        for (ChannelQualityReviewBatchRow row : rows) {
            if (!validBatchRow(row)
                    || !Integer.valueOf(expectedDomain).equals(row.getDomain())
                    || row.getId() >= previousId) {
                return false;
            }
            previousId = row.getId();
        }
        return true;
    }

    private static boolean validBatchRow(ChannelQualityReviewBatchRow row) {
        return row != null
                && row.getId() != null
                && row.getId() > 0
                && row.getDomain() != null
                && row.getDomain() >= 1
                && row.getDomain() <= 5
                && SOURCE_TYPE.equals(row.getSourceType())
                && StringUtils.hasText(row.getName())
                && row.getName().trim().length() >= 2
                && row.getName().trim().length() <= 120
                && row.getAssigneeUid() != null
                && row.getAssigneeUid() > 0
                && row.getCreatedByUid() != null
                && row.getCreatedByUid() > 0
                && PRIORITIES.contains(row.getPriority())
                && row.getCandidateCount() != null
                && row.getCandidateCount() >= 1
                && row.getCandidateCount() <= TASK_DETAIL_LIMIT
                && row.getCreateTime() != null;
    }

    private static boolean validTaskCounts(ChannelQualityReviewBatchRow row, Map<String, Integer> counts) {
        if (!validBatchRow(row) || counts == null || counts.size() != TASK_STATUSES.size()) {
            return false;
        }
        int total = 0;
        for (String status : TASK_STATUSES) {
            Integer count = counts.get(status);
            if (count == null || count < 0 || count > TASK_DETAIL_LIMIT) {
                return false;
            }
            total += count;
        }
        return total == row.getCandidateCount();
    }

    private static Map<String, Integer> emptyTaskCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String status : List.of("OPEN", "CLAIMED", "SUBMITTED", "COMPLETED", "CLOSED")) {
            counts.put(status, 0);
        }
        return counts;
    }

    private static String progressState(Map<String, Integer> counts) {
        if (counts.get("OPEN") > 0) {
            return "ACTION_REQUIRED";
        }
        if (counts.get("CLAIMED") > 0) {
            return "IN_PROGRESS";
        }
        if (counts.get("SUBMITTED") > 0) {
            return "REVIEW_PENDING";
        }
        if (counts.get("COMPLETED") > 0 && counts.get("CLOSED") == 0) {
            return "COMPLETED";
        }
        if (counts.get("CLOSED") > 0 && counts.get("COMPLETED") == 0) {
            return "CLOSED";
        }
        return "PARTIALLY_CLOSED";
    }

    private static String dueState(LocalDateTime dueAt, Map<String, Integer> counts, Instant now) {
        boolean active = counts.get("OPEN") > 0
                || counts.get("CLAIMED") > 0
                || counts.get("SUBMITTED") > 0;
        if (dueAt == null || !active) {
            return "NOT_APPLICABLE";
        }
        Instant deadline = toInstant(dueAt);
        if (now.isAfter(deadline)) {
            return "OVERDUE";
        }
        return Duration.between(now, deadline).compareTo(Duration.ofHours(48)) <= 0
                ? "DUE_SOON"
                : "ON_TRACK";
    }

    private static boolean validTaskRows(
            List<ChannelQualityReviewBatchTaskRow> rows,
            ChannelQualityReviewBatchRow batch,
            Map<String, Integer> expectedCounts) {
        if (rows == null || rows.size() != batch.getCandidateCount() || rows.size() > TASK_DETAIL_LIMIT) {
            return false;
        }
        Set<Long> taskIds = new LinkedHashSet<>();
        Set<ContentMaintenanceTaskRevisionKey> sourceKeys = new LinkedHashSet<>();
        Map<String, Integer> actualCounts = emptyTaskCounts();
        for (ChannelQualityReviewBatchTaskRow row : rows) {
            if (row == null
                    || row.getTaskId() == null
                    || row.getTaskId() <= 0
                    || !batch.getId().equals(row.getBatchId())
                    || row.getSourcePostId() == null
                    || row.getSourceRefId() == null
                    || !StringUtils.hasText(row.getTitle())
                    || !TASK_STATUSES.contains(row.getStatus())
                    || !validTerminalOutcome(row.getStatus(), row.getTerminalOutcomeCode())
                    || !validAttemptDecision(row.getLatestAttemptDecision())
                    || !taskIds.add(row.getTaskId())) {
                return false;
            }
            actualCounts.put(row.getStatus(), actualCounts.get(row.getStatus()) + 1);
            try {
                if (!sourceKeys.add(new ContentMaintenanceTaskRevisionKey(
                        row.getSourcePostId(), row.getSourceRefId()))) {
                    return false;
                }
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
        return actualCounts.equals(expectedCounts);
    }

    private ChannelQualityReviewBatchTaskDTO toTask(ChannelQualityReviewBatchTaskRow row) {
        return ChannelQualityReviewBatchTaskDTO.builder()
                .taskId(row.getTaskId())
                .sourcePostId(row.getSourcePostId())
                .sourceRefId(row.getSourceRefId())
                .title(row.getTitle())
                .postHref("/post/" + row.getSourcePostId())
                .status(row.getStatus())
                .maintenancePhase(taskPhase(row))
                .terminalOutcome(terminalOutcome(row.getStatus(), row.getTerminalOutcomeCode()))
                .build();
    }

    private static boolean validTerminalOutcome(String status, String outcome) {
        return outcome == null || (TERMINAL_OUTCOMES.contains(outcome) && "COMPLETED".equals(status));
    }

    private static boolean validAttemptDecision(String decision) {
        return decision == null || ATTEMPT_DECISIONS.contains(decision);
    }

    private static String taskPhase(ChannelQualityReviewBatchTaskRow row) {
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

    private static String terminalOutcome(String status, String outcome) {
        return outcome == null && "COMPLETED".equals(status) ? "VERIFIED_DELIVERY" : outcome;
    }

    private static ChannelQualityReviewBatchDTO toSummary(
            ChannelQualityReviewBatchRow row, Map<String, Integer> counts, Instant now) {
        return ChannelQualityReviewBatchDTO.builder()
                .id(row.getId())
                .domain(row.getDomain())
                .sourceType(row.getSourceType())
                .name(row.getName())
                .assigneeUid(row.getAssigneeUid())
                .createdByUid(row.getCreatedByUid())
                .priority(row.getPriority())
                .dueAt(toInstant(row.getDueAt()))
                .candidateCount(row.getCandidateCount())
                .progressState(progressState(counts))
                .dueState(dueState(row.getDueAt(), counts, now))
                .taskStatusCounts(Map.copyOf(counts))
                .createTime(toInstant(row.getCreateTime()))
                .build();
    }

    private static boolean validDispatchedTask(
            ContentMaintenanceTaskDTO task,
            long batchId,
            int domain,
            ChannelQualityReviewCandidateService.DispatchableCandidate candidate) {
        return task != null
                && task.getId() != null
                && task.getId() > 0
                && Long.valueOf(batchId).equals(task.getDispatchBatchId())
                && Integer.valueOf(domain).equals(task.getDomain())
                && SOURCE_TYPE.equals(task.getSourceType())
                && candidate.sourcePostId().equals(task.getSourcePostId())
                && candidate.sourceRefId().equals(task.getSourceRefId())
                && "OPEN".equals(task.getStatus());
    }

    private static boolean validResolvedCandidates(
            List<ChannelQualityReviewCandidateService.DispatchableCandidate> candidates,
            List<ContentMaintenanceTaskRevisionKey> sourceKeys) {
        if (candidates == null || candidates.size() != sourceKeys.size()) {
            return false;
        }
        for (int index = 0; index < candidates.size(); index++) {
            ChannelQualityReviewCandidateService.DispatchableCandidate candidate = candidates.get(index);
            ContentMaintenanceTaskRevisionKey expected = sourceKeys.get(index);
            if (candidate == null
                    || !expected.sourcePostId().equals(candidate.sourcePostId())
                    || !expected.sourceRefId().equals(candidate.sourceRefId())
                    || candidate.sourcePostType() == null
                    || !StringUtils.hasText(candidate.title())
                    || candidate.title().trim().length() > 160) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Object> batchAuditView(
            ChannelQualityReviewBatchDetailDTO detail,
            List<ContentMaintenanceTaskDTO> dispatchedTasks) {
        List<Map<String, Object>> tasks = dispatchedTasks.stream()
                .map(task -> Map.<String, Object>of(
                        "taskId", task.getId(),
                        "sourcePostId", task.getSourcePostId(),
                        "sourceRefId", task.getSourceRefId(),
                        "status", task.getStatus()))
                .toList();
        return Map.of(
                "batchId", detail.getId(),
                "domain", detail.getDomain(),
                "priority", detail.getPriority(),
                "dueAt", String.valueOf(detail.getDueAt()),
                "candidateCount", detail.getCandidateCount(),
                "tasks", tasks);
    }

    private ChannelQualityReviewBatchRow requireBatch(ChannelQualityReviewBatchRow row) {
        if (row == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!validBatchRow(row)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return row;
    }

    private void requireTables() {
        try {
            if (mapper.batchTableExists() > 0
                    && mapper.taskTableExists() > 0
                    && mapper.attemptTableExists() > 0) {
                return;
            }
        } catch (RuntimeException ignored) {
            // Preserve the same fail-closed contract for metadata failures.
        }
        throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                "Channel quality review batch migration is required: " + MIGRATION);
    }

    private void requireModerate(Long uid, int domain) {
        if (isGlobalModerator(uid)) {
            return;
        }
        if (!domainModeratorService.canModerateDomain(uid, domain)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean isGlobalModerator(Long uid) {
        return adminPermissionService.isAdmin(uid)
                || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR)
                || adminPermissionService.isLocalOpenMode();
    }

    private static List<ContentMaintenanceTaskRevisionKey> sourceKeys(
            List<ChannelQualityReviewBatchCandidateCmd> candidates) {
        if (candidates == null || candidates.isEmpty() || candidates.size() > MAX_CANDIDATES) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Set<ContentMaintenanceTaskRevisionKey> unique = new LinkedHashSet<>();
        for (ChannelQualityReviewBatchCandidateCmd candidate : candidates) {
            if (candidate == null) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
            ContentMaintenanceTaskRevisionKey key;
            try {
                key = new ContentMaintenanceTaskRevisionKey(
                        candidate.getSourcePostId(), candidate.getSourceRefId());
            } catch (IllegalArgumentException ex) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
            if (!unique.add(key)) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
        }
        return List.copyOf(unique);
    }

    private static String requiredName(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String normalized = value.trim();
        if (normalized.length() < 2 || normalized.length() > 120) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static int requireDueInDays(Integer value) {
        if (value == null || !DUE_IN_DAYS.contains(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static String enumValue(String value, Set<String> allowed) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static int requirePageSize(Integer size) {
        int value = size == null ? DEFAULT_PAGE_SIZE : size;
        if (value < 1 || value > MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static long requireCursor(Long cursor) {
        if (cursor == null) {
            return 0L;
        }
        if (cursor < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return cursor;
    }

    private static int requireDomain(Integer domain) {
        if (domain == null || domain < 1 || domain > 5) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return domain;
    }

    private static long requireId(Long value) {
        if (value == null || value <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static void requireOperator(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
