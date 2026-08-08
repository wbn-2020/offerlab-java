package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceTodoCheckpointRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceTodoEventRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceTodoMapper;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceTodoRow;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChannelQualityGovernanceTodoProjector {

    private static final String SOURCE_CONTRACT_VERSION = "V41_FACT_V1";
    private static final String PROJECTION_VERSION = "V42_1";
    private static final String CASE_OWNER_SCOPE = "CASE_OWNER";
    private static final String RETROSPECTIVE_SCOPE = "RETROSPECTIVE";
    private static final TaskPolicy ACK_CASE_4H =
            new TaskPolicy("ACK_CASE_4H", Duration.ofHours(4));
    private static final TaskPolicy RECORD_PLAN_24H =
            new TaskPolicy("RECORD_PLAN_24H", Duration.ofHours(24));
    private static final TaskPolicy COMPLETE_RETROSPECTIVE_72H =
            new TaskPolicy("COMPLETE_RETROSPECTIVE_72H", Duration.ofHours(72));

    private final ChannelQualityRiskGovernanceMilestoneQueryFacade milestoneQueryFacade;
    private final ChannelQualityGovernanceTodoMapper todoMapper;
    private final ChannelQualityGovernanceReminderPlanner reminderPlanner;
    private final SnowflakeIdGenerator idGenerator;

    /**
     * Rebuilds a case from the V41 fact boundary. Notification wakeups may be duplicated or reordered;
     * this method therefore always reconciles the authoritative fact sequence while holding the V42 checkpoint.
     */
    @Transactional
    public ProjectionResult projectCase(Long caseId) {
        if (caseId == null || caseId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        requireV42Tables();
        List<ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact> facts =
                milestoneQueryFacade.listCaseFacts(caseId);
        if (facts.isEmpty()) {
            return new ProjectionResult(caseId, 0, 0L);
        }
        validateFactSequence(caseId, facts);
        Map<Long, ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact> retrospectivePendingFacts =
                retrospectivePendingFacts(facts);

        ChannelQualityGovernanceTodoCheckpointRow checkpoint = todoMapper.lockCheckpoint(caseId);
        long previousFactId;
        if (checkpoint == null) {
            ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact firstFact = facts.get(0);
            ChannelQualityGovernanceTodoCheckpointRow initial = checkpoint(
                    firstFact, "READY");
            int inserted = todoMapper.insertCheckpointIgnore(initial);
            checkpoint = todoMapper.lockCheckpoint(caseId);
            if (checkpoint == null) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            previousFactId = inserted == 1 ? 0L : requireCheckpoint(checkpoint, caseId);
        } else {
            previousFactId = requireCheckpoint(checkpoint, caseId);
        }

        int applied = 0;
        ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact latest = null;
        for (ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact fact : facts) {
            if (fact.sourceFactId() <= previousFactId) {
                continue;
            }
            applyFact(fact, retrospectivePendingFacts);
            applied++;
            latest = fact;
        }
        if (latest != null && latest.sourceFactId() > checkpoint.getLastSourceFactId()) {
            todoMapper.advanceCheckpointOnlyForward(checkpoint(latest, "READY"));
        }
        return new ProjectionResult(caseId, applied, latest == null ? previousFactId : latest.sourceFactId());
    }

    private void applyFact(
            ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact fact,
            Map<Long, ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact> retrospectivePendingFacts) {
        switch (fact.factType()) {
            case "OWNER_ASSIGNED" -> applyOwnerAssigned(fact);
            case "OWNER_ACKNOWLEDGED" -> applyOwnerAcknowledged(fact);
            case "PLAN_RECORDED" -> completeTodo(
                    fact, "RECORD_PLAN", CASE_OWNER_SCOPE, null, "PLAN_RECORDED");
            case "CLOSE_SNAPSHOT_GENERATED" -> closeCaseOwnerTodos(fact, "SOURCE_TERMINATED");
            case "RETROSPECTIVE_PENDING" -> createRetrospectiveTodo(fact, fact.occurredAt());
            case "RETROSPECTIVE_OWNER_ASSIGNED" -> applyRetrospectiveOwnerAssigned(
                    fact, retrospectivePendingFacts.get(fact.retrospectiveId()));
            case "RETROSPECTIVE_COMPLETED" -> completeTodo(
                    fact, "COMPLETE_RETROSPECTIVE", RETROSPECTIVE_SCOPE,
                    fact.retrospectiveId(), "RETROSPECTIVE_COMPLETED");
            default -> throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private void applyOwnerAssigned(ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact fact) {
        requireCaseOwnerFact(fact);
        closeReplacedCaseOwnerTodos(fact);
        switch (fact.requiredAction()) {
            case "ACKNOWLEDGE_CASE" -> createTodo(
                    fact, "ACKNOWLEDGE_CASE", CASE_OWNER_SCOPE, null, fact.occurredAt(), ACK_CASE_4H);
            case "RECORD_PLAN" -> createTodo(
                    fact, "RECORD_PLAN", CASE_OWNER_SCOPE, null, fact.occurredAt(), RECORD_PLAN_24H);
            case "NONE" -> {
                // The V41 fact explicitly says the current state has no legal owner action.
            }
            default -> throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private void applyOwnerAcknowledged(ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact fact) {
        requireCaseOwnerFact(fact);
        completeTodo(fact, "ACKNOWLEDGE_CASE", CASE_OWNER_SCOPE, null, "OWNER_ACKNOWLEDGED");
        createTodo(fact, "RECORD_PLAN", CASE_OWNER_SCOPE, null, fact.occurredAt(), RECORD_PLAN_24H);
    }

    private void applyRetrospectiveOwnerAssigned(
            ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact fact,
            ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact pendingFact) {
        requireRetrospectiveFact(fact);
        if (pendingFact == null || pendingFact.responsibilityEpoch() == null
                || pendingFact.responsibilityEpoch() > fact.responsibilityEpoch()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        for (ChannelQualityGovernanceTodoRow todo :
                todoMapper.lockOpenTodosByRetrospectiveId(fact.retrospectiveId())) {
            requireTodo(todo);
            if (!RETROSPECTIVE_SCOPE.equals(todo.getSourceScope())) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            if (todo.getResponsibilityEpoch() > fact.responsibilityEpoch()) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            if (todo.getResponsibilityEpoch() < fact.responsibilityEpoch()) {
                closeTodo(todo, fact, "ASSIGNEE_CHANGED");
            }
        }
        createTodo(
                fact,
                "COMPLETE_RETROSPECTIVE",
                RETROSPECTIVE_SCOPE,
                fact.retrospectiveId(),
                pendingFact.occurredAt(),
                COMPLETE_RETROSPECTIVE_72H);
    }

    private void createRetrospectiveTodo(
            ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact fact,
            Instant anchorAt) {
        requireRetrospectiveFact(fact);
        createTodo(
                fact,
                "COMPLETE_RETROSPECTIVE",
                RETROSPECTIVE_SCOPE,
                fact.retrospectiveId(),
                anchorAt,
                COMPLETE_RETROSPECTIVE_72H);
    }

    private void closeReplacedCaseOwnerTodos(
            ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact fact) {
        for (ChannelQualityGovernanceTodoRow todo : todoMapper.lockOpenTodosByCaseId(fact.caseId())) {
            requireTodo(todo);
            if (!CASE_OWNER_SCOPE.equals(todo.getSourceScope())) {
                continue;
            }
            if (todo.getResponsibilityEpoch() > fact.responsibilityEpoch()) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            if (todo.getResponsibilityEpoch() < fact.responsibilityEpoch()) {
                closeTodo(todo, fact, "ASSIGNEE_CHANGED");
            }
        }
    }

    private void closeCaseOwnerTodos(
            ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact fact,
            String reason) {
        for (ChannelQualityGovernanceTodoRow todo : todoMapper.lockOpenTodosByCaseId(fact.caseId())) {
            requireTodo(todo);
            if (CASE_OWNER_SCOPE.equals(todo.getSourceScope())) {
                closeTodo(todo, fact, reason);
            }
        }
    }

    private void createTodo(
            ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact fact,
            String taskType,
            String sourceScope,
            Long retrospectiveId,
            Instant anchorAt,
            TaskPolicy policy) {
        if (!SOURCE_CONTRACT_VERSION.equals(fact.contractVersion())
                || !taskType.equals(policy.taskType())
                || anchorAt == null || fact.ownerUid() == null || fact.ownerUid() <= 0
                || fact.responsibilityEpoch() == null || fact.responsibilityEpoch() < 1) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        LocalDateTime localAnchor = toLocal(anchorAt);
        ChannelQualityGovernanceTodoRow todo = new ChannelQualityGovernanceTodoRow();
        todo.setId(idGenerator.nextId());
        todo.setCaseId(fact.caseId());
        todo.setRetrospectiveId(retrospectiveId);
        todo.setDomain(fact.domain());
        todo.setTaskType(taskType);
        todo.setSourceScope(sourceScope);
        todo.setResponsibilityEpoch(fact.responsibilityEpoch());
        todo.setSourceFactId(fact.sourceFactId());
        todo.setAssigneeUid(fact.ownerUid());
        todo.setStatus("OPEN");
        todo.setAnchorAt(localAnchor);
        todo.setDueAt(localAnchor.plus(policy.duration()));
        todo.setPolicySource("SYSTEM_DEFAULT");
        todo.setPolicyKey(policy.key());
        todo.setPolicyVersion(PROJECTION_VERSION);
        todo.setSlaMinutes(Math.toIntExact(policy.duration().toMinutes()));
        todo.setEscalationLevel("NONE");
        todo.setTodoVersion(0);
        if (todoMapper.insertTodoIgnore(todo) == 1) {
            appendTodoEvent(todo, fact, "TODO_CREATED", null, null, "OPEN", null);
            reminderPlanner.planInitialReminders(todo);
        }
    }

    private void completeTodo(
            ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact fact,
            String taskType,
            String sourceScope,
            Long retrospectiveId,
            String completionReason) {
        for (ChannelQualityGovernanceTodoRow todo :
                todosForScope(fact.caseId(), retrospectiveId, sourceScope)) {
            requireTodo(todo);
            if (!taskType.equals(todo.getTaskType())) {
                continue;
            }
            if (todo.getResponsibilityEpoch().equals(fact.responsibilityEpoch())) {
                if (todoMapper.terminalizeOpenTodo(
                        todo.getId(), todo.getCaseId(), todo.getRetrospectiveId(), todo.getTaskType(),
                        todo.getSourceScope(), todo.getResponsibilityEpoch(), todo.getTodoVersion(),
                        "COMPLETED", fact.sourceFactId(), completionReason, toLocal(fact.occurredAt()),
                        null, toLocal(fact.occurredAt())) == 1) {
                    appendTodoEvent(todo, fact, "TODO_COMPLETED", "OPEN", completionReason, "COMPLETED", null);
                    reminderPlanner.cancelUndispatchedReminders(todo.getId());
                }
            } else if (todo.getResponsibilityEpoch() > fact.responsibilityEpoch()) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
        }
    }

    private void closeTodo(
            ChannelQualityGovernanceTodoRow todo,
            ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact fact,
            String closeReason) {
        if (todoMapper.terminalizeOpenTodo(
                todo.getId(), todo.getCaseId(), todo.getRetrospectiveId(), todo.getTaskType(),
                todo.getSourceScope(), todo.getResponsibilityEpoch(), todo.getTodoVersion(),
                "CLOSED", null, null, null, closeReason, toLocal(fact.occurredAt())) == 1) {
            appendTodoEvent(todo, fact, "TODO_CLOSED", "OPEN", closeReason, "CLOSED", null);
            reminderPlanner.cancelUndispatchedReminders(todo.getId());
        }
    }

    private List<ChannelQualityGovernanceTodoRow> todosForScope(
            Long caseId, Long retrospectiveId, String sourceScope) {
        if (RETROSPECTIVE_SCOPE.equals(sourceScope)) {
            return todoMapper.lockOpenTodosByRetrospectiveId(retrospectiveId);
        }
        return todoMapper.lockOpenTodosByCaseId(caseId);
    }

    private void appendTodoEvent(
            ChannelQualityGovernanceTodoRow todo,
            ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact fact,
            String eventType,
            String previousStatus,
            String reason,
            String status,
            String escalationLevel) {
        ChannelQualityGovernanceTodoEventRow event = new ChannelQualityGovernanceTodoEventRow();
        event.setId(idGenerator.nextId());
        event.setTodoId(todo.getId());
        event.setCaseId(todo.getCaseId());
        event.setEventType(eventType);
        event.setPreviousStatus(previousStatus);
        event.setStatus(status);
        event.setAssigneeUid(todo.getAssigneeUid());
        event.setResponsibilityEpoch(todo.getResponsibilityEpoch());
        event.setSourceFactId(fact.sourceFactId());
        event.setReason(reason);
        event.setPreviousEscalationLevel(null);
        event.setEscalationLevel(escalationLevel);
        event.setTodoVersion("TODO_CREATED".equals(eventType) ? todo.getTodoVersion() : todo.getTodoVersion() + 1);
        event.setOccurredAt(toLocal(fact.occurredAt()));
        todoMapper.insertTodoEventIgnore(event);
    }

    private static Map<Long, ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact>
    retrospectivePendingFacts(
            List<ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact> facts) {
        Map<Long, ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact> values = new HashMap<>();
        for (ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact fact : facts) {
            if ("RETROSPECTIVE_PENDING".equals(fact.factType())) {
                requireRetrospectiveFact(fact);
                ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact previous =
                        values.putIfAbsent(fact.retrospectiveId(), fact);
                if (previous != null) {
                    throw new BizException(ErrorCode.DEPENDENCY_ERROR);
                }
            }
        }
        return values;
    }

    private static void validateFactSequence(
            Long caseId, List<ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact> facts) {
        long previousFactId = 0;
        for (ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact fact : facts) {
            if (fact == null || !caseId.equals(fact.caseId()) || fact.sourceFactId() == null
                    || fact.sourceFactId() <= previousFactId || fact.domain() == null
                    || fact.domain() < 1 || fact.domain() > 5 || fact.occurredAt() == null
                    || !SOURCE_CONTRACT_VERSION.equals(fact.contractVersion())) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            previousFactId = fact.sourceFactId();
        }
    }

    private static void requireCaseOwnerFact(
            ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact fact) {
        if (!CASE_OWNER_SCOPE.equals(fact.responsibilityScope())
                || fact.ownerUid() == null || fact.ownerUid() <= 0
                || fact.responsibilityEpoch() == null || fact.responsibilityEpoch() < 1) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private static void requireRetrospectiveFact(
            ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact fact) {
        if (!RETROSPECTIVE_SCOPE.equals(fact.responsibilityScope())
                || fact.retrospectiveId() == null || fact.retrospectiveId() <= 0
                || fact.ownerUid() == null || fact.ownerUid() <= 0
                || fact.responsibilityEpoch() == null || fact.responsibilityEpoch() < 1) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private static void requireTodo(ChannelQualityGovernanceTodoRow todo) {
        if (todo == null || todo.getId() == null || todo.getId() <= 0
                || todo.getCaseId() == null || todo.getCaseId() <= 0
                || todo.getAssigneeUid() == null || todo.getAssigneeUid() <= 0
                || todo.getResponsibilityEpoch() == null || todo.getResponsibilityEpoch() < 1
                || todo.getTodoVersion() == null || todo.getTodoVersion() < 0
                || !"OPEN".equals(todo.getStatus())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private static long requireCheckpoint(ChannelQualityGovernanceTodoCheckpointRow checkpoint, Long caseId) {
        if (!caseId.equals(checkpoint.getCaseId()) || checkpoint.getLastSourceFactId() == null
                || checkpoint.getLastSourceFactId() <= 0 || checkpoint.getDomain() == null
                || checkpoint.getDomain() < 1 || checkpoint.getDomain() > 5
                || !SOURCE_CONTRACT_VERSION.equals(checkpoint.getSourceContractVersion())
                || !PROJECTION_VERSION.equals(checkpoint.getProjectionVersion())
                || !Set.of("READY", "STALE", "BLOCKED").contains(checkpoint.getHealthStatus())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return checkpoint.getLastSourceFactId();
    }

    private static ChannelQualityGovernanceTodoCheckpointRow checkpoint(
            ChannelQualityRiskGovernanceMilestoneQueryFacade.V41GovernanceFact fact, String healthStatus) {
        ChannelQualityGovernanceTodoCheckpointRow row = new ChannelQualityGovernanceTodoCheckpointRow();
        row.setCaseId(fact.caseId());
        row.setDomain(fact.domain());
        row.setLastSourceFactId(fact.sourceFactId());
        row.setLastSourceOccurredAt(toLocal(fact.occurredAt()));
        row.setSourceContractVersion(SOURCE_CONTRACT_VERSION);
        row.setProjectionVersion(PROJECTION_VERSION);
        row.setHealthStatus(healthStatus);
        return row;
    }

    private void requireV42Tables() {
        try {
            if (todoMapper.v42TablesExist() == 5) {
                return;
            }
        } catch (RuntimeException ignored) {
            // The V42 projection must fail closed until its migration is present.
        }
        throw new BizException(ErrorCode.DEPENDENCY_ERROR);
    }

    private static LocalDateTime toLocal(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private record TaskPolicy(String key, Duration duration) {
        String taskType() {
            return switch (key) {
                case "ACK_CASE_4H" -> "ACKNOWLEDGE_CASE";
                case "RECORD_PLAN_24H" -> "RECORD_PLAN";
                case "COMPLETE_RETROSPECTIVE_72H" -> "COMPLETE_RETROSPECTIVE";
                default -> throw new IllegalArgumentException("Unknown V42 SLA policy");
            };
        }
    }

    public record ProjectionResult(Long caseId, int appliedFactCount, long lastSourceFactId) {
    }
}
