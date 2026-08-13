package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.ChannelQualityGovernanceAutomationCommandFacade;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceReminderIntentRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceTodoEventRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceTodoMapper;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceTodoRow;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChannelQualityGovernanceAutomationCommandFacadeImpl
        implements ChannelQualityGovernanceAutomationCommandFacade {

    private static final String CONTRACT_VERSION = "V42_GOVERNANCE_AUTOMATION_COMMAND_V1";
    private static final String SCHEDULE_VERSION = "V42_1";
    private static final Set<String> ESCALATION_LEVELS =
            Set.of("NONE", "CHANNEL_ATTENTION", "GOVERNANCE_ATTENTION");
    private static final Set<String> REMINDER_KINDS =
            Set.of("DUE_SOON", "DUE", "OVERDUE");

    private final ChannelQualityGovernanceTodoMapper todoMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final Clock analyticsClock;

    @Override
    @Transactional
    public AutomationCommandResult queueReminderIfEligible(
            Long todoId,
            Integer expectedTodoVersion,
            String reminderOccurrenceKey,
            String targetIdempotencyKey) {
        requireKey(targetIdempotencyKey);
        ReminderOccurrence occurrence = parseOccurrence(reminderOccurrenceKey);
        ChannelQualityGovernanceTodoRow todo = lockTodo(todoId);
        requireTodoVersion(todo, expectedTodoVersion);
        if (!"OPEN".equals(todo.getStatus()) || todo.getAssigneeUid() == null
                || todo.getAssigneeUid() <= 0 || todo.getDueAt() == null) {
            return result("SKIPPED_INELIGIBLE", todo, null, null, false);
        }
        String dedupKey = "v45:" + targetIdempotencyKey;
        ChannelQualityGovernanceReminderIntentRow existing =
                todoMapper.selectReminderByDedupKey(dedupKey);
        if (existing != null) {
            requireReminder(existing, todo, occurrence);
            return result("QUEUED", todo, existing.getId(), null, true);
        }
        ChannelQualityGovernanceReminderIntentRow existingOccurrence =
                todoMapper.selectReminderByOccurrence(
                        todo.getId(), SCHEDULE_VERSION, occurrence.kind(), occurrence.sequence());
        if (existingOccurrence != null) {
            requireReminder(existingOccurrence, todo, occurrence);
            return result("SKIPPED_ALREADY_QUEUED", todo, existingOccurrence.getId(), null, false);
        }
        LocalDateTime now = now();
        LocalDateTime deliverBefore = todo.getDueAt().plusHours(
                "COMPLETE_RETROSPECTIVE".equals(todo.getTaskType()) ? 96 : 48);
        if (!deliverBefore.isAfter(now)) {
            return result("SKIPPED_EXPIRED", todo, null, null, false);
        }
        ChannelQualityGovernanceReminderIntentRow intent = new ChannelQualityGovernanceReminderIntentRow();
        intent.setId(idGenerator.nextId());
        intent.setTodoId(todo.getId());
        intent.setCaseId(todo.getCaseId());
        intent.setAssigneeUid(todo.getAssigneeUid());
        intent.setReminderKind(occurrence.kind());
        intent.setSequenceNo(occurrence.sequence());
        intent.setScheduleVersion(SCHEDULE_VERSION);
        intent.setScheduledAt(now);
        intent.setDeliverBefore(deliverBefore);
        intent.setStatus("PLANNED");
        intent.setDisposition(null);
        intent.setNextAttemptAt(null);
        intent.setNotificationDedupKey(dedupKey);
        intent.setIntentVersion(0);
        try {
            if (todoMapper.insertReminderIntentIgnore(intent) != 1) {
                ChannelQualityGovernanceReminderIntentRow concurrent =
                        todoMapper.selectReminderByDedupKey(dedupKey);
                if (concurrent != null) {
                    requireReminder(concurrent, todo, occurrence);
                    return result("QUEUED", todo, concurrent.getId(), null, true);
                }
                ChannelQualityGovernanceReminderIntentRow concurrentOccurrence =
                        todoMapper.selectReminderByOccurrence(
                                todo.getId(), SCHEDULE_VERSION, occurrence.kind(), occurrence.sequence());
                if (concurrentOccurrence == null) {
                    throw new BizException(ErrorCode.DEPENDENCY_ERROR);
                }
                requireReminder(concurrentOccurrence, todo, occurrence);
                return result("SKIPPED_ALREADY_QUEUED", todo, concurrentOccurrence.getId(), null, false);
            }
        } catch (DuplicateKeyException ex) {
            ChannelQualityGovernanceReminderIntentRow concurrent =
                    todoMapper.selectReminderByDedupKey(dedupKey);
            if (concurrent != null) {
                requireReminder(concurrent, todo, occurrence);
                return result("QUEUED", todo, concurrent.getId(), null, true);
            }
            ChannelQualityGovernanceReminderIntentRow concurrentOccurrence =
                    todoMapper.selectReminderByOccurrence(
                            todo.getId(), SCHEDULE_VERSION, occurrence.kind(), occurrence.sequence());
            if (concurrentOccurrence == null) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            requireReminder(concurrentOccurrence, todo, occurrence);
            return result("SKIPPED_ALREADY_QUEUED", todo, concurrentOccurrence.getId(), null, false);
        }
        return result("QUEUED", todo, intent.getId(), null, false);
    }

    @Override
    @Transactional
    public AutomationCommandResult escalateTodoIfEligible(
            Long todoId,
            Integer expectedTodoVersion,
            String expectedEscalationLevel,
            String targetEscalationLevel,
            String targetIdempotencyKey) {
        requireKey(targetIdempotencyKey);
        String expected = normalizeLevel(expectedEscalationLevel);
        String target = normalizeLevel(targetEscalationLevel);
        if (!validEscalationTransition(expected, target)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        ChannelQualityGovernanceTodoRow todo = lockTodo(todoId);
        requireTodoVersion(todo, expectedTodoVersion);
        if (!"OPEN".equals(todo.getStatus()) || !expected.equals(todo.getEscalationLevel())) {
            return result("SKIPPED_STALE_FACT", todo, null, null, false);
        }
        ChannelQualityGovernanceTodoEventRow existing =
                todoMapper.selectTodoEventByReason(todo.getId(), "TODO_ESCALATED", targetIdempotencyKey);
        if (existing != null) {
            if (!target.equals(existing.getEscalationLevel())
                    || !expected.equals(existing.getPreviousEscalationLevel())) {
                throw new BizException(ErrorCode.DUPLICATE_OPERATION);
            }
            return result("ESCALATED", todo, null, existing.getId(), true);
        }
        long eventId = idGenerator.nextId();
        if (todoMapper.advanceTodoEscalation(
                todo.getId(), expectedTodoVersion, expected, target, eventId, now()) != 1) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        ChannelQualityGovernanceTodoEventRow event = new ChannelQualityGovernanceTodoEventRow();
        event.setId(eventId);
        event.setTodoId(todo.getId());
        event.setCaseId(todo.getCaseId());
        event.setEventType("TODO_ESCALATED");
        event.setPreviousStatus("OPEN");
        event.setStatus("OPEN");
        event.setAssigneeUid(todo.getAssigneeUid());
        event.setResponsibilityEpoch(todo.getResponsibilityEpoch());
        // V42 event uniqueness permits one TODO_ESCALATED event per source fact. Each automation
        // command therefore materializes its own immutable source fact identity.
        event.setSourceFactId(eventId);
        event.setReason(targetIdempotencyKey);
        event.setPreviousEscalationLevel(expected);
        event.setEscalationLevel(target);
        event.setTodoVersion(expectedTodoVersion + 1);
        event.setOccurredAt(now());
        try {
            if (todoMapper.insertTodoEventIgnore(event) != 1) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return new ChannelQualityGovernanceAutomationCommandFacade.AutomationCommandResult(
                CONTRACT_VERSION, "ESCALATED", todo.getId(), expectedTodoVersion + 1,
                target, null, eventId, false);
    }

    private ChannelQualityGovernanceTodoRow lockTodo(Long todoId) {
        if (todoId == null || todoId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        ChannelQualityGovernanceTodoRow todo;
        try {
            todo = todoMapper.lockTodoById(todoId);
            if (todoMapper.v42TablesExist() != 5) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (todo == null || todo.getId() == null || todo.getCaseId() == null
                || todo.getDomain() == null || todo.getDomain() < 1 || todo.getDomain() > 5
                || todo.getTodoVersion() == null || todo.getTodoVersion() < 0
                || !ESCALATION_LEVELS.contains(todo.getEscalationLevel())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return todo;
    }

    private static void requireTodoVersion(ChannelQualityGovernanceTodoRow todo, Integer expected) {
        if (expected == null || expected < 0 || !expected.equals(todo.getTodoVersion())) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private static void requireKey(String key) {
        if (!StringUtils.hasText(key) || key.trim().length() > 160
                || !key.trim().matches("[A-Za-z0-9._:-]{8,160}")) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static ReminderOccurrence parseOccurrence(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String[] parts = raw.trim().toUpperCase(Locale.ROOT).split(":", -1);
        if (parts.length != 2 || !REMINDER_KINDS.contains(parts[0])) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        try {
            int sequence = Integer.parseInt(parts[1]);
            if (sequence < 1 || sequence > 2) {
                throw new NumberFormatException();
            }
            return new ReminderOccurrence(parts[0], sequence);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static String normalizeLevel(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!ESCALATION_LEVELS.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static boolean validEscalationTransition(String expected, String target) {
        return ("NONE".equals(expected) && "CHANNEL_ATTENTION".equals(target))
                || ("CHANNEL_ATTENTION".equals(expected)
                && "GOVERNANCE_ATTENTION".equals(target));
    }

    private static void requireReminder(
            ChannelQualityGovernanceReminderIntentRow row,
            ChannelQualityGovernanceTodoRow todo,
            ReminderOccurrence occurrence) {
        if (row.getTodoId() == null || !row.getTodoId().equals(todo.getId())
                || !SCHEDULE_VERSION.equals(row.getScheduleVersion())
                || !occurrence.kind().equals(row.getReminderKind())
                || !occurrence.sequence().equals(row.getSequenceNo())
                || row.getId() == null || row.getId() <= 0) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private AutomationCommandResult result(
            String outcome,
            ChannelQualityGovernanceTodoRow todo,
            Long intentId,
            Long eventId,
            boolean replayed) {
        return new AutomationCommandResult(
                CONTRACT_VERSION,
                outcome,
                todo.getId(),
                todo.getTodoVersion(),
                todo.getEscalationLevel(),
                intentId,
                eventId,
                replayed);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(analyticsClock.instant(), ZoneOffset.UTC);
    }

    private record ReminderOccurrence(String kind, Integer sequence) {
    }
}
