package com.offerlab.community.analytics.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface ChannelQualityReviewBatchMapper {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_collab_content_maintenance_dispatch_batch'
            """)
    int batchTableExists();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_collab_content_maintenance_task'
            """)
    int taskTableExists();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_collab_content_maintenance_task_attempt'
            """)
    int attemptTableExists();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_collab_content_maintenance_dispatch_batch_event'
            """)
    int coordinationEventTableExists();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 't_collab_content_maintenance_dispatch_batch_event'
              AND column_name IN (
                'id', 'batch_id', 'operator_uid', 'event_type',
                'previous_due_at', 'effective_due_at',
                'previous_assignee_uid', 'replacement_assignee_uid',
                'risk_code', 'withdraw_reason_code',
                'affected_task_count', 'note',
                'coordination_version', 'withdrawn_batch_id', 'create_time'
              )
              AND (
                (column_name IN ('id', 'batch_id', 'operator_uid')
                    AND data_type = 'bigint' AND is_nullable = 'NO')
                OR (column_name = 'event_type'
                    AND data_type = 'varchar' AND character_maximum_length = 40 AND is_nullable = 'NO')
                OR (column_name IN ('previous_due_at', 'effective_due_at')
                    AND data_type = 'datetime' AND datetime_precision = 3 AND is_nullable = 'YES')
                OR (column_name IN ('previous_assignee_uid', 'replacement_assignee_uid')
                    AND data_type = 'bigint' AND is_nullable = 'YES')
                OR (column_name IN ('risk_code', 'withdraw_reason_code')
                    AND data_type = 'varchar' AND character_maximum_length = 32 AND is_nullable = 'YES')
                OR (column_name = 'affected_task_count'
                    AND data_type = 'tinyint' AND is_nullable = 'NO')
                OR (column_name = 'note'
                    AND data_type = 'varchar' AND character_maximum_length = 500 AND is_nullable = 'NO')
                OR (column_name = 'coordination_version'
                    AND data_type = 'int' AND is_nullable = 'NO')
                OR (column_name = 'withdrawn_batch_id'
                    AND data_type = 'bigint' AND is_nullable = 'YES'
                    AND extra LIKE '%GENERATED%')
                OR (column_name = 'create_time'
                    AND data_type = 'datetime' AND datetime_precision = 3 AND is_nullable = 'NO')
              )
            """)
    int coordinationEventColumnsExist();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.table_constraints
            WHERE table_schema = DATABASE()
              AND table_name = 't_collab_content_maintenance_dispatch_batch_event'
              AND (
                (constraint_name = 'PRIMARY' AND constraint_type = 'PRIMARY KEY')
                OR (
                    constraint_name IN (
                        'chk_maintenance_dispatch_batch_event_identity',
                        'chk_maintenance_dispatch_batch_event_note',
                        'chk_maintenance_dispatch_batch_event_shape'
                    )
                    AND constraint_type = 'CHECK'
                )
              )
            """)
    int coordinationEventConstraintsExist();

    @Select("""
            SELECT COUNT(DISTINCT index_name)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 't_collab_content_maintenance_dispatch_batch_event'
              AND index_name = 'uk_maintenance_dispatch_batch_event_withdrawn'
              AND non_unique = 0
              AND column_name = 'withdrawn_batch_id'
            """)
    int coordinationEventWithdrawnIndexExists();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 't_collab_content_maintenance_dispatch_batch'
              AND column_name IN ('effective_due_at', 'coordination_version')
            """)
    int coordinationColumnsExist();

    @Insert("""
            INSERT INTO t_collab_content_maintenance_dispatch_batch (
                id,
                domain,
                source_type,
                name,
                assignee_uid,
                priority,
                due_at,
                effective_due_at,
                created_by_uid,
                candidate_count
            ) VALUES (
                #{id},
                #{domain},
                #{sourceType},
                #{name},
                #{assigneeUid},
                #{priority},
                #{dueAt},
                #{dueAt},
                #{createdByUid},
                #{candidateCount}
            )
            """)
    int insert(@Param("id") Long id,
               @Param("domain") Integer domain,
               @Param("sourceType") String sourceType,
               @Param("name") String name,
               @Param("assigneeUid") Long assigneeUid,
               @Param("priority") String priority,
               @Param("dueAt") LocalDateTime dueAt,
               @Param("createdByUid") Long createdByUid,
               @Param("candidateCount") Integer candidateCount);

    @Select("""
            SELECT id,
                   domain,
                   source_type AS sourceType,
                   name,
                   assignee_uid AS assigneeUid,
                   created_by_uid AS createdByUid,
                   priority,
                   due_at AS dueAt,
                   candidate_count AS candidateCount,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_maintenance_dispatch_batch
            WHERE id = #{id}
              AND source_type = 'CHANNEL_HEALTH'
            """)
    ChannelQualityReviewBatchRow selectById(@Param("id") Long id);

    @Select("""
            <script>
            SELECT id,
                   domain,
                   source_type AS sourceType,
                   name,
                   assignee_uid AS assigneeUid,
                   created_by_uid AS createdByUid,
                   priority,
                   due_at AS dueAt,
                   candidate_count AS candidateCount,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_maintenance_dispatch_batch
            WHERE domain = #{domain}
              AND source_type = 'CHANNEL_HEALTH'
              AND (#{cursor} = 0 OR id &lt; #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ChannelQualityReviewBatchRow> listByDomain(
            @Param("domain") Integer domain,
            @Param("cursor") long cursor,
            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT dispatch_batch_id AS batchId,
                   task_status AS status,
                   COUNT(*) AS taskCount
            FROM t_collab_content_maintenance_task
            WHERE dispatch_batch_id IN
            <foreach collection="batchIds" item="batchId" open="(" separator="," close=")">
                #{batchId}
            </foreach>
              AND source_type = 'CHANNEL_HEALTH'
            GROUP BY dispatch_batch_id, task_status
            </script>
            """)
    List<ChannelQualityReviewBatchTaskStatusRow> listTaskStatusCounts(
            @Param("batchIds") Collection<Long> batchIds);

    @Select("""
            SELECT task.id AS taskId,
                   task.dispatch_batch_id AS batchId,
                   task.source_post_id AS sourcePostId,
                   task.source_ref_id AS sourceRefId,
                   task.title,
                   task.task_status AS status,
                   task.terminal_outcome_code AS terminalOutcomeCode,
                   (SELECT attempt.decision
                      FROM t_collab_content_maintenance_task_attempt attempt
                     WHERE attempt.task_id = task.id
                     ORDER BY attempt.attempt_no DESC
                     LIMIT 1) AS latestAttemptDecision
            FROM t_collab_content_maintenance_task task
            WHERE task.dispatch_batch_id = #{batchId}
              AND task.source_type = 'CHANNEL_HEALTH'
            ORDER BY task.id ASC
            LIMIT #{limit}
            """)
    List<ChannelQualityReviewBatchTaskRow> listTasksByBatchId(
            @Param("batchId") Long batchId,
            @Param("limit") int limit);

    @Select("""
            SELECT id,
                   domain,
                   source_type AS sourceType,
                   name,
                   assignee_uid AS assigneeUid,
                   due_at AS dueAt,
                   effective_due_at AS effectiveDueAt,
                   coordination_version AS coordinationVersion,
                   candidate_count AS candidateCount,
                   create_time AS createTime
            FROM t_collab_content_maintenance_dispatch_batch
            WHERE id = #{id}
              AND source_type = 'CHANNEL_HEALTH'
            """)
    ChannelQualityReviewBatchCoordinationRow selectCoordinationById(@Param("id") Long id);

    @Select("""
            SELECT id,
                   domain,
                   source_type AS sourceType,
                   name,
                   assignee_uid AS assigneeUid,
                   due_at AS dueAt,
                   effective_due_at AS effectiveDueAt,
                   coordination_version AS coordinationVersion,
                   candidate_count AS candidateCount,
                   create_time AS createTime
            FROM t_collab_content_maintenance_dispatch_batch
            WHERE id = #{id}
              AND source_type = 'CHANNEL_HEALTH'
            FOR UPDATE
            """)
    ChannelQualityReviewBatchCoordinationRow lockCoordinationById(@Param("id") Long id);

    @Select("""
            SELECT task.id AS taskId,
                   task.title,
                   task.task_status AS status,
                   (SELECT attempt.decision
                      FROM t_collab_content_maintenance_task_attempt attempt
                     WHERE attempt.task_id = task.id
                     ORDER BY attempt.attempt_no DESC
                     LIMIT 1) AS latestAttemptDecision,
                   task.assignee_uid AS assigneeUid,
                   task.due_at AS dueAt,
                   task.update_time AS updateTime
            FROM t_collab_content_maintenance_task task
            WHERE task.dispatch_batch_id = #{batchId}
              AND task.source_type = 'CHANNEL_HEALTH'
            ORDER BY task.id ASC
            LIMIT #{limit}
            """)
    List<ChannelQualityReviewBatchCoordinationTaskRow> listCoordinationTasks(
            @Param("batchId") Long batchId,
            @Param("limit") int limit);

    @Update("""
            UPDATE t_collab_content_maintenance_dispatch_batch
            SET effective_due_at = #{effectiveDueAt},
                coordination_version = coordination_version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{batchId}
              AND coordination_version = #{expectedVersion}
            """)
    int extendDeadline(@Param("batchId") Long batchId,
                       @Param("expectedVersion") Integer expectedVersion,
                       @Param("effectiveDueAt") LocalDateTime effectiveDueAt);

    @Update("""
            UPDATE t_collab_content_maintenance_dispatch_batch
            SET coordination_version = coordination_version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{batchId}
              AND coordination_version = #{expectedVersion}
            """)
    int advanceCoordinationVersion(@Param("batchId") Long batchId,
                                   @Param("expectedVersion") Integer expectedVersion);

    @Insert("""
            INSERT INTO t_collab_content_maintenance_dispatch_batch_event (
                id,
                batch_id,
                operator_uid,
                event_type,
                previous_due_at,
                effective_due_at,
                previous_assignee_uid,
                replacement_assignee_uid,
                risk_code,
                withdraw_reason_code,
                affected_task_count,
                note,
                coordination_version
            ) VALUES (
                #{id},
                #{batchId},
                #{operatorUid},
                #{eventType},
                #{previousDueAt},
                #{effectiveDueAt},
                #{previousAssigneeUid},
                #{replacementAssigneeUid},
                #{riskCode},
                #{withdrawReasonCode},
                #{affectedTaskCount},
                #{note},
                #{coordinationVersion}
            )
            """)
    int insertCoordinationEvent(@Param("id") Long id,
                                @Param("batchId") Long batchId,
                                @Param("operatorUid") Long operatorUid,
                                @Param("eventType") String eventType,
                                @Param("previousDueAt") LocalDateTime previousDueAt,
                                @Param("effectiveDueAt") LocalDateTime effectiveDueAt,
                                @Param("previousAssigneeUid") Long previousAssigneeUid,
                                @Param("replacementAssigneeUid") Long replacementAssigneeUid,
                                @Param("riskCode") String riskCode,
                                @Param("withdrawReasonCode") String withdrawReasonCode,
                                @Param("affectedTaskCount") Integer affectedTaskCount,
                                @Param("note") String note,
                                @Param("coordinationVersion") Integer coordinationVersion);

    @Select("""
            <script>
            SELECT id,
                   batch_id AS batchId,
                   event_type AS eventType,
                   previous_due_at AS previousDueAt,
                   effective_due_at AS effectiveDueAt,
                   previous_assignee_uid AS previousAssigneeUid,
                   replacement_assignee_uid AS replacementAssigneeUid,
                   risk_code AS riskCode,
                   withdraw_reason_code AS withdrawReasonCode,
                   affected_task_count AS affectedTaskCount,
                   note,
                   coordination_version AS coordinationVersion,
                   create_time AS createTime
            FROM t_collab_content_maintenance_dispatch_batch_event
            WHERE batch_id = #{batchId}
              AND (#{cursor} = 0 OR id &lt; #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ChannelQualityReviewBatchEventRow> listCoordinationEvents(
            @Param("batchId") Long batchId,
            @Param("cursor") long cursor,
            @Param("limit") int limit);
}
