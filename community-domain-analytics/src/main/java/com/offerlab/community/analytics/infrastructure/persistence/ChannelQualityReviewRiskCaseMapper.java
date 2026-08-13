package com.offerlab.community.analytics.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChannelQualityReviewRiskCaseMapper {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_channel_quality_review_risk_case'
            """)
    int caseTableExists();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_channel_quality_review_risk_case_event'
            """)
    int eventTableExists();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 't_channel_quality_review_risk_case'
              AND column_name IN (
                'id', 'batch_id', 'domain', 'trigger_type', 'risk_event_id',
                'risk_code', 'due_state', 'status', 'owner_uid', 'case_version',
                'opened_coordination_version', 'created_by_uid', 'active_batch_id',
                'v41_close_snapshot_id', 'legacy_closed_without_snapshot',
                'create_time', 'update_time'
              )
              AND (
                (column_name IN ('id', 'batch_id', 'created_by_uid')
                    AND data_type = 'bigint' AND is_nullable = 'NO')
                OR (column_name = 'domain'
                    AND data_type = 'tinyint' AND is_nullable = 'NO')
                OR (column_name IN ('trigger_type', 'status')
                    AND data_type = 'varchar' AND character_maximum_length = 32 AND is_nullable = 'NO')
                OR (column_name = 'risk_event_id'
                    AND data_type = 'bigint' AND is_nullable = 'YES')
                OR (column_name IN ('risk_code', 'due_state')
                    AND data_type = 'varchar' AND character_maximum_length = 32 AND is_nullable = 'YES')
                OR (column_name = 'owner_uid'
                    AND data_type = 'bigint' AND is_nullable = 'YES')
                OR (column_name IN ('case_version', 'opened_coordination_version')
                    AND data_type = 'int' AND is_nullable = 'NO')
                OR (column_name = 'v41_close_snapshot_id'
                    AND data_type = 'bigint' AND is_nullable = 'YES')
                OR (column_name = 'legacy_closed_without_snapshot'
                    AND data_type = 'tinyint' AND is_nullable = 'NO')
                OR (column_name = 'active_batch_id'
                    AND data_type = 'bigint' AND is_nullable = 'YES'
                    AND extra LIKE '%GENERATED%')
                OR (column_name IN ('create_time', 'update_time')
                    AND data_type = 'datetime' AND datetime_precision = 3 AND is_nullable = 'NO')
              )
            """)
    int caseColumnsExist();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 't_channel_quality_review_risk_case_event'
              AND column_name IN (
                'id', 'case_id', 'batch_id', 'operator_uid', 'event_type',
                'previous_status', 'status', 'owner_uid',
                'observed_coordination_version', 'case_version', 'note', 'create_time'
              )
              AND (
                (column_name IN ('id', 'case_id', 'batch_id', 'operator_uid')
                    AND data_type = 'bigint' AND is_nullable = 'NO')
                OR (column_name = 'event_type'
                    AND data_type = 'varchar' AND character_maximum_length = 40 AND is_nullable = 'NO')
                OR (column_name = 'previous_status'
                    AND data_type = 'varchar' AND character_maximum_length = 32 AND is_nullable = 'YES')
                OR (column_name = 'status'
                    AND data_type = 'varchar' AND character_maximum_length = 32 AND is_nullable = 'NO')
                OR (column_name = 'owner_uid'
                    AND data_type = 'bigint' AND is_nullable = 'YES')
                OR (column_name IN ('observed_coordination_version', 'case_version')
                    AND data_type = 'int' AND is_nullable = 'NO')
                OR (column_name = 'note'
                    AND data_type = 'varchar' AND character_maximum_length = 500 AND is_nullable = 'NO')
                OR (column_name = 'create_time'
                    AND data_type = 'datetime' AND datetime_precision = 3 AND is_nullable = 'NO')
              )
            """)
    int eventColumnsExist();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.table_constraints
            WHERE table_schema = DATABASE()
              AND table_name = 't_channel_quality_review_risk_case'
              AND (
                (constraint_name = 'PRIMARY' AND constraint_type = 'PRIMARY KEY')
                OR (
                    constraint_name IN (
                        'chk_quality_review_risk_case_domain',
                        'chk_quality_review_risk_case_identity',
                        'chk_quality_review_risk_case_trigger',
                        'chk_quality_review_risk_case_status',
                        'chk_quality_review_risk_case_owner_status',
                        'chk_quality_review_risk_case_v41_close_snapshot'
                    )
                    AND constraint_type = 'CHECK'
                )
              )
            """)
    int caseConstraintsExist();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.table_constraints
            WHERE table_schema = DATABASE()
              AND table_name = 't_channel_quality_review_risk_case_event'
              AND (
                (constraint_name = 'PRIMARY' AND constraint_type = 'PRIMARY KEY')
                OR (
                    constraint_name IN (
                        'chk_quality_review_risk_case_event_identity',
                        'chk_quality_review_risk_case_event_note',
                        'chk_quality_review_risk_case_event_shape'
                    )
                    AND constraint_type = 'CHECK'
                )
              )
            """)
    int eventConstraintsExist();

    @Select("""
            SELECT COUNT(DISTINCT index_name)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 't_channel_quality_review_risk_case'
              AND (
                index_name = 'PRIMARY'
                OR (
                    index_name IN (
                        'uk_quality_review_risk_case_event',
                        'uk_quality_review_risk_case_active_batch',
                        'uk_quality_review_risk_case_v41_close_snapshot'
                    )
                    AND non_unique = 0
                )
                OR index_name IN (
                    'idx_quality_review_risk_case_domain_queue',
                    'idx_quality_review_risk_case_batch',
                    'idx_quality_review_risk_case_v41_snapshot'
                )
              )
            """)
    int caseIndexesExist();

    @Select("""
            SELECT COUNT(DISTINCT index_name)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 't_channel_quality_review_risk_case_event'
              AND index_name IN (
                'PRIMARY',
                'idx_quality_review_risk_case_event_timeline',
                'idx_quality_review_risk_case_event_batch'
              )
            """)
    int eventIndexesExist();

    @Select("""
            SELECT id,
                   batch_id AS batchId,
                   domain,
                   trigger_type AS triggerType,
                   risk_event_id AS riskEventId,
                   risk_code AS riskCode,
                   due_state AS dueState,
                   status,
                   owner_uid AS ownerUid,
                   case_version AS caseVersion,
                   opened_coordination_version AS openedCoordinationVersion,
                   created_by_uid AS createdByUid,
                   v41_close_snapshot_id AS v41CloseSnapshotId,
                   legacy_closed_without_snapshot AS legacyClosedWithoutSnapshot,
                   active_batch_id AS activeBatchId,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_channel_quality_review_risk_case
            WHERE id = #{id}
            """)
    ChannelQualityReviewRiskCaseRow selectById(@Param("id") Long id);

    @Select("""
            SELECT id,
                   batch_id AS batchId,
                   domain,
                   trigger_type AS triggerType,
                   risk_event_id AS riskEventId,
                   risk_code AS riskCode,
                   due_state AS dueState,
                   status,
                   owner_uid AS ownerUid,
                   case_version AS caseVersion,
                   opened_coordination_version AS openedCoordinationVersion,
                   created_by_uid AS createdByUid,
                   v41_close_snapshot_id AS v41CloseSnapshotId,
                   legacy_closed_without_snapshot AS legacyClosedWithoutSnapshot,
                   active_batch_id AS activeBatchId,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_channel_quality_review_risk_case
            WHERE id = #{id}
            FOR UPDATE
            """)
    ChannelQualityReviewRiskCaseRow lockById(@Param("id") Long id);

    @Select("""
            SELECT id,
                   batch_id AS batchId,
                   domain,
                   trigger_type AS triggerType,
                   risk_event_id AS riskEventId,
                   risk_code AS riskCode,
                   due_state AS dueState,
                   status,
                   owner_uid AS ownerUid,
                   case_version AS caseVersion,
                   opened_coordination_version AS openedCoordinationVersion,
                   created_by_uid AS createdByUid,
                   v41_close_snapshot_id AS v41CloseSnapshotId,
                   legacy_closed_without_snapshot AS legacyClosedWithoutSnapshot,
                   active_batch_id AS activeBatchId,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_channel_quality_review_risk_case
            WHERE active_batch_id = #{batchId}
            """)
    ChannelQualityReviewRiskCaseRow selectActiveByBatchId(@Param("batchId") Long batchId);

    @Select("""
            SELECT id,
                   batch_id AS batchId,
                   domain,
                   trigger_type AS triggerType,
                   risk_event_id AS riskEventId,
                   risk_code AS riskCode,
                   due_state AS dueState,
                   status,
                   owner_uid AS ownerUid,
                   case_version AS caseVersion,
                   opened_coordination_version AS openedCoordinationVersion,
                   created_by_uid AS createdByUid,
                   v41_close_snapshot_id AS v41CloseSnapshotId,
                   legacy_closed_without_snapshot AS legacyClosedWithoutSnapshot,
                   active_batch_id AS activeBatchId,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_channel_quality_review_risk_case
            WHERE risk_event_id = #{riskEventId}
            """)
    ChannelQualityReviewRiskCaseRow selectByRiskEventId(@Param("riskEventId") Long riskEventId);

    @Select("""
            SELECT id,
                   batch_id AS batchId,
                   event_type AS eventType,
                   risk_code AS riskCode,
                   coordination_version AS coordinationVersion
            FROM t_collab_content_maintenance_dispatch_batch_event
            WHERE batch_id = #{batchId}
              AND id = #{eventId}
              AND event_type = 'RISK_NOTE_ADDED'
            """)
    ChannelQualityReviewRiskCaseRiskNoteEventRow selectRiskNoteEvent(
            @Param("batchId") Long batchId,
            @Param("eventId") Long eventId);

    @Insert("""
            INSERT INTO t_channel_quality_review_risk_case (
                id,
                batch_id,
                domain,
                trigger_type,
                risk_event_id,
                risk_code,
                due_state,
                status,
                owner_uid,
                case_version,
                opened_coordination_version,
                created_by_uid
            ) VALUES (
                #{id},
                #{batchId},
                #{domain},
                #{triggerType},
                #{riskEventId},
                #{riskCode},
                #{dueState},
                #{status},
                #{ownerUid},
                #{caseVersion},
                #{openedCoordinationVersion},
                #{createdByUid}
            )
            """)
    int insertCase(@Param("id") Long id,
                   @Param("batchId") Long batchId,
                   @Param("domain") Integer domain,
                   @Param("triggerType") String triggerType,
                   @Param("riskEventId") Long riskEventId,
                   @Param("riskCode") String riskCode,
                   @Param("dueState") String dueState,
                   @Param("status") String status,
                   @Param("ownerUid") Long ownerUid,
                   @Param("caseVersion") Integer caseVersion,
                   @Param("openedCoordinationVersion") Integer openedCoordinationVersion,
                   @Param("createdByUid") Long createdByUid);

    @Update("""
            UPDATE t_channel_quality_review_risk_case
            SET status = #{status},
                owner_uid = #{ownerUid},
                case_version = case_version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{caseId}
              AND case_version = #{expectedCaseVersion}
              AND status <> 'CLOSED'
              AND #{status} <> 'CLOSED'
            """)
    int updateCase(@Param("caseId") Long caseId,
                   @Param("expectedCaseVersion") Integer expectedCaseVersion,
                   @Param("status") String status,
                   @Param("ownerUid") Long ownerUid);

    @Update("""
            UPDATE t_channel_quality_review_risk_case
            SET status = 'CLOSED',
                case_version = case_version + 1,
                v41_close_snapshot_id = #{snapshotId},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{caseId}
              AND case_version = #{expectedCaseVersion}
              AND status = 'RESOLVED'
              AND v41_close_snapshot_id IS NULL
              AND legacy_closed_without_snapshot = 0
            """)
    int closeWithSnapshot(@Param("caseId") Long caseId,
                          @Param("expectedCaseVersion") Integer expectedCaseVersion,
                          @Param("snapshotId") Long snapshotId);

    @Insert("""
            INSERT INTO t_channel_quality_review_risk_case_event (
                id,
                case_id,
                batch_id,
                operator_uid,
                event_type,
                previous_status,
                status,
                owner_uid,
                observed_coordination_version,
                case_version,
                note
            ) VALUES (
                #{id},
                #{caseId},
                #{batchId},
                #{operatorUid},
                #{eventType},
                #{previousStatus},
                #{status},
                #{ownerUid},
                #{observedCoordinationVersion},
                #{caseVersion},
                #{note}
            )
            """)
    int insertEvent(@Param("id") Long id,
                    @Param("caseId") Long caseId,
                    @Param("batchId") Long batchId,
                    @Param("operatorUid") Long operatorUid,
                    @Param("eventType") String eventType,
                    @Param("previousStatus") String previousStatus,
                    @Param("status") String status,
                    @Param("ownerUid") Long ownerUid,
                    @Param("observedCoordinationVersion") Integer observedCoordinationVersion,
                    @Param("caseVersion") Integer caseVersion,
                    @Param("note") String note);

    @Select("""
            <script>
            SELECT id,
                   case_id AS caseId,
                   batch_id AS batchId,
                   operator_uid AS operatorUid,
                   event_type AS eventType,
                   previous_status AS previousStatus,
                   status,
                   owner_uid AS ownerUid,
                   observed_coordination_version AS observedCoordinationVersion,
                   case_version AS caseVersion,
                   note,
                   create_time AS createTime
            FROM t_channel_quality_review_risk_case_event
            WHERE case_id = #{caseId}
              AND (#{cursor} = 0 OR id &lt; #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ChannelQualityReviewRiskCaseEventRow> listEvents(
            @Param("caseId") Long caseId,
            @Param("cursor") long cursor,
            @Param("limit") int limit);

    @Select("""
            <script>
            WITH batch_snapshot AS (
                SELECT batch.id AS batchId,
                       batch.domain AS domain,
                       batch.name AS batchName,
                       batch.priority AS priority,
                       batch.due_at AS dueAt,
                       batch.effective_due_at AS effectiveDueAt,
                       batch.coordination_version AS coordinationVersion,
                       batch.candidate_count AS candidateCount,
                       batch.create_time AS createTime,
                       SUM(CASE
                           WHEN task.task_status IN ('OPEN', 'CLAIMED', 'SUBMITTED') THEN 1
                           ELSE 0
                       END) AS activeTaskCount,
                       CASE
                           WHEN batch.effective_due_at IS NULL
                               OR SUM(CASE
                                   WHEN task.task_status IN ('OPEN', 'CLAIMED', 'SUBMITTED') THEN 1
                                   ELSE 0
                               END) = 0 THEN 'NOT_APPLICABLE'
                           WHEN batch.effective_due_at &lt; #{observedAt} THEN 'OVERDUE'
                           WHEN batch.effective_due_at &lt;= DATE_ADD(#{observedAt}, INTERVAL 48 HOUR)
                               THEN 'DUE_SOON'
                           ELSE 'ON_TRACK'
                       END AS dueState
                FROM t_collab_content_maintenance_dispatch_batch batch
                LEFT JOIN t_collab_content_maintenance_task task
                    ON task.dispatch_batch_id = batch.id
                    AND task.source_type = 'CHANNEL_HEALTH'
                WHERE batch.domain = #{domain}
                  AND batch.source_type = 'CHANNEL_HEALTH'
                GROUP BY batch.id,
                         batch.domain,
                         batch.name,
                         batch.priority,
                         batch.due_at,
                         batch.effective_due_at,
                         batch.coordination_version,
                         batch.candidate_count,
                         batch.create_time
            ),
            active_case AS (
                SELECT id,
                       batch_id AS batchId,
                       trigger_type AS triggerType,
                       risk_event_id AS riskEventId,
                       risk_code AS riskCode,
                       due_state AS triggerDueState,
                       status,
                       owner_uid AS ownerUid,
                       case_version AS caseVersion,
                       opened_coordination_version AS openedCoordinationVersion,
                       create_time AS createTime,
                       update_time AS updateTime
                FROM t_channel_quality_review_risk_case
                WHERE active_batch_id IS NOT NULL
            ),
            queue_rows AS (
                SELECT 'ACTIVE_CASE' AS queueSource,
                       CASE
                           WHEN batch.dueState = 'OVERDUE' THEN 1
                           WHEN batch.dueState = 'DUE_SOON' THEN 2
                           WHEN risk_case.status = 'RESOLVED' THEN 3
                           ELSE 4
                       END AS queuePriority,
                       risk_case.updateTime AS queueUpdateTime,
                       risk_case.id AS queueRowId,
                       risk_case.id AS caseId,
                       batch.batchId,
                       batch.domain,
                       batch.batchName,
                       batch.priority,
                       batch.dueAt,
                       batch.effectiveDueAt,
                       batch.coordinationVersion,
                       batch.candidateCount,
                       batch.activeTaskCount,
                       risk_case.triggerType,
                       risk_case.riskEventId,
                       risk_case.riskCode,
                       batch.dueState,
                       risk_case.status,
                       risk_case.ownerUid,
                       risk_case.caseVersion,
                       risk_case.openedCoordinationVersion,
                       risk_case.createTime AS createTime,
                       risk_case.updateTime
                FROM batch_snapshot batch
                INNER JOIN active_case risk_case ON risk_case.batchId = batch.batchId

                UNION ALL

                SELECT 'UNHANDLED_RISK_EVENT' AS queueSource,
                       CASE
                           WHEN batch.dueState = 'OVERDUE' THEN 1
                           WHEN batch.dueState = 'DUE_SOON' THEN 2
                           ELSE 3
                       END AS queuePriority,
                       event.create_time AS queueUpdateTime,
                       event.id AS queueRowId,
                       NULL AS caseId,
                       batch.batchId,
                       batch.domain,
                       batch.batchName,
                       batch.priority,
                       batch.dueAt,
                       batch.effectiveDueAt,
                       batch.coordinationVersion,
                       batch.candidateCount,
                       batch.activeTaskCount,
                       'RISK_EVENT' AS triggerType,
                       event.id AS riskEventId,
                       event.risk_code AS riskCode,
                       batch.dueState,
                       NULL AS status,
                       NULL AS ownerUid,
                       NULL AS caseVersion,
                       NULL AS openedCoordinationVersion,
                       event.create_time AS createTime,
                       event.create_time AS updateTime
                FROM batch_snapshot batch
                INNER JOIN t_collab_content_maintenance_dispatch_batch_event event
                    ON event.batch_id = batch.batchId
                    AND event.event_type = 'RISK_NOTE_ADDED'
                LEFT JOIN t_channel_quality_review_risk_case anchored_case
                    ON anchored_case.risk_event_id = event.id
                LEFT JOIN active_case existing_active_case
                    ON existing_active_case.batchId = batch.batchId
                WHERE anchored_case.id IS NULL
                  AND existing_active_case.id IS NULL
                  AND NOT EXISTS (
                      SELECT 1
                      FROM t_collab_content_maintenance_dispatch_batch_event newer_event
                      LEFT JOIN t_channel_quality_review_risk_case newer_case
                          ON newer_case.risk_event_id = newer_event.id
                      WHERE newer_event.batch_id = event.batch_id
                        AND newer_event.event_type = 'RISK_NOTE_ADDED'
                        AND newer_case.id IS NULL
                        AND newer_event.id > event.id
                  )

                UNION ALL

                SELECT batch.dueState AS queueSource,
                       CASE
                           WHEN batch.dueState = 'OVERDUE' THEN 1
                           ELSE 2
                       END AS queuePriority,
                       batch.effectiveDueAt AS queueUpdateTime,
                       batch.batchId AS queueRowId,
                       NULL AS caseId,
                       batch.batchId,
                       batch.domain,
                       batch.batchName,
                       batch.priority,
                       batch.dueAt,
                       batch.effectiveDueAt,
                       batch.coordinationVersion,
                       batch.candidateCount,
                       batch.activeTaskCount,
                       'DUE_STATE' AS triggerType,
                       NULL AS riskEventId,
                       NULL AS riskCode,
                       batch.dueState,
                       NULL AS status,
                       NULL AS ownerUid,
                       NULL AS caseVersion,
                       NULL AS openedCoordinationVersion,
                       batch.createTime,
                       batch.effectiveDueAt AS updateTime
                FROM batch_snapshot batch
                LEFT JOIN active_case risk_case ON risk_case.batchId = batch.batchId
                WHERE risk_case.id IS NULL
                  AND batch.dueState IN ('DUE_SOON', 'OVERDUE')
                  AND NOT EXISTS (
                      SELECT 1
                      FROM t_collab_content_maintenance_dispatch_batch_event unhandled_event
                      LEFT JOIN t_channel_quality_review_risk_case anchored_case
                          ON anchored_case.risk_event_id = unhandled_event.id
                      WHERE unhandled_event.batch_id = batch.batchId
                        AND unhandled_event.event_type = 'RISK_NOTE_ADDED'
                        AND anchored_case.id IS NULL
                  )
            )
            SELECT queueSource,
                   queuePriority,
                   queueUpdateTime,
                   queueRowId,
                   caseId,
                   batchId,
                   domain,
                   batchName,
                   priority,
                   dueAt,
                   effectiveDueAt,
                   coordinationVersion,
                   candidateCount,
                   activeTaskCount,
                   triggerType,
                   riskEventId,
                   riskCode,
                   dueState,
                   status,
                   ownerUid,
                   caseVersion,
                   openedCoordinationVersion,
                   createTime,
                   updateTime
            FROM queue_rows
            WHERE (
                #{cursorPriority} IS NULL
                OR queuePriority &gt; #{cursorPriority}
                OR (queuePriority = #{cursorPriority} AND queueUpdateTime &lt; #{cursorUpdateTime})
                OR (
                    queuePriority = #{cursorPriority}
                    AND queueUpdateTime = #{cursorUpdateTime}
                    AND (
                        queueRowId &lt; #{cursorRowId}
                        OR (queueRowId = #{cursorRowId} AND queueSource &gt; #{cursorSource})
                    )
                )
              )
              <choose>
                <when test="mode == 'ACTIVE'">
                  AND queueSource = 'ACTIVE_CASE'
                  AND status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS')
                </when>
                <when test="mode == 'RESOLVED'">
                  AND queueSource = 'ACTIVE_CASE'
                  AND status = 'RESOLVED'
                </when>
                <when test="mode == 'UNHANDLED'">
                  AND queueSource IN ('UNHANDLED_RISK_EVENT', 'DUE_SOON', 'OVERDUE')
                </when>
              </choose>
            ORDER BY queuePriority ASC, queueUpdateTime DESC, queueRowId DESC, queueSource ASC
            LIMIT #{limit}
            </script>
            """)
    List<ChannelQualityReviewRiskCaseQueueRow> listQueueRows(
            @Param("domain") Integer domain,
            @Param("mode") String mode,
            @Param("observedAt") LocalDateTime observedAt,
            @Param("cursorPriority") Integer cursorPriority,
            @Param("cursorUpdateTime") LocalDateTime cursorUpdateTime,
            @Param("cursorRowId") Long cursorRowId,
            @Param("cursorSource") String cursorSource,
            @Param("limit") int limit);
}
