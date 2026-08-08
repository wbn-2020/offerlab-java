package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceTodoItemDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceTodoPageDTO;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceTodoMapper;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceTodoRow;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.application.DomainModeratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChannelQualityGovernanceTodoService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 20;
    private static final String CURSOR_VERSION = "v42mt1";
    private static final Set<String> STATUSES = Set.of("OPEN", "COMPLETED", "CLOSED");
    private static final Set<String> TASK_TYPES = Set.of(
            "ACKNOWLEDGE_CASE", "RECORD_PLAN", "COMPLETE_RETROSPECTIVE");
    private static final Set<String> DUE_STATES = Set.of("ALL", "ON_TRACK", "DUE_SOON", "OVERDUE");
    private static final Set<String> ESCALATION_LEVELS = Set.of(
            "NONE", "CHANNEL_ATTENTION", "GOVERNANCE_ATTENTION");

    private final ChannelQualityGovernanceTodoMapper todoMapper;
    private final DomainModeratorService domainModeratorService;
    private final AdminPermissionService adminPermissionService;
    private final Clock analyticsClock;

    @Transactional(readOnly = true)
    public ChannelQualityGovernanceTodoPageDTO listMine(
            String rawStatus,
            String rawTaskType,
            String rawDueState,
            String rawCursor,
            Integer rawSize,
            Long operatorUid) {
        requireOperator(operatorUid);
        requireV42Tables();
        Query query = query(rawStatus, rawTaskType, rawDueState, null, rawCursor, rawSize);
        List<ChannelQualityGovernanceTodoRow> candidates = todoMapper.listPersonalCursorCandidates(
                operatorUid, query.status(), query.taskType(), query.dueState(), query.evaluationTime(),
                query.dueSoonAt(), query.cursorOpenRank(), query.cursorOverdueRank(), query.cursorDueAt(),
                query.cursorId(), query.limit());
        return page(candidates, query, row -> canModerate(operatorUid, row.getDomain()));
    }

    @Transactional(readOnly = true)
    public ChannelQualityGovernanceTodoPageDTO listByDomain(
            Integer rawDomain,
            String rawStatus,
            String rawTaskType,
            String rawDueState,
            String rawEscalationLevel,
            String rawCursor,
            Integer rawSize,
            Long operatorUid) {
        requireOperator(operatorUid);
        int domain = requireDomain(rawDomain);
        requireModerate(operatorUid, domain);
        requireV42Tables();
        Query query = query(rawStatus, rawTaskType, rawDueState, rawEscalationLevel, rawCursor, rawSize);
        List<ChannelQualityGovernanceTodoRow> candidates = todoMapper.listDomainCursorCandidates(
                domain, query.status(), query.taskType(), query.dueState(), query.escalationLevel(),
                query.evaluationTime(), query.dueSoonAt(), query.cursorOpenRank(), query.cursorOverdueRank(),
                query.cursorDueAt(), query.cursorId(), query.limit());
        return page(candidates, query, row -> true);
    }

    private ChannelQualityGovernanceTodoPageDTO page(
            List<ChannelQualityGovernanceTodoRow> candidates,
            Query query,
            Visibility visibility) {
        List<ChannelQualityGovernanceTodoRow> rawPage = candidates.size() <= query.pageSize()
                ? candidates
                : candidates.subList(0, query.pageSize());
        List<ChannelQualityGovernanceTodoItemDTO> items = new ArrayList<>();
        for (ChannelQualityGovernanceTodoRow row : rawPage) {
            requireRow(row);
            if (visibility.visible(row)) {
                items.add(toItem(row, query.evaluationTime()));
            }
        }
        String nextCursor = candidates.size() > query.pageSize()
                ? encodeCursor(rawPage.get(rawPage.size() - 1), query.evaluationTime()) : null;
        return ChannelQualityGovernanceTodoPageDTO.builder()
                .dependencyStatus("READY")
                .evaluationTime(query.evaluationTime().atZone(ZoneOffset.UTC).toInstant())
                .freshThrough(null)
                .nextCursor(nextCursor)
                .items(items)
                .build();
    }

    private ChannelQualityGovernanceTodoItemDTO toItem(
            ChannelQualityGovernanceTodoRow row, LocalDateTime evaluationTime) {
        boolean actionable = "OPEN".equals(row.getStatus()) && canModerate(row.getAssigneeUid(), row.getDomain());
        String actionability = !"OPEN".equals(row.getStatus())
                ? "SOURCE_TERMINAL"
                : actionable ? "ACTIONABLE" : "ASSIGNEE_INELIGIBLE";
        String dueState = "OPEN".equals(row.getStatus()) ? dueState(row, evaluationTime) : null;
        return ChannelQualityGovernanceTodoItemDTO.builder()
                .todoId(row.getId())
                .caseId(row.getCaseId())
                .retrospectiveId(row.getRetrospectiveId())
                .domain(row.getDomain())
                .taskType(row.getTaskType())
                .status(row.getStatus())
                .anchorAt(toInstant(row.getAnchorAt()))
                .dueAt(toInstant(row.getDueAt()))
                .dueState(dueState)
                .isOverdue("OVERDUE".equals(dueState))
                .slaOutcome(slaOutcome(row))
                .completionReason(row.getCompletionReason())
                .closeReason(row.getCloseReason())
                .escalationLevel(row.getEscalationLevel())
                .actionability(actionability)
                .canOpenSource(false)
                .actionPath(null)
                .todoVersion(row.getTodoVersion())
                .build();
    }

    private Query query(
            String rawStatus,
            String rawTaskType,
            String rawDueState,
            String rawEscalationLevel,
            String rawCursor,
            Integer rawSize) {
        String status = optionalEnum(rawStatus, STATUSES);
        String taskType = optionalEnum(rawTaskType, TASK_TYPES);
        String dueState = optionalEnum(rawDueState, DUE_STATES);
        String escalationLevel = optionalEnum(rawEscalationLevel, ESCALATION_LEVELS);
        int pageSize = rawSize == null ? DEFAULT_PAGE_SIZE : rawSize;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Cursor cursor = decodeCursor(rawCursor);
        LocalDateTime evaluationTime = cursor == null
                ? LocalDateTime.ofInstant(analyticsClock.instant(), ZoneOffset.UTC)
                : cursor.evaluationTime();
        return new Query(
                status,
                taskType,
                dueState,
                escalationLevel,
                evaluationTime,
                evaluationTime.plusHours(24),
                cursor == null ? null : cursor.openRank(),
                cursor == null ? null : cursor.overdueRank(),
                cursor == null ? null : cursor.dueAt(),
                cursor == null ? null : cursor.todoId(),
                pageSize,
                pageSize + 1);
    }

    private String dueState(ChannelQualityGovernanceTodoRow row, LocalDateTime evaluationTime) {
        if (!row.getDueAt().isAfter(evaluationTime)) {
            return "OVERDUE";
        }
        return row.getDueAt().isAfter(evaluationTime.plus(dueSoonWindow(row.getTaskType())))
                ? "ON_TRACK" : "DUE_SOON";
    }

    private static java.time.Duration dueSoonWindow(String taskType) {
        return switch (taskType) {
            case "ACKNOWLEDGE_CASE" -> java.time.Duration.ofHours(1);
            case "RECORD_PLAN" -> java.time.Duration.ofHours(4);
            case "COMPLETE_RETROSPECTIVE" -> java.time.Duration.ofHours(24);
            default -> throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        };
    }

    private static String slaOutcome(ChannelQualityGovernanceTodoRow row) {
        if ("COMPLETED".equals(row.getStatus())) {
            return row.getCompletedAt().isAfter(row.getDueAt()) ? "OVERDUE_COMPLETED" : "ON_TIME";
        }
        if ("CLOSED".equals(row.getStatus())) {
            return "EXCLUDED_" + row.getCloseReason();
        }
        return null;
    }

    private static String optionalEnum(String value, Set<String> allowed) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static String encodeCursor(ChannelQualityGovernanceTodoRow row, LocalDateTime evaluationTime) {
        int openRank = "OPEN".equals(row.getStatus()) ? 0 : 1;
        int overdueRank = "OPEN".equals(row.getStatus()) && !row.getDueAt().isAfter(evaluationTime) ? 0 : 1;
        String value = String.join("|",
                CURSOR_VERSION,
                String.valueOf(evaluationTime.toInstant(ZoneOffset.UTC).toEpochMilli()),
                String.valueOf(openRank),
                String.valueOf(overdueRank),
                String.valueOf(row.getDueAt().toInstant(ZoneOffset.UTC).toEpochMilli()),
                String.valueOf(row.getId()));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String rawCursor) {
        if (!StringUtils.hasText(rawCursor)) {
            return null;
        }
        try {
            String value = new String(
                    Base64.getUrlDecoder().decode(rawCursor.trim()), StandardCharsets.UTF_8);
            String[] parts = value.split("\\|", -1);
            if (parts.length != 6 || !CURSOR_VERSION.equals(parts[0])) {
                throw new IllegalArgumentException();
            }
            long evaluationEpoch = Long.parseLong(parts[1]);
            int openRank = Integer.parseInt(parts[2]);
            int overdueRank = Integer.parseInt(parts[3]);
            long dueEpoch = Long.parseLong(parts[4]);
            long todoId = Long.parseLong(parts[5]);
            if ((openRank != 0 && openRank != 1) || (overdueRank != 0 && overdueRank != 1)
                    || todoId <= 0) {
                throw new IllegalArgumentException();
            }
            return new Cursor(
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(evaluationEpoch), ZoneOffset.UTC),
                    openRank,
                    overdueRank,
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(dueEpoch), ZoneOffset.UTC),
                    todoId);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private void requireV42Tables() {
        try {
            if (todoMapper.v42TablesExist() == 5) {
                return;
            }
        } catch (RuntimeException ignored) {
            // Read APIs fail closed until the V42 migration is present.
        }
        throw new BizException(ErrorCode.DEPENDENCY_ERROR);
    }

    private void requireModerate(Long uid, int domain) {
        if (!canModerate(uid, domain)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean canModerate(Long uid, Integer domain) {
        return uid != null && uid > 0 && domain != null && domain >= 1 && domain <= 5
                && (adminPermissionService.isAdmin(uid)
                || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR)
                || adminPermissionService.isLocalOpenMode()
                || domainModeratorService.canModerateDomain(uid, domain));
    }

    private static void requireOperator(Long operatorUid) {
        if (operatorUid == null || operatorUid <= 0) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private static int requireDomain(Integer domain) {
        if (domain == null || domain < 1 || domain > 5) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return domain;
    }

    private static void requireRow(ChannelQualityGovernanceTodoRow row) {
        if (row == null || row.getId() == null || row.getId() <= 0
                || row.getCaseId() == null || row.getCaseId() <= 0
                || row.getDomain() == null || row.getDomain() < 1 || row.getDomain() > 5
                || !STATUSES.contains(row.getStatus()) || !TASK_TYPES.contains(row.getTaskType())
                || row.getAnchorAt() == null || row.getDueAt() == null
                || !row.getDueAt().isAfter(row.getAnchorAt())
                || row.getTodoVersion() == null || row.getTodoVersion() < 0
                || !ESCALATION_LEVELS.contains(row.getEscalationLevel())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private static Instant toInstant(LocalDateTime value) {
        return value.atZone(ZoneOffset.UTC).toInstant();
    }

    private record Query(
            String status,
            String taskType,
            String dueState,
            String escalationLevel,
            LocalDateTime evaluationTime,
            LocalDateTime dueSoonAt,
            Integer cursorOpenRank,
            Integer cursorOverdueRank,
            LocalDateTime cursorDueAt,
            Long cursorId,
            int pageSize,
            int limit) {
    }

    private record Cursor(
            LocalDateTime evaluationTime,
            int openRank,
            int overdueRank,
            LocalDateTime dueAt,
            long todoId) {
    }

    @FunctionalInterface
    private interface Visibility {
        boolean visible(ChannelQualityGovernanceTodoRow row);
    }
}
