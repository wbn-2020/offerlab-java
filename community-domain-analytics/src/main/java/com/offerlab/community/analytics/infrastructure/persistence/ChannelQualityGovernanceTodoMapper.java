package com.offerlab.community.analytics.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChannelQualityGovernanceTodoMapper {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN (
                't_channel_quality_governance_todo',
                't_channel_quality_governance_todo_event',
                't_channel_quality_governance_reminder_intent',
                't_channel_quality_governance_reminder_attempt',
                't_channel_quality_governance_todo_checkpoint'
              )
            """)
    int v42TablesExist();

    @Select("""
            SELECT case_id AS caseId,
                   domain,
                   last_source_fact_id AS lastSourceFactId,
                   last_source_occurred_at AS lastSourceOccurredAt,
                   source_contract_version AS sourceContractVersion,
                   projection_version AS projectionVersion,
                   health_status AS healthStatus,
                   checkpoint_version AS checkpointVersion,
                   update_time AS updateTime
            FROM t_channel_quality_governance_todo_checkpoint
            WHERE case_id = #{caseId}
            """)
    ChannelQualityGovernanceTodoCheckpointRow selectCheckpoint(@Param("caseId") Long caseId);

    @Select("""
            SELECT case_id AS caseId,
                   domain,
                   last_source_fact_id AS lastSourceFactId,
                   last_source_occurred_at AS lastSourceOccurredAt,
                   source_contract_version AS sourceContractVersion,
                   projection_version AS projectionVersion,
                   health_status AS healthStatus,
                   checkpoint_version AS checkpointVersion,
                   update_time AS updateTime
            FROM t_channel_quality_governance_todo_checkpoint
            WHERE case_id = #{caseId}
            FOR UPDATE
            """)
    ChannelQualityGovernanceTodoCheckpointRow lockCheckpoint(@Param("caseId") Long caseId);

    @Insert("""
            INSERT IGNORE INTO t_channel_quality_governance_todo_checkpoint (
                case_id, domain, last_source_fact_id, last_source_occurred_at,
                source_contract_version, projection_version, health_status, checkpoint_version
            ) VALUES (
                #{row.caseId}, #{row.domain}, #{row.lastSourceFactId}, #{row.lastSourceOccurredAt},
                #{row.sourceContractVersion}, #{row.projectionVersion}, #{row.healthStatus}, 0
            )
            """)
    int insertCheckpointIgnore(@Param("row") ChannelQualityGovernanceTodoCheckpointRow row);

    @Update("""
            UPDATE t_channel_quality_governance_todo_checkpoint
            SET domain = #{row.domain},
                last_source_fact_id = #{row.lastSourceFactId},
                last_source_occurred_at = #{row.lastSourceOccurredAt},
                source_contract_version = #{row.sourceContractVersion},
                projection_version = #{row.projectionVersion},
                health_status = #{row.healthStatus},
                checkpoint_version = checkpoint_version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE case_id = #{row.caseId}
              AND last_source_fact_id < #{row.lastSourceFactId}
            """)
    int advanceCheckpointOnlyForward(@Param("row") ChannelQualityGovernanceTodoCheckpointRow row);

    @Select("""
            SELECT id,
                   case_id AS caseId,
                   retrospective_id AS retrospectiveId,
                   domain,
                   task_type AS taskType,
                   source_scope AS sourceScope,
                   responsibility_epoch AS responsibilityEpoch,
                   source_fact_id AS sourceFactId,
                   assignee_uid AS assigneeUid,
                   status,
                   anchor_at AS anchorAt,
                   due_at AS dueAt,
                   policy_source AS policySource,
                   policy_key AS policyKey,
                   policy_version AS policyVersion,
                   sla_minutes AS slaMinutes,
                   completion_fact_id AS completionFactId,
                   completion_reason AS completionReason,
                   completed_at AS completedAt,
                   close_reason AS closeReason,
                   closed_at AS closedAt,
                   escalation_level AS escalationLevel,
                   last_escalation_event_id AS lastEscalationEventId,
                   escalated_at AS escalatedAt,
                   todo_version AS todoVersion,
                   active_marker AS activeMarker,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_channel_quality_governance_todo
            WHERE case_id = #{caseId}
              AND status = 'OPEN'
            ORDER BY task_type ASC, id ASC
            FOR UPDATE
            """)
    List<ChannelQualityGovernanceTodoRow> lockOpenTodosByCaseId(@Param("caseId") Long caseId);

    @Select("""
            SELECT id,
                   case_id AS caseId,
                   retrospective_id AS retrospectiveId,
                   domain,
                   task_type AS taskType,
                   source_scope AS sourceScope,
                   responsibility_epoch AS responsibilityEpoch,
                   source_fact_id AS sourceFactId,
                   assignee_uid AS assigneeUid,
                   status,
                   anchor_at AS anchorAt,
                   due_at AS dueAt,
                   policy_source AS policySource,
                   policy_key AS policyKey,
                   policy_version AS policyVersion,
                   sla_minutes AS slaMinutes,
                   completion_fact_id AS completionFactId,
                   completion_reason AS completionReason,
                   completed_at AS completedAt,
                   close_reason AS closeReason,
                   closed_at AS closedAt,
                   escalation_level AS escalationLevel,
                   last_escalation_event_id AS lastEscalationEventId,
                   escalated_at AS escalatedAt,
                   todo_version AS todoVersion,
                   active_marker AS activeMarker,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_channel_quality_governance_todo
            WHERE retrospective_id = #{retrospectiveId}
              AND status = 'OPEN'
            ORDER BY task_type ASC, id ASC
            FOR UPDATE
            """)
    List<ChannelQualityGovernanceTodoRow> lockOpenTodosByRetrospectiveId(
            @Param("retrospectiveId") Long retrospectiveId);

    @Select("""
            SELECT id,
                   case_id AS caseId,
                   retrospective_id AS retrospectiveId,
                   domain,
                   task_type AS taskType,
                   source_scope AS sourceScope,
                   responsibility_epoch AS responsibilityEpoch,
                   source_fact_id AS sourceFactId,
                   assignee_uid AS assigneeUid,
                   status,
                   anchor_at AS anchorAt,
                   due_at AS dueAt,
                   policy_source AS policySource,
                   policy_key AS policyKey,
                   policy_version AS policyVersion,
                   sla_minutes AS slaMinutes,
                   completion_fact_id AS completionFactId,
                   completion_reason AS completionReason,
                   completed_at AS completedAt,
                   close_reason AS closeReason,
                   closed_at AS closedAt,
                   escalation_level AS escalationLevel,
                   last_escalation_event_id AS lastEscalationEventId,
                   escalated_at AS escalatedAt,
                   todo_version AS todoVersion,
                   active_marker AS activeMarker,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_channel_quality_governance_todo
            WHERE id = #{todoId}
            FOR UPDATE
            """)
    ChannelQualityGovernanceTodoRow lockTodoById(@Param("todoId") Long todoId);

    @Select("""
            SELECT id,
                   todo_id AS todoId,
                   case_id AS caseId,
                   event_type AS eventType,
                   previous_status AS previousStatus,
                   status,
                   assignee_uid AS assigneeUid,
                   responsibility_epoch AS responsibilityEpoch,
                   source_fact_id AS sourceFactId,
                   reason,
                   previous_escalation_level AS previousEscalationLevel,
                   escalation_level AS escalationLevel,
                   todo_version AS todoVersion,
                   occurred_at AS occurredAt,
                   create_time AS createTime
            FROM t_channel_quality_governance_todo_event
            WHERE todo_id = #{todoId}
              AND event_type = #{eventType}
              AND reason = #{reason}
            ORDER BY id DESC
            LIMIT 1
            """)
    ChannelQualityGovernanceTodoEventRow selectTodoEventByReason(
            @Param("todoId") Long todoId,
            @Param("eventType") String eventType,
            @Param("reason") String reason);

    @Update("""
            UPDATE t_channel_quality_governance_todo
            SET escalation_level = #{targetEscalationLevel},
                last_escalation_event_id = #{eventId},
                escalated_at = #{occurredAt},
                todo_version = todo_version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{todoId}
              AND status = 'OPEN'
              AND todo_version = #{expectedTodoVersion}
              AND escalation_level = #{expectedEscalationLevel}
            """)
    int advanceTodoEscalation(
            @Param("todoId") Long todoId,
            @Param("expectedTodoVersion") Integer expectedTodoVersion,
            @Param("expectedEscalationLevel") String expectedEscalationLevel,
            @Param("targetEscalationLevel") String targetEscalationLevel,
            @Param("eventId") Long eventId,
            @Param("occurredAt") LocalDateTime occurredAt);

    @Insert("""
            INSERT IGNORE INTO t_channel_quality_governance_todo (
                id, case_id, retrospective_id, domain, task_type, source_scope, responsibility_epoch,
                source_fact_id, assignee_uid, status, anchor_at, due_at, policy_source, policy_key,
                policy_version, sla_minutes, completion_fact_id, completion_reason, completed_at,
                close_reason, closed_at, escalation_level, last_escalation_event_id, escalated_at,
                todo_version
            ) VALUES (
                #{row.id}, #{row.caseId}, #{row.retrospectiveId}, #{row.domain}, #{row.taskType},
                #{row.sourceScope}, #{row.responsibilityEpoch}, #{row.sourceFactId}, #{row.assigneeUid},
                #{row.status}, #{row.anchorAt}, #{row.dueAt}, #{row.policySource}, #{row.policyKey},
                #{row.policyVersion}, #{row.slaMinutes}, #{row.completionFactId},
                #{row.completionReason}, #{row.completedAt}, #{row.closeReason}, #{row.closedAt},
                #{row.escalationLevel}, #{row.lastEscalationEventId}, #{row.escalatedAt}, #{row.todoVersion}
            )
            """)
    int insertTodoIgnore(@Param("row") ChannelQualityGovernanceTodoRow row);

    @Insert("""
            INSERT IGNORE INTO t_channel_quality_governance_todo_event (
                id, todo_id, case_id, event_type, previous_status, status, assignee_uid,
                responsibility_epoch, source_fact_id, reason, previous_escalation_level,
                escalation_level, todo_version, occurred_at
            ) VALUES (
                #{row.id}, #{row.todoId}, #{row.caseId}, #{row.eventType}, #{row.previousStatus},
                #{row.status}, #{row.assigneeUid}, #{row.responsibilityEpoch}, #{row.sourceFactId},
                #{row.reason}, #{row.previousEscalationLevel}, #{row.escalationLevel},
                #{row.todoVersion}, #{row.occurredAt}
            )
            """)
    int insertTodoEventIgnore(@Param("row") ChannelQualityGovernanceTodoEventRow row);

    @Insert("""
            INSERT IGNORE INTO t_channel_quality_governance_reminder_intent (
                id, todo_id, case_id, assignee_uid, reminder_kind, sequence_no, schedule_version,
                scheduled_at, deliver_before, status, disposition, next_attempt_at,
                notification_dedup_key, intent_version
            ) VALUES (
                #{row.id}, #{row.todoId}, #{row.caseId}, #{row.assigneeUid}, #{row.reminderKind},
                #{row.sequenceNo}, #{row.scheduleVersion}, #{row.scheduledAt}, #{row.deliverBefore},
                #{row.status}, #{row.disposition}, #{row.nextAttemptAt}, #{row.notificationDedupKey},
                #{row.intentVersion}
            )
            """)
    int insertReminderIntentIgnore(@Param("row") ChannelQualityGovernanceReminderIntentRow row);

    @Select("""
            SELECT id,
                   todo_id AS todoId,
                   case_id AS caseId,
                   assignee_uid AS assigneeUid,
                   reminder_kind AS reminderKind,
                   sequence_no AS sequenceNo,
                   schedule_version AS scheduleVersion,
                   scheduled_at AS scheduledAt,
                   deliver_before AS deliverBefore,
                   status,
                   disposition,
                   next_attempt_at AS nextAttemptAt,
                   notification_dedup_key AS notificationDedupKey,
                   intent_version AS intentVersion,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_channel_quality_governance_reminder_intent
            WHERE notification_dedup_key = #{dedupKey}
            """)
    ChannelQualityGovernanceReminderIntentRow selectReminderByDedupKey(
            @Param("dedupKey") String dedupKey);

    @Select("""
            SELECT id,
                   todo_id AS todoId,
                   case_id AS caseId,
                   assignee_uid AS assigneeUid,
                   reminder_kind AS reminderKind,
                   sequence_no AS sequenceNo,
                   schedule_version AS scheduleVersion,
                   scheduled_at AS scheduledAt,
                   deliver_before AS deliverBefore,
                   status,
                   disposition,
                   next_attempt_at AS nextAttemptAt,
                   notification_dedup_key AS notificationDedupKey,
                   intent_version AS intentVersion,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_channel_quality_governance_reminder_intent
            WHERE todo_id = #{todoId}
              AND schedule_version = #{scheduleVersion}
              AND reminder_kind = #{reminderKind}
              AND sequence_no = #{sequenceNo}
            """)
    ChannelQualityGovernanceReminderIntentRow selectReminderByOccurrence(
            @Param("todoId") Long todoId,
            @Param("scheduleVersion") String scheduleVersion,
            @Param("reminderKind") String reminderKind,
            @Param("sequenceNo") Integer sequenceNo);

    @Insert("""
            INSERT IGNORE INTO t_channel_quality_governance_reminder_attempt (
                id, reminder_id, attempt_no, request_event_key, outcome, policy_decision,
                retry_after, notification_id, error_code, occurred_at
            ) VALUES (
                #{row.id}, #{row.reminderId}, #{row.attemptNo}, #{row.requestEventKey},
                #{row.outcome}, #{row.policyDecision}, #{row.retryAfter}, #{row.notificationId},
                #{row.errorCode}, #{row.occurredAt}
            )
            """)
    int insertReminderAttemptIgnore(@Param("row") ChannelQualityGovernanceReminderAttemptRow row);

    @Select("""
            SELECT COUNT(*)
            FROM t_channel_quality_governance_reminder_attempt
            WHERE reminder_id = #{reminderId}
            """)
    int countReminderAttempts(@Param("reminderId") Long reminderId);

    @Select("""
            SELECT id,
                   todo_id AS todoId,
                   case_id AS caseId,
                   assignee_uid AS assigneeUid,
                   reminder_kind AS reminderKind,
                   sequence_no AS sequenceNo,
                   schedule_version AS scheduleVersion,
                   scheduled_at AS scheduledAt,
                   deliver_before AS deliverBefore,
                   status,
                   disposition,
                   next_attempt_at AS nextAttemptAt,
                   notification_dedup_key AS notificationDedupKey,
                   intent_version AS intentVersion,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_channel_quality_governance_reminder_intent
            WHERE status IN ('PLANNED', 'DEFERRED')
              AND COALESCE(next_attempt_at, scheduled_at) <= #{now}
            ORDER BY COALESCE(next_attempt_at, scheduled_at) ASC, id ASC
            LIMIT #{limit}
            FOR UPDATE
            """)
    List<ChannelQualityGovernanceReminderIntentRow> lockDispatchableReminderIntents(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    @Update("""
            UPDATE t_channel_quality_governance_reminder_intent
            SET status = 'OUTBOXED',
                disposition = NULL,
                next_attempt_at = NULL,
                intent_version = intent_version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{reminderId}
              AND status IN ('PLANNED', 'DEFERRED')
              AND intent_version = #{expectedIntentVersion}
            """)
    int markReminderOutboxed(@Param("reminderId") Long reminderId,
                             @Param("expectedIntentVersion") Integer expectedIntentVersion);

    @Update("""
            UPDATE t_channel_quality_governance_reminder_intent
            SET status = 'CANCELLED',
                disposition = 'SUPPRESSED_TODO_TERMINAL',
                next_attempt_at = NULL,
                intent_version = intent_version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE todo_id = #{todoId}
              AND status IN ('PLANNED', 'DEFERRED')
            """)
    int cancelUndispatchedReminderIntents(@Param("todoId") Long todoId);

    @Update("""
            UPDATE t_channel_quality_governance_todo
            SET status = #{terminalStatus},
                completion_fact_id = #{completionFactId},
                completion_reason = #{completionReason},
                completed_at = #{completedAt},
                close_reason = #{closeReason},
                closed_at = #{closedAt},
                todo_version = todo_version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{todoId}
              AND case_id = #{caseId}
              AND retrospective_id <=> #{retrospectiveId}
              AND task_type = #{taskType}
              AND source_scope = #{sourceScope}
              AND responsibility_epoch = #{responsibilityEpoch}
              AND status = 'OPEN'
              AND todo_version = #{expectedTodoVersion}
            """)
    int terminalizeOpenTodo(@Param("todoId") Long todoId,
                            @Param("caseId") Long caseId,
                            @Param("retrospectiveId") Long retrospectiveId,
                            @Param("taskType") String taskType,
                            @Param("sourceScope") String sourceScope,
                            @Param("responsibilityEpoch") Integer responsibilityEpoch,
                            @Param("expectedTodoVersion") Integer expectedTodoVersion,
                            @Param("terminalStatus") String terminalStatus,
                            @Param("completionFactId") Long completionFactId,
                            @Param("completionReason") String completionReason,
                            @Param("completedAt") LocalDateTime completedAt,
                            @Param("closeReason") String closeReason,
                            @Param("closedAt") LocalDateTime closedAt);

    @Select("""
            <script>
            SELECT id,
                   case_id AS caseId,
                   retrospective_id AS retrospectiveId,
                   domain,
                   task_type AS taskType,
                   source_scope AS sourceScope,
                   responsibility_epoch AS responsibilityEpoch,
                   source_fact_id AS sourceFactId,
                   assignee_uid AS assigneeUid,
                   status,
                   anchor_at AS anchorAt,
                   due_at AS dueAt,
                   policy_source AS policySource,
                   policy_key AS policyKey,
                   policy_version AS policyVersion,
                   sla_minutes AS slaMinutes,
                   completion_fact_id AS completionFactId,
                   completion_reason AS completionReason,
                   completed_at AS completedAt,
                   close_reason AS closeReason,
                   closed_at AS closedAt,
                   escalation_level AS escalationLevel,
                   last_escalation_event_id AS lastEscalationEventId,
                   escalated_at AS escalatedAt,
                   todo_version AS todoVersion,
                   active_marker AS activeMarker,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_channel_quality_governance_todo
            WHERE assignee_uid = #{assigneeUid}
              AND (#{status} IS NULL OR status = #{status})
              AND (#{taskType} IS NULL OR task_type = #{taskType})
              AND (
                #{dueState} IS NULL OR #{dueState} = 'ALL'
                OR (#{dueState} = 'ON_TRACK' AND status = 'OPEN' AND due_at &gt; CASE task_type
                    WHEN 'ACKNOWLEDGE_CASE' THEN DATE_ADD(#{evaluationTime}, INTERVAL 1 HOUR)
                    WHEN 'RECORD_PLAN' THEN DATE_ADD(#{evaluationTime}, INTERVAL 4 HOUR)
                    ELSE DATE_ADD(#{evaluationTime}, INTERVAL 24 HOUR)
                  END)
                OR (#{dueState} = 'DUE_SOON' AND status = 'OPEN'
                    AND due_at &gt; #{evaluationTime} AND due_at &lt;= CASE task_type
                        WHEN 'ACKNOWLEDGE_CASE' THEN DATE_ADD(#{evaluationTime}, INTERVAL 1 HOUR)
                        WHEN 'RECORD_PLAN' THEN DATE_ADD(#{evaluationTime}, INTERVAL 4 HOUR)
                        ELSE DATE_ADD(#{evaluationTime}, INTERVAL 24 HOUR)
                      END)
                OR (#{dueState} = 'OVERDUE' AND status = 'OPEN' AND due_at &lt;= #{evaluationTime})
              )
              AND (
                #{cursorOpenRank} IS NULL
                OR (CASE WHEN status = 'OPEN' THEN 0 ELSE 1 END) &gt; #{cursorOpenRank}
                OR (
                    (CASE WHEN status = 'OPEN' THEN 0 ELSE 1 END) = #{cursorOpenRank}
                    AND (CASE WHEN status = 'OPEN' AND due_at &lt;= #{evaluationTime} THEN 0 ELSE 1 END)
                        &gt; #{cursorOverdueRank}
                )
                OR (
                    (CASE WHEN status = 'OPEN' THEN 0 ELSE 1 END) = #{cursorOpenRank}
                    AND (CASE WHEN status = 'OPEN' AND due_at &lt;= #{evaluationTime} THEN 0 ELSE 1 END)
                        = #{cursorOverdueRank}
                    AND due_at &gt; #{cursorDueAt}
                )
                OR (
                    (CASE WHEN status = 'OPEN' THEN 0 ELSE 1 END) = #{cursorOpenRank}
                    AND (CASE WHEN status = 'OPEN' AND due_at &lt;= #{evaluationTime} THEN 0 ELSE 1 END)
                        = #{cursorOverdueRank}
                    AND due_at = #{cursorDueAt}
                    AND id &gt; #{cursorId}
                )
              )
            ORDER BY (CASE WHEN status = 'OPEN' THEN 0 ELSE 1 END) ASC,
                     (CASE WHEN status = 'OPEN' AND due_at &lt;= #{evaluationTime} THEN 0 ELSE 1 END) ASC,
                     due_at ASC, id ASC
            LIMIT #{limit}
            </script>
            """)
    List<ChannelQualityGovernanceTodoRow> listPersonalCursorCandidates(
            @Param("assigneeUid") Long assigneeUid,
            @Param("status") String status,
            @Param("taskType") String taskType,
            @Param("dueState") String dueState,
            @Param("evaluationTime") LocalDateTime evaluationTime,
            @Param("dueSoonAt") LocalDateTime dueSoonAt,
            @Param("cursorOpenRank") Integer cursorOpenRank,
            @Param("cursorOverdueRank") Integer cursorOverdueRank,
            @Param("cursorDueAt") LocalDateTime cursorDueAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT id,
                   case_id AS caseId,
                   retrospective_id AS retrospectiveId,
                   domain,
                   task_type AS taskType,
                   source_scope AS sourceScope,
                   responsibility_epoch AS responsibilityEpoch,
                   source_fact_id AS sourceFactId,
                   assignee_uid AS assigneeUid,
                   status,
                   anchor_at AS anchorAt,
                   due_at AS dueAt,
                   policy_source AS policySource,
                   policy_key AS policyKey,
                   policy_version AS policyVersion,
                   sla_minutes AS slaMinutes,
                   completion_fact_id AS completionFactId,
                   completion_reason AS completionReason,
                   completed_at AS completedAt,
                   close_reason AS closeReason,
                   closed_at AS closedAt,
                   escalation_level AS escalationLevel,
                   last_escalation_event_id AS lastEscalationEventId,
                   escalated_at AS escalatedAt,
                   todo_version AS todoVersion,
                   active_marker AS activeMarker,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_channel_quality_governance_todo
            WHERE domain = #{domain}
              AND (#{status} IS NULL OR status = #{status})
              AND (#{taskType} IS NULL OR task_type = #{taskType})
              AND (#{escalationLevel} IS NULL OR escalation_level = #{escalationLevel})
              AND (
                #{dueState} IS NULL OR #{dueState} = 'ALL'
                OR (#{dueState} = 'ON_TRACK' AND status = 'OPEN' AND due_at &gt; CASE task_type
                    WHEN 'ACKNOWLEDGE_CASE' THEN DATE_ADD(#{evaluationTime}, INTERVAL 1 HOUR)
                    WHEN 'RECORD_PLAN' THEN DATE_ADD(#{evaluationTime}, INTERVAL 4 HOUR)
                    ELSE DATE_ADD(#{evaluationTime}, INTERVAL 24 HOUR)
                  END)
                OR (#{dueState} = 'DUE_SOON' AND status = 'OPEN'
                    AND due_at &gt; #{evaluationTime} AND due_at &lt;= CASE task_type
                        WHEN 'ACKNOWLEDGE_CASE' THEN DATE_ADD(#{evaluationTime}, INTERVAL 1 HOUR)
                        WHEN 'RECORD_PLAN' THEN DATE_ADD(#{evaluationTime}, INTERVAL 4 HOUR)
                        ELSE DATE_ADD(#{evaluationTime}, INTERVAL 24 HOUR)
                      END)
                OR (#{dueState} = 'OVERDUE' AND status = 'OPEN' AND due_at &lt;= #{evaluationTime})
              )
              AND (
                #{cursorOpenRank} IS NULL
                OR (CASE WHEN status = 'OPEN' THEN 0 ELSE 1 END) &gt; #{cursorOpenRank}
                OR (
                    (CASE WHEN status = 'OPEN' THEN 0 ELSE 1 END) = #{cursorOpenRank}
                    AND (CASE WHEN status = 'OPEN' AND due_at &lt;= #{evaluationTime} THEN 0 ELSE 1 END)
                        &gt; #{cursorOverdueRank}
                )
                OR (
                    (CASE WHEN status = 'OPEN' THEN 0 ELSE 1 END) = #{cursorOpenRank}
                    AND (CASE WHEN status = 'OPEN' AND due_at &lt;= #{evaluationTime} THEN 0 ELSE 1 END)
                        = #{cursorOverdueRank}
                    AND due_at &gt; #{cursorDueAt}
                )
                OR (
                    (CASE WHEN status = 'OPEN' THEN 0 ELSE 1 END) = #{cursorOpenRank}
                    AND (CASE WHEN status = 'OPEN' AND due_at &lt;= #{evaluationTime} THEN 0 ELSE 1 END)
                        = #{cursorOverdueRank}
                    AND due_at = #{cursorDueAt}
                    AND id &gt; #{cursorId}
                )
              )
            ORDER BY (CASE WHEN status = 'OPEN' THEN 0 ELSE 1 END) ASC,
                     (CASE WHEN status = 'OPEN' AND due_at &lt;= #{evaluationTime} THEN 0 ELSE 1 END) ASC,
                     due_at ASC, id ASC
            LIMIT #{limit}
            </script>
            """)
    List<ChannelQualityGovernanceTodoRow> listDomainCursorCandidates(
            @Param("domain") Integer domain,
            @Param("status") String status,
            @Param("taskType") String taskType,
            @Param("dueState") String dueState,
            @Param("escalationLevel") String escalationLevel,
            @Param("evaluationTime") LocalDateTime evaluationTime,
            @Param("dueSoonAt") LocalDateTime dueSoonAt,
            @Param("cursorOpenRank") Integer cursorOpenRank,
            @Param("cursorOverdueRank") Integer cursorOverdueRank,
            @Param("cursorDueAt") LocalDateTime cursorDueAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);
}
