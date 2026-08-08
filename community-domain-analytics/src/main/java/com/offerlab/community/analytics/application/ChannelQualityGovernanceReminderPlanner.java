package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.GovernanceReminderRequestedEvent;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceReminderAttemptRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceReminderIntentRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceTodoMapper;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceTodoRow;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.mq.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelQualityGovernanceReminderPlanner {

    private static final String SCHEDULE_VERSION = "V42_1";
    private static final int DISPATCH_BATCH_SIZE = 50;

    private final ChannelQualityGovernanceTodoMapper todoMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final EventPublisher eventPublisher;
    private final Clock analyticsClock;

    /**
     * The projector calls this in its own transaction. Every logical reminder is materialized once;
     * reminders that are already obsolete are retained as suppressed history instead of being backfilled.
     */
    public void planInitialReminders(ChannelQualityGovernanceTodoRow todo) {
        LocalDateTime now = now();
        List<ReminderStage> stages = stagesFor(todo);
        int latestDueStage = latestDueStage(stages, now);
        for (int index = 0; index < stages.size(); index++) {
            ReminderStage stage = stages.get(index);
            boolean supersededByLateMaterialization = latestDueStage > index;
            String status = supersededByLateMaterialization ? "SUPPRESSED" : "PLANNED";
            String disposition = supersededByLateMaterialization
                    ? "SKIPPED_LATE_MATERIALIZATION" : null;
            ChannelQualityGovernanceReminderIntentRow intent = newIntent(
                    todo, stage, status, disposition);
            if (todoMapper.insertReminderIntentIgnore(intent) == 1) {
                if ("SUPPRESSED".equals(status)) {
                    recordAttempt(
                            intent,
                            "late-materialization:" + intent.getId(),
                            "SKIPPED_LATE_MATERIALIZATION",
                            "NOT_EVALUATED",
                            null);
                } else if (!stage.scheduledAt().isAfter(now)) {
                    dispatch(intent);
                }
            }
        }
    }

    @Scheduled(fixedDelayString = "${offerlab.governance-reminder.dispatch-delay-ms:60000}")
    @Transactional
    public void dispatchDueReminderIntents() {
        LocalDateTime now = now();
        List<ChannelQualityGovernanceReminderIntentRow> intents =
                todoMapper.lockDispatchableReminderIntents(now, DISPATCH_BATCH_SIZE);
        for (ChannelQualityGovernanceReminderIntentRow intent : intents) {
            if (intent == null || intent.getId() == null || intent.getIntentVersion() == null) {
                log.warn("Skipping malformed V42 governance reminder intent");
                continue;
            }
            dispatch(intent);
        }
    }

    public void cancelUndispatchedReminders(Long todoId) {
        if (todoId != null && todoId > 0) {
            todoMapper.cancelUndispatchedReminderIntents(todoId);
        }
    }

    private void dispatch(ChannelQualityGovernanceReminderIntentRow intent) {
        ChannelQualityGovernanceTodoRow todo = todoMapper.lockTodoById(intent.getTodoId());
        if (todo == null || !"OPEN".equals(todo.getStatus())
                || todo.getTodoVersion() == null || todo.getTodoVersion() < 0
                || todo.getAssigneeUid() == null || todo.getAssigneeUid() <= 0
                || intent.getDeliverBefore() == null || now().isAfter(intent.getDeliverBefore())) {
            todoMapper.cancelUndispatchedReminderIntents(intent.getTodoId());
            return;
        }
        if (todoMapper.markReminderOutboxed(intent.getId(), intent.getIntentVersion()) != 1) {
            return;
        }
        recordAttempt(
                intent,
                "outbox-request:" + intent.getId() + ":" + intent.getIntentVersion(),
                "RETRY_PENDING",
                "NOT_EVALUATED",
                null);
        eventPublisher.publish(GovernanceReminderRequestedEvent.builder()
                .reminderId(intent.getId())
                .todoId(todo.getId())
                .caseId(todo.getCaseId())
                .scheduleVersion(intent.getScheduleVersion())
                .reminderKind(intent.getReminderKind())
                .sequenceNo(intent.getSequenceNo())
                .build());
    }

    private ChannelQualityGovernanceReminderIntentRow newIntent(
            ChannelQualityGovernanceTodoRow todo,
            ReminderStage stage,
            String status,
            String disposition) {
        LocalDateTime deliverBefore = todo.getDueAt().plusHours(
                "COMPLETE_RETROSPECTIVE".equals(todo.getTaskType()) ? 96 : 48);
        ChannelQualityGovernanceReminderIntentRow intent = new ChannelQualityGovernanceReminderIntentRow();
        intent.setId(idGenerator.nextId());
        intent.setTodoId(todo.getId());
        intent.setCaseId(todo.getCaseId());
        intent.setAssigneeUid(todo.getAssigneeUid());
        intent.setReminderKind(stage.reminderKind());
        intent.setSequenceNo(stage.sequenceNo());
        intent.setScheduleVersion(SCHEDULE_VERSION);
        intent.setScheduledAt(stage.scheduledAt());
        intent.setDeliverBefore(deliverBefore);
        intent.setStatus(status);
        intent.setDisposition(disposition);
        intent.setNextAttemptAt(null);
        intent.setNotificationDedupKey(
                todo.getId() + ":" + SCHEDULE_VERSION + ":" + stage.reminderKind() + ":" + stage.sequenceNo());
        intent.setIntentVersion(0);
        return intent;
    }

    private void recordAttempt(
            ChannelQualityGovernanceReminderIntentRow intent,
            String requestEventKey,
            String outcome,
            String policyDecision,
            LocalDateTime retryAfter) {
        ChannelQualityGovernanceReminderAttemptRow attempt =
                new ChannelQualityGovernanceReminderAttemptRow();
        attempt.setId(idGenerator.nextId());
        attempt.setReminderId(intent.getId());
        attempt.setAttemptNo(todoMapper.countReminderAttempts(intent.getId()) + 1);
        attempt.setRequestEventKey(requestEventKey);
        attempt.setOutcome(outcome);
        attempt.setPolicyDecision(policyDecision);
        attempt.setRetryAfter(retryAfter);
        attempt.setOccurredAt(now());
        todoMapper.insertReminderAttemptIgnore(attempt);
    }

    private static List<ReminderStage> stagesFor(ChannelQualityGovernanceTodoRow todo) {
        LocalDateTime dueAt = todo.getDueAt();
        return switch (todo.getTaskType()) {
            case "ACKNOWLEDGE_CASE" -> List.of(
                    new ReminderStage("DUE_SOON", 1, dueAt.minusHours(1)),
                    new ReminderStage("DUE", 1, dueAt),
                    new ReminderStage("OVERDUE", 1, dueAt.plusHours(4)));
            case "RECORD_PLAN" -> List.of(
                    new ReminderStage("DUE_SOON", 1, dueAt.minusHours(4)),
                    new ReminderStage("DUE", 1, dueAt),
                    new ReminderStage("OVERDUE", 1, dueAt.plusHours(24)));
            case "COMPLETE_RETROSPECTIVE" -> List.of(
                    new ReminderStage("DUE_SOON", 1, dueAt.minusHours(24)),
                    new ReminderStage("DUE", 1, dueAt),
                    new ReminderStage("OVERDUE", 1, dueAt.plusHours(24)),
                    new ReminderStage("OVERDUE", 2, dueAt.plusHours(72)));
            default -> throw new IllegalArgumentException("Unsupported V42 todo type");
        };
    }

    private static int latestDueStage(List<ReminderStage> stages, LocalDateTime now) {
        int latest = -1;
        for (int index = 0; index < stages.size(); index++) {
            if (!stages.get(index).scheduledAt().isAfter(now)) {
                latest = index;
            }
        }
        return latest;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(analyticsClock.instant(), ZoneOffset.UTC);
    }

    private record ReminderStage(String reminderKind, int sequenceNo, LocalDateTime scheduledAt) {
    }
}
