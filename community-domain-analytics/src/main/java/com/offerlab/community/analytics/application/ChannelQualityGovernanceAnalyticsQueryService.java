package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsBreakdownDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsBreakdownItemDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsFunnelStageDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsHandlingDurationDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsOverviewDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsProjectionHealthDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsRateDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsRatesDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsSourceWatermarkDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsTrendBucketDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsTrendsDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceAnalyticsWindowDTO;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceAnalyticsCursorRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceAnalyticsFunnelRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceAnalyticsGenerationRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceAnalyticsHandlingDurationRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceAnalyticsHealthSummaryRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceAnalyticsMapper;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernanceAnalyticsMetricRow;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.security.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChannelQualityGovernanceAnalyticsQueryService {

    private static final int V43_TABLE_COUNT = 11;
    private static final int MAX_RANGE_DAYS = 365;
    private static final int MIN_GROUP_SAMPLE = 5;
    private static final int ISSUE_COUNT_CAP = 1_000;
    private static final String DEFINITION_VERSION = "V43.1";
    private static final String STORAGE_DEFINITION_VERSION = "V43_1";
    private static final List<String> SOURCE_TYPES = List.of(
            "V38_BATCH", "V38_ATTEMPT", "V39_EVENT", "V40_CASE_EVENT",
            "V41_CLOSE_SNAPSHOT", "V41_RECURRENCE", "V42_TODO", "V42_REMINDER");
    private static final Set<String> CORE_SOURCE_TYPES = Set.of(
            "V38_BATCH", "V38_ATTEMPT", "V39_EVENT", "V40_CASE_EVENT",
            "V41_CLOSE_SNAPSHOT", "V41_RECURRENCE");
    private static final Set<String> V42_METRICS = Set.of(
            "GOVERNANCE_OVERDUE_RATE",
            "REMINDER_COVERAGE_RATE",
            "POST_REMINDER_COMPLETION_RATE");
    private static final Set<String> METRIC_CODES = Set.of(
            "CASE_OPENED_COUNT", "CASE_CLOSED_COUNT", "GOVERNANCE_OVERDUE_RATE",
            "REMINDER_COVERAGE_RATE", "POST_REMINDER_COMPLETION_RATE",
            "TASK_REWORK_RATE", "BATCH_WITHDRAW_RATE", "RISK_RECURRENCE_RATE");
    private static final Set<String> TRIGGER_TYPES = Set.of("RISK_EVENT", "DUE_STATE");
    private static final Set<String> RISK_CATEGORIES = Set.of(
            "BLOCKER", "CAPACITY_RISK", "REVIEW_DELAY", "OVERDUE_ESCALATION");
    private static final Set<Integer> RECURRENCE_WINDOWS = Set.of(7, 30, 90);
    private static final Set<String> DIMENSIONS = Set.of(
            "TRIGGER_TYPE", "RISK_CATEGORY", "ROOT_CAUSE_CATEGORY",
            "RESOLUTION_OUTCOME", "RECOVERY_VERIFICATION_STATUS");
    private static final Map<String, Set<String>> BREAKDOWN_DIMENSIONS = Map.of(
            "CASE_OPENED_COUNT", Set.of("TRIGGER_TYPE", "RISK_CATEGORY"),
            "CASE_CLOSED_COUNT", Set.of(
                    "TRIGGER_TYPE", "RISK_CATEGORY", "ROOT_CAUSE_CATEGORY",
                    "RESOLUTION_OUTCOME", "RECOVERY_VERIFICATION_STATUS"),
            "RISK_RECURRENCE_RATE", Set.of("RISK_CATEGORY", "ROOT_CAUSE_CATEGORY"));

    private final ChannelQualityGovernanceAnalyticsMapper analyticsMapper;
    private final AdminPermissionService adminPermissionService;
    private final Clock analyticsClock;

    @Transactional(readOnly = true)
    public ChannelQualityGovernanceAnalyticsOverviewDTO overview(
            String rawFrom,
            String rawTo,
            Integer rawDomain,
            String rawTriggerType,
            String rawRiskCategory,
            Integer rawRecurrenceWindowDays,
            Long operatorUid) {
        requireAnalyticsRead(operatorUid);
        requireV43Tables();
        Window window = requireWindow(rawFrom, rawTo);
        Filter filter = requireFilter(rawDomain, rawTriggerType, rawRiskCategory, rawRecurrenceWindowDays);
        ProjectionState projection = projectionState();
        if (!projection.coreReady()) {
            return unavailableOverview(window, projection);
        }

        ChannelQualityGovernanceAnalyticsFunnelRow funnel = analyticsMapper.selectFunnel(
                projection.generationId(), window.from(), window.to(), window.asOf(),
                filter.domain(), filter.triggerType(), filter.riskCategory());
        ChannelQualityGovernanceAnalyticsHandlingDurationRow duration = analyticsMapper.selectHandlingDuration(
                projection.generationId(), window.from(), window.to(), window.asOf(),
                filter.domain(), filter.triggerType(), filter.riskCategory());

        return ChannelQualityGovernanceAnalyticsOverviewDTO.builder()
                .window(toWindowDto(window))
                .metricDefinitionVersion(DEFINITION_VERSION)
                .projectionGeneration(projection.generationId())
                .projectionStatus(projection.status())
                .sourceWatermarks(projection.watermarks())
                .funnel(toFunnel(funnel))
                .handlingDuration(toHandlingDuration(duration))
                .rates(ChannelQualityGovernanceAnalyticsRatesDTO.builder()
                        .governanceOverdueRate(rate(
                                "GOVERNANCE_OVERDUE_RATE", window, filter, projection, false))
                        .reminderCoverageRate(rate(
                                "REMINDER_COVERAGE_RATE", window, filter, projection, true))
                        .postReminderCompletionRate(rate(
                                "POST_REMINDER_COMPLETION_RATE", window, filter, projection, true))
                        .taskReworkRate(rate("TASK_REWORK_RATE", window, filter, projection, false))
                        .batchWithdrawRate(rate("BATCH_WITHDRAW_RATE", window, filter, projection, false))
                        .riskRecurrenceRate(rate("RISK_RECURRENCE_RATE", window, filter, projection, false))
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public ChannelQualityGovernanceAnalyticsTrendsDTO trends(
            String rawMetricCode,
            String rawGrain,
            String rawFrom,
            String rawTo,
            Integer rawDomain,
            String rawTriggerType,
            String rawRiskCategory,
            Integer rawRecurrenceWindowDays,
            Long operatorUid) {
        requireAnalyticsRead(operatorUid);
        requireV43Tables();
        String metricCode = requireMetricCode(rawMetricCode);
        Grain grain = requireGrain(rawGrain);
        Window window = requireWindow(rawFrom, rawTo);
        Filter filter = requireFilter(rawDomain, rawTriggerType, rawRiskCategory, rawRecurrenceWindowDays);
        ProjectionState projection = projectionState();
        List<ChannelQualityGovernanceAnalyticsTrendBucketDTO> buckets = new ArrayList<>();

        String unavailableReason = unavailableMetricReason(metricCode, filter, projection);
        List<ChannelQualityGovernanceAnalyticsMetricRow> rows = unavailableReason == null
                ? selectMetricRows(metricCode, window, filter, projection)
                : List.of();
        Map<LocalDate, List<ChannelQualityGovernanceAnalyticsMetricRow>> rowsByDate = new HashMap<>();
        for (ChannelQualityGovernanceAnalyticsMetricRow row : rows) {
            if (row.getBucketDate() != null) {
                rowsByDate.computeIfAbsent(row.getBucketDate(), ignored -> new ArrayList<>()).add(row);
            }
        }

        for (LocalDate bucketStart = window.from().toLocalDate();
             bucketStart.isBefore(window.to().toLocalDate());
             bucketStart = bucketStart.plusDays(grain.days())) {
            LocalDate bucketEnd = bucketStart.plusDays(grain.days());
            if (bucketEnd.isAfter(window.to().toLocalDate())) {
                bucketEnd = window.to().toLocalDate();
            }
            List<ChannelQualityGovernanceAnalyticsMetricRow> bucketRows = new ArrayList<>();
            for (LocalDate date = bucketStart; date.isBefore(bucketEnd); date = date.plusDays(1)) {
                bucketRows.addAll(rowsByDate.getOrDefault(date, List.of()));
            }
            MetricAggregate aggregate = unavailableReason == null
                    ? aggregate(metricCode, bucketRows, true)
                    : MetricAggregate.unavailable(unavailableReason);
            buckets.add(toTrendBucket(bucketStart, bucketEnd, aggregate, projection.generationId()));
        }

        return ChannelQualityGovernanceAnalyticsTrendsDTO.builder()
                .metricCode(metricCode)
                .grain(grain.name())
                .window(toWindowDto(window))
                .projectionGeneration(projection.generationId())
                .projectionStatus(projection.status())
                .buckets(buckets)
                .build();
    }

    @Transactional(readOnly = true)
    public ChannelQualityGovernanceAnalyticsBreakdownDTO breakdown(
            String rawMetricCode,
            String rawDimension,
            String rawFrom,
            String rawTo,
            Integer rawDomain,
            Long operatorUid) {
        requireAnalyticsRead(operatorUid);
        requireV43Tables();
        String metricCode = requireMetricCode(rawMetricCode);
        String dimension = requireBreakdownDimension(metricCode, rawDimension);
        Window window = requireWindow(rawFrom, rawTo);
        Filter filter = requireFilter(rawDomain, null, null, null);
        ProjectionState projection = projectionState();
        List<ChannelQualityGovernanceAnalyticsBreakdownItemDTO> items = new ArrayList<>();

        String unavailableReason = unavailableMetricReason(metricCode, filter, projection);
        if (unavailableReason == null) {
            for (ChannelQualityGovernanceAnalyticsMetricRow row : analyticsMapper.selectBreakdownRows(
                    projection.generationId(), metricCode, window.from().toLocalDate(), window.to().toLocalDate(),
                    storageDomain(filter.domain()), dimension)) {
                items.add(toBreakdownItem(row.getDimensionValue(), aggregate(metricCode, List.of(row), true)));
            }
        }

        return ChannelQualityGovernanceAnalyticsBreakdownDTO.builder()
                .metricCode(metricCode)
                .dimension(dimension)
                .window(toWindowDto(window))
                .projectionGeneration(projection.generationId())
                .projectionStatus(projection.status())
                .items(items)
                .build();
    }

    @Transactional(readOnly = true)
    public ChannelQualityGovernanceAnalyticsProjectionHealthDTO projectionHealth(Long operatorUid) {
        requireOps(operatorUid);
        requireV43Tables();
        ProjectionState projection = projectionState();
        ChannelQualityGovernanceAnalyticsHealthSummaryRow summary = projection.generationId() == null
                ? null : analyticsMapper.selectHealthSummary(projection.generationId());
        long openIssueCount = summary == null || summary.getOpenIssueCount() == null ? 0L : summary.getOpenIssueCount();
        return ChannelQualityGovernanceAnalyticsProjectionHealthDTO.builder()
                .projectionStatus(projection.status())
                .activeGeneration(projection.generationId())
                .latestCompletedGeneration(projection.latestCompletedGenerationId())
                .latestCompletedAt(toInstant(projection.latestCompletedAt()))
                .sourceWatermarks(projection.watermarks())
                .dirtyBucketCount(summary == null ? 0L : safeLong(summary.getDirtyBucketCount()))
                .oldestDirtyBucket(summary == null ? null : summary.getOldestDirtyBucket())
                .openIssueCount(Math.min(openIssueCount, ISSUE_COUNT_CAP))
                .issueCountCapped(openIssueCount > ISSUE_COUNT_CAP)
                .v42CapabilityStatus(projection.v42TodoAvailable() && projection.v42ReminderAvailable()
                        ? "AVAILABLE" : "UNAVAILABLE")
                .rebuildSupported(projection.coreReady())
                .build();
    }

    private List<ChannelQualityGovernanceAnalyticsMetricRow> selectMetricRows(
            String metricCode,
            Window window,
            Filter filter,
            ProjectionState projection) {
        MetricDimension metricDimension = metricDimension(filter);
        return analyticsMapper.selectMetricRows(
                projection.generationId(), metricCode, window.from().toLocalDate(), window.to().toLocalDate(),
                storageDomain(filter.domain()), metricDimension.type(), metricDimension.value());
    }

    private ChannelQualityGovernanceAnalyticsRateDTO rate(
            String metricCode,
            Window window,
            Filter filter,
            ProjectionState projection,
            boolean requiresReminderSource) {
        String unavailableReason = unavailableMetricReason(metricCode, filter, projection);
        if (unavailableReason != null) {
            return unavailableRate(unavailableReason);
        }
        if (requiresReminderSource && !projection.v42ReminderAvailable()) {
            return unavailableRate("V42_REMINDER_SOURCE_UNAVAILABLE");
        }
        return toRate(aggregate(metricCode, selectMetricRows(metricCode, window, filter, projection), false));
    }

    private String unavailableMetricReason(String metricCode, Filter filter, ProjectionState projection) {
        if (!projection.coreReady()) {
            return "CORE_PROJECTION_UNAVAILABLE";
        }
        if (V42_METRICS.contains(metricCode) && !projection.v42TodoAvailable()) {
            return "V42_TODO_SOURCE_UNAVAILABLE";
        }
        if (("REMINDER_COVERAGE_RATE".equals(metricCode) || "POST_REMINDER_COMPLETION_RATE".equals(metricCode))
                && !projection.v42ReminderAvailable()) {
            return "V42_REMINDER_SOURCE_UNAVAILABLE";
        }
        if ("RISK_RECURRENCE_RATE".equals(metricCode) && filter.recurrenceWindowDays() != 30) {
            return "RECURRENCE_WINDOW_NOT_PROJECTED";
        }
        if (filter.triggerType() != null && filter.riskCategory() != null) {
            return "COMBINED_FILTER_UNAVAILABLE";
        }
        return null;
    }

    private ProjectionState projectionState() {
        ChannelQualityGovernanceAnalyticsGenerationRow active = analyticsMapper.selectActiveGeneration();
        ChannelQualityGovernanceAnalyticsGenerationRow latest = analyticsMapper.selectLatestCompletedGeneration();
        Long generationId = active == null ? null : active.getId();
        Map<String, ChannelQualityGovernanceAnalyticsCursorRow> cursorByType = new HashMap<>();
        if (generationId != null) {
            for (ChannelQualityGovernanceAnalyticsCursorRow row : analyticsMapper.selectCursors(generationId)) {
                if (row != null && SOURCE_TYPES.contains(row.getSourceType())) {
                    cursorByType.put(row.getSourceType(), row);
                }
            }
        }

        List<ChannelQualityGovernanceAnalyticsSourceWatermarkDTO> watermarks = new ArrayList<>();
        boolean coreReady = generationId != null;
        boolean v42TodoAvailable = false;
        boolean v42ReminderAvailable = false;
        LocalDateTime oldestCoreCoverage = null;
        for (String sourceType : SOURCE_TYPES) {
            ChannelQualityGovernanceAnalyticsCursorRow row = cursorByType.get(sourceType);
            boolean available = cursorAvailable(row, generationId);
            if (CORE_SOURCE_TYPES.contains(sourceType)) {
                coreReady = coreReady && available;
                if (available && (oldestCoreCoverage == null
                        || row.getCoveredThrough().isBefore(oldestCoreCoverage))) {
                    oldestCoreCoverage = row.getCoveredThrough();
                }
            }
            if ("V42_TODO".equals(sourceType)) {
                v42TodoAvailable = available;
            }
            if ("V42_REMINDER".equals(sourceType)) {
                v42ReminderAvailable = available;
            }
            watermarks.add(toWatermark(sourceType, row, available));
        }

        String status;
        if (!coreReady) {
            status = "UNAVAILABLE";
        } else if (oldestCoreCoverage != null
                && Duration.between(oldestCoreCoverage.toInstant(ZoneOffset.UTC), analyticsClock.instant()).toHours() > 24) {
            status = "STALE";
        } else if (!v42TodoAvailable || !v42ReminderAvailable) {
            status = "DEGRADED";
        } else {
            status = "READY";
        }
        return new ProjectionState(
                generationId,
                latest == null ? null : latest.getId(),
                latest == null ? null : latest.getCompletedAt(),
                coreReady,
                v42TodoAvailable,
                v42ReminderAvailable,
                status,
                watermarks);
    }

    private ChannelQualityGovernanceAnalyticsOverviewDTO unavailableOverview(
            Window window,
            ProjectionState projection) {
        String reason = "CORE_PROJECTION_UNAVAILABLE";
        return ChannelQualityGovernanceAnalyticsOverviewDTO.builder()
                .window(toWindowDto(window))
                .metricDefinitionVersion(DEFINITION_VERSION)
                .projectionGeneration(projection.generationId())
                .projectionStatus(projection.status())
                .sourceWatermarks(projection.watermarks())
                .funnel(unavailableFunnel(reason))
                .handlingDuration(ChannelQualityGovernanceAnalyticsHandlingDurationDTO.builder()
                        .availability("UNAVAILABLE")
                        .reason(reason)
                        .build())
                .rates(ChannelQualityGovernanceAnalyticsRatesDTO.builder()
                        .governanceOverdueRate(unavailableRate(reason))
                        .reminderCoverageRate(unavailableRate(reason))
                        .postReminderCompletionRate(unavailableRate(reason))
                        .taskReworkRate(unavailableRate(reason))
                        .batchWithdrawRate(unavailableRate(reason))
                        .riskRecurrenceRate(unavailableRate(reason))
                        .build())
                .build();
    }

    private List<ChannelQualityGovernanceAnalyticsFunnelStageDTO> toFunnel(
            ChannelQualityGovernanceAnalyticsFunnelRow row) {
        ChannelQualityGovernanceAnalyticsFunnelRow safe = row == null
                ? new ChannelQualityGovernanceAnalyticsFunnelRow() : row;
        return List.of(
                funnelStage("OPENED", safe.getOpenedCount()),
                funnelStage("ACKNOWLEDGED", safe.getAcknowledgedCount()),
                funnelStage("PLANNED", safe.getPlannedCount()),
                funnelStage("RESOLUTION_SUBMITTED", safe.getResolutionSubmittedCount()),
                funnelStage("GOVERNANCE_CLOSED", safe.getGovernanceClosedCount()),
                funnelStage("RECOVERY_VERIFIED", safe.getRecoveryVerifiedCount()));
    }

    private List<ChannelQualityGovernanceAnalyticsFunnelStageDTO> unavailableFunnel(String reason) {
        return List.of(
                unavailableFunnelStage("OPENED", reason),
                unavailableFunnelStage("ACKNOWLEDGED", reason),
                unavailableFunnelStage("PLANNED", reason),
                unavailableFunnelStage("RESOLUTION_SUBMITTED", reason),
                unavailableFunnelStage("GOVERNANCE_CLOSED", reason),
                unavailableFunnelStage("RECOVERY_VERIFIED", reason));
    }

    private ChannelQualityGovernanceAnalyticsHandlingDurationDTO toHandlingDuration(
            ChannelQualityGovernanceAnalyticsHandlingDurationRow row) {
        long sampleCount = row == null ? 0L : safeLong(row.getSampleCount());
        if (sampleCount == 0) {
            return ChannelQualityGovernanceAnalyticsHandlingDurationDTO.builder()
                    .availability("AVAILABLE")
                    .sampleCount(0L)
                    .reason("NO_ELIGIBLE_SAMPLE")
                    .build();
        }
        return ChannelQualityGovernanceAnalyticsHandlingDurationDTO.builder()
                .availability("AVAILABLE")
                .sampleCount(sampleCount)
                .averageSeconds(row.getAverageSeconds())
                .p50Seconds(row.getP50Seconds())
                .p90Seconds(row.getP90Seconds())
                .maxSeconds(row.getMaxSeconds())
                .build();
    }

    private MetricAggregate aggregate(
            String metricCode,
            List<ChannelQualityGovernanceAnalyticsMetricRow> rows,
            boolean suppressSmallSample) {
        if (rows.isEmpty()) {
            return MetricAggregate.noEligibleSample();
        }
        if (rows.stream().anyMatch(row -> row.getSampleCount() == null
                || !STORAGE_DEFINITION_VERSION.equals(row.getDefinitionVersion())
                || !Set.of("AVAILABLE", "UNAVAILABLE", "SUPPRESSED").contains(row.getAvailabilityStatus()))) {
            return MetricAggregate.unavailable("METRIC_CONTRACT_UNAVAILABLE");
        }
        if (rows.stream().anyMatch(row -> "UNAVAILABLE".equals(row.getAvailabilityStatus()))) {
            return MetricAggregate.unavailable("METRIC_SOURCE_UNAVAILABLE");
        }
        if (rows.stream().anyMatch(row -> "SUPPRESSED".equals(row.getAvailabilityStatus()))) {
            return MetricAggregate.suppressed();
        }
        long sampleCount = rows.stream().map(ChannelQualityGovernanceAnalyticsMetricRow::getSampleCount)
                .mapToLong(ChannelQualityGovernanceAnalyticsQueryService::safeLong).sum();
        if (sampleCount == 0) {
            return MetricAggregate.noEligibleSample();
        }
        if (suppressSmallSample && sampleCount < MIN_GROUP_SAMPLE) {
            return MetricAggregate.suppressed();
        }
        if (isRateMetric(metricCode)) {
            if (rows.stream().anyMatch(row -> row.getNumerator() == null || row.getDenominator() == null)) {
                return MetricAggregate.unavailable("METRIC_VALUE_UNAVAILABLE");
            }
            long numerator = rows.stream().map(ChannelQualityGovernanceAnalyticsMetricRow::getNumerator)
                    .mapToLong(ChannelQualityGovernanceAnalyticsQueryService::safeLong).sum();
            long denominator = rows.stream().map(ChannelQualityGovernanceAnalyticsMetricRow::getDenominator)
                    .mapToLong(ChannelQualityGovernanceAnalyticsQueryService::safeLong).sum();
            if (denominator == 0) {
                return MetricAggregate.noEligibleSample();
            }
            return MetricAggregate.availableRate(numerator, denominator, sampleCount);
        }
        return MetricAggregate.availableCount(sampleCount);
    }

    private ChannelQualityGovernanceAnalyticsRateDTO toRate(MetricAggregate aggregate) {
        return ChannelQualityGovernanceAnalyticsRateDTO.builder()
                .availability(aggregate.availability())
                .value(aggregate.value())
                .numerator(aggregate.numerator())
                .denominator(aggregate.denominator())
                .reason(aggregate.reason())
                .build();
    }

    private ChannelQualityGovernanceAnalyticsTrendBucketDTO toTrendBucket(
            LocalDate bucketStart,
            LocalDate bucketEnd,
            MetricAggregate aggregate,
            Long projectionGeneration) {
        return ChannelQualityGovernanceAnalyticsTrendBucketDTO.builder()
                .bucketStart(bucketStart.atStartOfDay().toInstant(ZoneOffset.UTC))
                .bucketEnd(bucketEnd.atStartOfDay().toInstant(ZoneOffset.UTC))
                .availability(aggregate.availability())
                .value(aggregate.value())
                .numerator(aggregate.numerator())
                .denominator(aggregate.denominator())
                .sampleCount(aggregate.sampleCount())
                .reason(aggregate.reason())
                .projectionGeneration(projectionGeneration)
                .build();
    }

    private ChannelQualityGovernanceAnalyticsBreakdownItemDTO toBreakdownItem(
            String dimensionValue,
            MetricAggregate aggregate) {
        return ChannelQualityGovernanceAnalyticsBreakdownItemDTO.builder()
                .dimensionValue(dimensionValue)
                .availability(aggregate.availability())
                .value(aggregate.value())
                .numerator(aggregate.numerator())
                .denominator(aggregate.denominator())
                .sampleCount(aggregate.sampleCount())
                .reason(aggregate.reason())
                .build();
    }

    private ChannelQualityGovernanceAnalyticsSourceWatermarkDTO toWatermark(
            String sourceType,
            ChannelQualityGovernanceAnalyticsCursorRow row,
            boolean available) {
        LocalDateTime coveredThrough = row == null ? null : row.getCoveredThrough();
        Long lagSeconds = null;
        if (coveredThrough != null) {
            lagSeconds = Math.max(0L,
                    Duration.between(coveredThrough.toInstant(ZoneOffset.UTC), analyticsClock.instant()).getSeconds());
        }
        return ChannelQualityGovernanceAnalyticsSourceWatermarkDTO.builder()
                .sourceType(sourceType)
                .available(available)
                .coveredThrough(toInstant(coveredThrough))
                .lagSeconds(lagSeconds)
                .backlogCount(row == null ? null : row.getBacklogCount())
                .status(available ? "AVAILABLE" : "UNAVAILABLE")
                .build();
    }

    private static ChannelQualityGovernanceAnalyticsFunnelStageDTO funnelStage(String stage, Long count) {
        return ChannelQualityGovernanceAnalyticsFunnelStageDTO.builder()
                .stage(stage)
                .availability("AVAILABLE")
                .count(safeLong(count))
                .build();
    }

    private static ChannelQualityGovernanceAnalyticsFunnelStageDTO unavailableFunnelStage(String stage, String reason) {
        return ChannelQualityGovernanceAnalyticsFunnelStageDTO.builder()
                .stage(stage)
                .availability("UNAVAILABLE")
                .reason(reason)
                .build();
    }

    private static ChannelQualityGovernanceAnalyticsRateDTO unavailableRate(String reason) {
        return ChannelQualityGovernanceAnalyticsRateDTO.builder()
                .availability("UNAVAILABLE")
                .reason(reason)
                .build();
    }

    private Window requireWindow(String rawFrom, String rawTo) {
        LocalDateTime from = parseUtcDateBoundary(rawFrom);
        LocalDateTime to = parseUtcDateBoundary(rawTo);
        if (!to.isAfter(from) || Duration.between(from, to).toDays() > MAX_RANGE_DAYS) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return new Window(from, to, LocalDateTime.ofInstant(analyticsClock.instant(), ZoneOffset.UTC));
    }

    private static LocalDateTime parseUtcDateBoundary(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        try {
            Instant instant = OffsetDateTime.parse(value.trim()).toInstant();
            LocalDateTime utc = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
            if (!utc.toLocalTime().equals(LocalTime.MIDNIGHT)) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
            return utc;
        } catch (DateTimeException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static Filter requireFilter(
            Integer rawDomain,
            String rawTriggerType,
            String rawRiskCategory,
            Integer rawRecurrenceWindowDays) {
        Integer domain = rawDomain == null ? null : requireDomain(rawDomain);
        String triggerType = optionalEnum(rawTriggerType, TRIGGER_TYPES);
        String riskCategory = optionalEnum(rawRiskCategory, RISK_CATEGORIES);
        int recurrenceWindowDays = rawRecurrenceWindowDays == null ? 30 : rawRecurrenceWindowDays;
        if (!RECURRENCE_WINDOWS.contains(recurrenceWindowDays)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return new Filter(domain, triggerType, riskCategory, recurrenceWindowDays);
    }

    private static String requireMetricCode(String value) {
        String metricCode = optionalEnum(value, METRIC_CODES);
        if (metricCode == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return metricCode;
    }

    private static Grain requireGrain(String value) {
        String normalized = optionalEnum(value, Set.of("DAY", "WEEK"));
        if (normalized == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return Grain.valueOf(normalized);
    }

    private static String requireBreakdownDimension(String metricCode, String rawDimension) {
        String dimension = optionalEnum(rawDimension, DIMENSIONS);
        if (dimension == null || !BREAKDOWN_DIMENSIONS.getOrDefault(metricCode, Set.of()).contains(dimension)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return dimension;
    }

    private static String optionalEnum(String value, Set<String> allowed) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static int requireDomain(Integer domain) {
        if (domain == null || domain < 1 || domain > 5) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return domain;
    }

    private static MetricDimension metricDimension(Filter filter) {
        if (filter.triggerType() != null && filter.riskCategory() != null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (filter.triggerType() != null) {
            return new MetricDimension("TRIGGER_TYPE", filter.triggerType());
        }
        if (filter.riskCategory() != null) {
            return new MetricDimension("RISK_CATEGORY", filter.riskCategory());
        }
        return new MetricDimension("NONE", "ALL");
    }

    private void requireAnalyticsRead(Long operatorUid) {
        if (operatorUid == null || operatorUid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (adminPermissionService.isAdmin(operatorUid)
                || adminPermissionService.hasRole(operatorUid, AdminPermissionService.ROLE_CONTENT_MODERATOR)
                || adminPermissionService.hasRole(operatorUid, AdminPermissionService.ROLE_OPS)
                || adminPermissionService.isLocalOpenMode()) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN);
    }

    private void requireOps(Long operatorUid) {
        if (operatorUid == null || operatorUid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        adminPermissionService.requireScope(operatorUid, AdminPermissionService.ROLE_OPS);
    }

    private void requireV43Tables() {
        try {
            if (analyticsMapper.v43TablesExist() == V43_TABLE_COUNT) {
                return;
            }
        } catch (RuntimeException ignored) {
            // The read model must fail closed until its own migration is present.
        }
        throw new BizException(ErrorCode.DEPENDENCY_ERROR);
    }

    private static boolean cursorAvailable(ChannelQualityGovernanceAnalyticsCursorRow row, Long generationId) {
        return row != null
                && generationId != null
                && generationId.equals(row.getProjectionGeneration())
                && row.getCoveredThrough() != null
                && row.getLastSuccessAt() != null
                && !StringUtils.hasText(row.getLastErrorCode());
    }

    private static boolean isRateMetric(String metricCode) {
        return !Set.of("CASE_OPENED_COUNT", "CASE_CLOSED_COUNT").contains(metricCode);
    }

    private static int storageDomain(Integer domain) {
        return domain == null ? 0 : domain;
    }

    private static long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static ChannelQualityGovernanceAnalyticsWindowDTO toWindowDto(Window window) {
        return ChannelQualityGovernanceAnalyticsWindowDTO.builder()
                .from(window.from().toInstant(ZoneOffset.UTC))
                .to(window.to().toInstant(ZoneOffset.UTC))
                .asOf(window.asOf().toInstant(ZoneOffset.UTC))
                .timezone("UTC")
                .build();
    }

    private enum Grain {
        DAY(1),
        WEEK(7);

        private final int days;

        Grain(int days) {
            this.days = days;
        }

        public int days() {
            return days;
        }
    }

    private record Window(LocalDateTime from, LocalDateTime to, LocalDateTime asOf) {
    }

    private record Filter(Integer domain, String triggerType, String riskCategory, int recurrenceWindowDays) {
    }

    private record MetricDimension(String type, String value) {
    }

    private record ProjectionState(
            Long generationId,
            Long latestCompletedGenerationId,
            LocalDateTime latestCompletedAt,
            boolean coreReady,
            boolean v42TodoAvailable,
            boolean v42ReminderAvailable,
            String status,
            List<ChannelQualityGovernanceAnalyticsSourceWatermarkDTO> watermarks) {
    }

    private record MetricAggregate(
            String availability,
            BigDecimal value,
            Long numerator,
            Long denominator,
            Long sampleCount,
            String reason) {

        private static MetricAggregate availableRate(long numerator, long denominator, long sampleCount) {
            return new MetricAggregate(
                    "AVAILABLE",
                    BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP),
                    numerator,
                    denominator,
                    sampleCount,
                    null);
        }

        private static MetricAggregate availableCount(long sampleCount) {
            return new MetricAggregate(
                    "AVAILABLE",
                    BigDecimal.valueOf(sampleCount),
                    null,
                    null,
                    sampleCount,
                    null);
        }

        private static MetricAggregate noEligibleSample() {
            return new MetricAggregate("AVAILABLE", null, 0L, 0L, 0L, "NO_ELIGIBLE_SAMPLE");
        }

        private static MetricAggregate unavailable(String reason) {
            return new MetricAggregate("UNAVAILABLE", null, null, null, null, reason);
        }

        private static MetricAggregate suppressed() {
            return new MetricAggregate("SUPPRESSED", null, null, null, null, "SMALL_SAMPLE_SUPPRESSED");
        }
    }
}
