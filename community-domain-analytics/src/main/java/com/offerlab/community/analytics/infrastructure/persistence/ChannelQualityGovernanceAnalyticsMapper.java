package com.offerlab.community.analytics.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChannelQualityGovernanceAnalyticsMapper {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN (
                't_channel_quality_governance_case_fact',
                't_channel_quality_governance_recurrence_fact',
                't_channel_quality_governance_review_fact',
                't_channel_quality_governance_batch_fact',
                't_channel_quality_governance_todo_fact',
                't_channel_quality_governance_metric_daily',
                't_channel_quality_governance_projection_cursor',
                't_channel_quality_governance_dirty_bucket',
                't_channel_quality_governance_projection_generation',
                't_channel_quality_governance_rebuild_request',
                't_channel_quality_governance_projection_issue'
              )
            """)
    int v43TablesExist();

    @Select("""
            SELECT id,
                   status,
                   scope_from AS scopeFrom,
                   scope_to AS scopeTo,
                   started_at AS startedAt,
                   completed_at AS completedAt,
                   validation_digest AS validationDigest,
                   is_active AS isActive
            FROM t_channel_quality_governance_projection_generation
            WHERE is_active = 1
              AND status = 'COMPLETED'
            LIMIT 1
            """)
    ChannelQualityGovernanceAnalyticsGenerationRow selectActiveGeneration();

    @Select("""
            SELECT id,
                   status,
                   scope_from AS scopeFrom,
                   scope_to AS scopeTo,
                   started_at AS startedAt,
                   completed_at AS completedAt,
                   validation_digest AS validationDigest,
                   is_active AS isActive
            FROM t_channel_quality_governance_projection_generation
            WHERE status = 'COMPLETED'
            ORDER BY completed_at DESC, id DESC
            LIMIT 1
            """)
    ChannelQualityGovernanceAnalyticsGenerationRow selectLatestCompletedGeneration();

    @Select("""
            SELECT source_type AS sourceType,
                   cursor_time AS cursorTime,
                   cursor_id AS cursorId,
                   covered_through AS coveredThrough,
                   backlog_count AS backlogCount,
                   last_success_at AS lastSuccessAt,
                   last_error_code AS lastErrorCode,
                   projection_generation AS projectionGeneration,
                   cursor_version AS cursorVersion
            FROM t_channel_quality_governance_projection_cursor
            WHERE projection_generation = #{projectionGeneration}
            ORDER BY source_type ASC
            """)
    List<ChannelQualityGovernanceAnalyticsCursorRow> selectCursors(
            @Param("projectionGeneration") Long projectionGeneration);

    @Select("""
            SELECT COUNT(*) AS openedCount,
                   COALESCE(SUM(CASE
                       WHEN first_acknowledged_at IS NOT NULL AND first_acknowledged_at <= #{asOf}
                       THEN 1 ELSE 0 END), 0) AS acknowledgedCount,
                   COALESCE(SUM(CASE
                       WHEN first_planned_at IS NOT NULL AND first_planned_at <= #{asOf}
                       THEN 1 ELSE 0 END), 0) AS plannedCount,
                   COALESCE(SUM(CASE
                       WHEN resolution_submitted_at IS NOT NULL AND resolution_submitted_at <= #{asOf}
                       THEN 1 ELSE 0 END), 0) AS resolutionSubmittedCount,
                   COALESCE(SUM(CASE
                       WHEN closed_at IS NOT NULL AND closed_at <= #{asOf}
                       THEN 1 ELSE 0 END), 0) AS governanceClosedCount,
                   COALESCE(SUM(CASE
                       WHEN closed_at IS NOT NULL AND closed_at <= #{asOf}
                        AND recovery_verification_status = 'FULL'
                       THEN 1 ELSE 0 END), 0) AS recoveryVerifiedCount
            FROM t_channel_quality_governance_case_fact
            WHERE case_opened_at >= #{from}
              AND case_opened_at < #{to}
              AND projection_generation = #{projectionGeneration}
              AND (#{domain} IS NULL OR domain = #{domain})
              AND (#{triggerType} IS NULL OR trigger_type = #{triggerType})
              AND (#{riskCategory} IS NULL OR risk_category = #{riskCategory})
            """)
    ChannelQualityGovernanceAnalyticsFunnelRow selectFunnel(
            @Param("projectionGeneration") Long projectionGeneration,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("asOf") LocalDateTime asOf,
            @Param("domain") Integer domain,
            @Param("triggerType") String triggerType,
            @Param("riskCategory") String riskCategory);

    @Select("""
            WITH eligible AS (
                SELECT TIMESTAMPDIFF(SECOND, case_opened_at, closed_at) AS durationSeconds
                FROM t_channel_quality_governance_case_fact
                WHERE closed_at >= #{from}
                  AND closed_at < #{to}
                  AND closed_at <= #{asOf}
                  AND closed_at >= case_opened_at
                  AND projection_generation = #{projectionGeneration}
                  AND (#{domain} IS NULL OR domain = #{domain})
                  AND (#{triggerType} IS NULL OR trigger_type = #{triggerType})
                  AND (#{riskCategory} IS NULL OR risk_category = #{riskCategory})
            ),
            ranked AS (
                SELECT durationSeconds,
                       ROW_NUMBER() OVER (ORDER BY durationSeconds ASC) AS rowNumber,
                       COUNT(*) OVER () AS totalCount
                FROM eligible
            )
            SELECT COUNT(*) AS sampleCount,
                   CAST(AVG(durationSeconds) AS SIGNED) AS averageSeconds,
                   MAX(CASE WHEN rowNumber = CEIL(totalCount * 0.50) THEN durationSeconds END) AS p50Seconds,
                   MAX(CASE WHEN rowNumber = CEIL(totalCount * 0.90) THEN durationSeconds END) AS p90Seconds,
                   MAX(durationSeconds) AS maxSeconds
            FROM ranked
            """)
    ChannelQualityGovernanceAnalyticsHandlingDurationRow selectHandlingDuration(
            @Param("projectionGeneration") Long projectionGeneration,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("asOf") LocalDateTime asOf,
            @Param("domain") Integer domain,
            @Param("triggerType") String triggerType,
            @Param("riskCategory") String riskCategory);

    @Select("""
            SELECT metric_code AS metricCode,
                   bucket_date AS bucketDate,
                   domain,
                   dimension_type AS dimensionType,
                   dimension_value AS dimensionValue,
                   numerator,
                   denominator,
                   sample_count AS sampleCount,
                   value_sum_seconds AS valueSumSeconds,
                   availability_status AS availabilityStatus,
                   definition_version AS definitionVersion
            FROM t_channel_quality_governance_metric_daily
            WHERE projection_generation = #{projectionGeneration}
              AND metric_code = #{metricCode}
              AND bucket_date >= #{fromDate}
              AND bucket_date < #{toDate}
              AND domain = #{domain}
              AND dimension_type = #{dimensionType}
              AND dimension_value = #{dimensionValue}
            ORDER BY bucket_date ASC
            """)
    List<ChannelQualityGovernanceAnalyticsMetricRow> selectMetricRows(
            @Param("projectionGeneration") Long projectionGeneration,
            @Param("metricCode") String metricCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("domain") Integer domain,
            @Param("dimensionType") String dimensionType,
            @Param("dimensionValue") String dimensionValue);

    @Select("""
            SELECT metric_code AS metricCode,
                   NULL AS bucketDate,
                   NULL AS domain,
                   dimension_type AS dimensionType,
                   dimension_value AS dimensionValue,
                   SUM(numerator) AS numerator,
                   SUM(denominator) AS denominator,
                   SUM(sample_count) AS sampleCount,
                   SUM(value_sum_seconds) AS valueSumSeconds,
                   CASE
                       WHEN SUM(CASE WHEN availability_status = 'UNAVAILABLE' THEN 1 ELSE 0 END) > 0
                           THEN 'UNAVAILABLE'
                       WHEN SUM(CASE WHEN availability_status = 'SUPPRESSED' THEN 1 ELSE 0 END) > 0
                           THEN 'SUPPRESSED'
                       ELSE 'AVAILABLE'
                   END AS availabilityStatus,
                   MIN(definition_version) AS definitionVersion
            FROM t_channel_quality_governance_metric_daily
            WHERE projection_generation = #{projectionGeneration}
              AND metric_code = #{metricCode}
              AND bucket_date >= #{fromDate}
              AND bucket_date < #{toDate}
              AND domain = #{domain}
              AND dimension_type = #{dimensionType}
            GROUP BY metric_code, dimension_type, dimension_value
            ORDER BY dimension_value ASC
            LIMIT 100
            """)
    List<ChannelQualityGovernanceAnalyticsMetricRow> selectBreakdownRows(
            @Param("projectionGeneration") Long projectionGeneration,
            @Param("metricCode") String metricCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("domain") Integer domain,
            @Param("dimensionType") String dimensionType);

    @Select("""
            SELECT
                (SELECT COUNT(*)
                 FROM t_channel_quality_governance_dirty_bucket
                 WHERE status IN ('PENDING', 'RUNNING', 'FAILED')) AS dirtyBucketCount,
                (SELECT MIN(bucket_date)
                 FROM t_channel_quality_governance_dirty_bucket
                 WHERE status IN ('PENDING', 'RUNNING', 'FAILED')) AS oldestDirtyBucket,
                (SELECT COUNT(*)
                 FROM t_channel_quality_governance_projection_issue
                 WHERE status = 'OPEN'
                   AND projection_generation = #{projectionGeneration}) AS openIssueCount
            """)
    ChannelQualityGovernanceAnalyticsHealthSummaryRow selectHealthSummary(
            @Param("projectionGeneration") Long projectionGeneration);

    @Select("""
            SELECT id,
                   operator_uid AS operatorUid,
                   idempotency_key AS idempotencyKey,
                   request_fingerprint AS requestFingerprint,
                   status,
                   dry_run AS dryRun,
                   scope_from AS scopeFrom,
                   scope_to AS scopeTo,
                   result_summary AS resultSummary
            FROM t_channel_quality_governance_rebuild_request
            WHERE operator_uid = #{operatorUid}
              AND idempotency_key = #{idempotencyKey}
            LIMIT 1
            """)
    ChannelQualityGovernanceAnalyticsRebuildRequestRow selectRebuildRequest(
            @Param("operatorUid") Long operatorUid,
            @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT COUNT(*)
            FROM t_channel_quality_governance_rebuild_request
            WHERE dry_run = 0
              AND status IN ('REQUESTED', 'RUNNING', 'VALIDATING')
              AND scope_from = #{scopeFrom}
              AND scope_to = #{scopeTo}
            """)
    int countActiveRebuildRequests(
            @Param("scopeFrom") LocalDateTime scopeFrom,
            @Param("scopeTo") LocalDateTime scopeTo);

    @Insert("""
            INSERT IGNORE INTO t_channel_quality_governance_rebuild_request (
                id, operator_uid, idempotency_key, request_fingerprint, status,
                dry_run, scope_from, scope_to, result_summary
            ) VALUES (
                #{row.id}, #{row.operatorUid}, #{row.idempotencyKey}, #{row.requestFingerprint}, #{row.status},
                #{row.dryRun}, #{row.scopeFrom}, #{row.scopeTo}, #{row.resultSummary}
            )
            """)
    int insertRebuildRequestIgnore(@Param("row") ChannelQualityGovernanceAnalyticsRebuildRequestRow row);
}
